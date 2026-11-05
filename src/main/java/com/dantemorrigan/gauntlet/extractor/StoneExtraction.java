package com.dantemorrigan.gauntlet.extractor;

import com.dantemorrigan.gauntlet.power.SoulStone;
import com.dantemorrigan.gauntlet.power.TimeAbility;
import com.dantemorrigan.gauntlet.power.MindStone;
import com.dantemorrigan.gauntlet.power.RealityStone;
import com.dantemorrigan.gauntlet.cosmos.CosmosTravel;
import com.dantemorrigan.gauntlet.ability.AbilityController;
import com.dantemorrigan.gauntlet.snap.InfinitySnap;
import com.dantemorrigan.gauntlet.power.InfinityBeam;
import com.dantemorrigan.gauntlet.CoopRules;
import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.ModComponents;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.net.ModStatus;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server rules of the Infinity Extractor.
 *
 * <p>A stone is pulled in {@link #CYCLES} cycles of {@link #CYCLE_TICKS} ticks each. A cycle only counts if the
 * extractor stays aimed at the target (in range, in line of sight) and the stone is not in use for all of it;
 * otherwise nothing is gained. Finished cycles are remembered for {@link #KEEP_TICKS} ticks (per extractor owner,
 * target and stone) and the owner has to press again for the next one, so the victim gets windows to react.
 *
 * <p>Model state (custom_model_data float 0): 0 empty, 1 first cycle running, 2 / 3 one / two cycles done (kept while
 * the next one runs), 4 a stone sits in the chamber.
 */
public final class StoneExtraction {
	public static final int RANGE = 20;
	public static final int CYCLES = 3;
	public static final int CYCLE_TICKS = 36;
	public static final int KEEP_TICKS = 20 * 20;
	/** cos of the largest angle between the line of sight and the target before the cycle breaks. */
	private static final double AIM_DOT = 0.8;
	/** Ticks the extractor may drift off its target (a step, a turn of the head) before the cycle breaks; nothing is gained meanwhile. */
	private static final int AIM_GRACE = 12;

	// Fx kinds, see ModNetwork.ExtractorFx.
	private static final int FX_START = 0, FX_CYCLE = 1, FX_FAIL = 2, FX_BLOCKED = 3, FX_CAPTURED = 4, FX_RETURNED = 5;

	private static final class Session {
		final UUID target;
		final Stone stone;
		final ItemStack extractor, targetGauntlet;
		final ServerLevel level;
		int cycles;
		int channelTicks;
		int lostTicks;
		boolean channeling;
		long touched;

		Session(UUID target, Stone stone, ItemStack extractor, ItemStack targetGauntlet, ServerLevel level, long now) {
			this.target = target;
			this.stone = stone;
			this.extractor = extractor;
			this.targetGauntlet = targetGauntlet;
			this.level = level;
			this.touched = now;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	private static final Map<UUID, Long> LAST_NOTICE = new HashMap<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(StoneExtraction::serverTick);
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID owner = handler.getPlayer().getUUID();
			SESSIONS.remove(owner);
			LAST_NOTICE.remove(owner);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {SESSIONS.clear(); LAST_NOTICE.clear();});
	}

	// --- chamber -------------------------------------------------------------------------------------------------

	public static ItemStack chamber(ItemStack extractor) {
		return extractor.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyOne();
	}

	private static void setState(ItemStack extractor, int state) {
		CustomModelData current = extractor.get(DataComponents.CUSTOM_MODEL_DATA);
		if (current != null && current.getFloat(0) != null && current.getFloat(0) == state) return;
		extractor.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of((float) state), List.of(), List.of(), List.of()));
	}

	private static int state(ItemStack extractor) {
		CustomModelData data = extractor.get(DataComponents.CUSTOM_MODEL_DATA);
		Float f = data == null ? null : data.getFloat(0);
		return f == null ? 0 : f.intValue();
	}

	private static int idleState(Session s) {
		return s == null || s.cycles == 0 ? 0 : s.cycles + 1;
	}

	/** -1 = automatic. */
	private static int selected(ItemStack extractor) {
		return extractor.getOrDefault(ModComponents.EXTRACTOR_STONE, -1);
	}

	// --- player actions ------------------------------------------------------------------------------------------

	/** Shift + right-click: take the stone out of the chamber, or (empty chamber) choose the stone to pull. */
	public static void sneakUse(ServerPlayer p, ItemStack extractor) {
		if (SESSIONS.containsKey(p.getUUID()) && SESSIONS.get(p.getUUID()).channeling) return;
		ItemStack inside = chamber(extractor);
		if (!inside.isEmpty()) {
			extractor.remove(DataComponents.CONTAINER);
			setState(extractor, 0);
			if (!p.getInventory().add(inside)) p.drop(inside, false);
			p.level().playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 1.3f);
			notice(p, Component.translatable("extractor.infinity_gauntlet.taken", inside.getHoverName()), true);
			return;
		}
		int next = selected(extractor) + 1;
		if (next >= Stone.values().length) {
			extractor.remove(ModComponents.EXTRACTOR_STONE);
			notice(p, Component.translatable("extractor.infinity_gauntlet.mode_auto"), true);
		} else {
			extractor.set(ModComponents.EXTRACTOR_STONE, next);
			notice(p, Component.translatable("extractor.infinity_gauntlet.mode_stone", stoneName(Stone.values()[next])), true);
		}
		p.level().playSound(null, p.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1.5f);
	}

	/** Right-click: starts a cycle on the aimed gauntlet wearer. Returns true when the player should keep holding. */
	public static boolean begin(ServerPlayer p, ItemStack extractor) {
		Session running = SESSIONS.get(p.getUUID());
		if (running != null && running.channeling) return false;
		if (!chamber(extractor).isEmpty()) {
			returnStone(p, extractor);
			return false;
		}
		LivingEntity target = findTarget(p);
		if (target == null) {
			notice(p, Component.translatable("extractor.infinity_gauntlet.no_target"), false);
			return false;
		}
		ItemStack gauntlet = GauntletState.activeGauntlet(target);
		Set<Stone> present = GauntletState.inserted(gauntlet);
		if (present.isEmpty()) {
			notice(p, Component.translatable("extractor.infinity_gauntlet.empty_gauntlet"), false);
			return false;
		}
		Stone stone = null;
		int chosen = selected(extractor);
		if (chosen >= 0) {
			stone = Stone.values()[chosen];
			if (!present.contains(stone)) {
				notice(p, Component.translatable("extractor.infinity_gauntlet.no_such_stone", stoneName(stone)), false);
				return false;
			}
		} else {
			for (Stone s : Stone.values()) if (present.contains(s) && !stoneInUse(target, gauntlet, s)) {stone = s; break;}
			if (stone == null) stone = present.iterator().next(); // all of them are in use: report the blocked one
		}
		if (stoneInUse(target, gauntlet, stone)) {
			fx(p, FX_BLOCKED, target, stone, 0);
			notice(p, Component.translatable("extractor.infinity_gauntlet.in_use", stoneName(stone)), false);
			return false;
		}
		long now = p.level().getGameTime();
		Session s = running;
		if (s == null || !s.target.equals(target.getUUID()) || s.stone != stone || s.extractor != extractor || s.targetGauntlet != gauntlet
				|| s.level != p.level() || now - s.touched > KEEP_TICKS) {
			s = new Session(target.getUUID(), stone, extractor, gauntlet, p.level(), now);
			SESSIONS.put(p.getUUID(), s);
		}
		s.channeling = true;
		s.channelTicks = 0;
		s.lostTicks = 0;
		s.touched = now;
		setState(extractor, s.cycles == 0 ? 1 : s.cycles + 1);
		fx(p, FX_START, target, stone, s.cycles);
		p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 1.2f + 0.15f * s.cycles);
		if (target instanceof ServerPlayer victim) {
			ModStatus.send(victim, Component.translatable("extractor.infinity_gauntlet.victim", stoneName(stone), s.cycles + 1, CYCLES));
		}
		return true;
	}

	public static void tick(ServerPlayer p, ItemStack extractor) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || !s.channeling) return;
		ServerLevel level = p.level();
		Entity found = level.getEntity(s.target);
		if (!(found instanceof LivingEntity target) || !target.isAlive() || !canExtract(p, target)) {
			abort(p, extractor, s, null, true);
			return;
		}
		ItemStack gauntlet = GauntletState.activeGauntlet(target);
		if (gauntlet.isEmpty() || gauntlet != s.targetGauntlet || extractor != s.extractor
				|| level != s.level || !GauntletState.inserted(gauntlet).contains(s.stone)) {
			abort(p, extractor, s, target, true);
			return;
		}
		if (stoneInUse(target, gauntlet, s.stone)) {
			fx(p, FX_BLOCKED, target, s.stone, s.cycles);
			notice(p, Component.translatable("extractor.infinity_gauntlet.in_use", stoneName(s.stone)), false);
			abort(p, extractor, s, target, false);
			return;
		}
		Vec3 toTarget = target.getBoundingBox().getCenter().subtract(p.getEyePosition());
		double distance = toTarget.length();
		if (distance > RANGE + 4) {
			notice(p, Component.translatable("extractor.infinity_gauntlet.lost"), false);
			abort(p, extractor, s, target, false);
			return;
		}
		// A forgiving hold: a short drift off the target only pauses the cycle, a long one breaks it.
		if (toTarget.normalize().dot(p.getLookAngle()) < AIM_DOT || !p.hasLineOfSight(target)) {
			if (++s.lostTicks > AIM_GRACE) {
				notice(p, Component.translatable("extractor.infinity_gauntlet.lost"), false);
				abort(p, extractor, s, target, false);
			}
			return;
		}
		s.lostTicks = 0;
		s.channelTicks++;
		s.touched = level.getGameTime();
		if (s.channelTicks % 12 == 0) {
			level.playSound(null, target.getX(), target.getY() + 1, target.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7f,
					0.8f + 0.5f * s.channelTicks / CYCLE_TICKS);
		}
		if (s.channelTicks >= CYCLE_TICKS) finishCycle(p, extractor, s, target, gauntlet);
	}

	/** Button released (or the item switched): the running cycle is lost, finished ones are kept. */
	public static void release(ServerPlayer p, ItemStack extractor) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || !s.channeling) return;
		abort(p, extractor, s, p.level().getEntity(s.target) instanceof LivingEntity t ? t : null, false);
	}

	// --- internals -----------------------------------------------------------------------------------------------

	private static void finishCycle(ServerPlayer p, ItemStack extractor, Session s, LivingEntity target, ItemStack gauntlet) {
		s.channeling = false;
		s.cycles++;
		s.touched = p.level().getGameTime();
		if (s.cycles < CYCLES) {
			setState(extractor, s.cycles + 1);
			fx(p, FX_CYCLE, target, s.stone, s.cycles);
			p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.9f + 0.3f * s.cycles);
			if (target instanceof ServerPlayer victim) {
				ModStatus.send(victim, Component.translatable("extractor.infinity_gauntlet.victim_cycle", stoneName(s.stone), s.cycles, CYCLES));
			}
			p.stopUsingItem();
			return;
		}
		SESSIONS.remove(p.getUUID());
		ItemStack stone = GauntletState.takeStone(gauntlet, s.stone);
		if (stone.isEmpty()) {
			notice(p, Component.translatable("extractor.infinity_gauntlet.lost"), true);
			setState(extractor, 0);
			fx(p, FX_FAIL, target, s.stone, 0);
			p.stopUsingItem();
			return;
		}
		extractor.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(stone)));
		setState(extractor, 4);
		com.dantemorrigan.gauntlet.ModAdvancements.award(p, "steal_stone");
		if (target instanceof ServerPlayer victim) {
			victim.closeContainer();
			ModStatus.send(victim, Component.translatable("extractor.infinity_gauntlet.victim_lost", stoneName(s.stone)));
		}
		fx(p, FX_CAPTURED, target, s.stone, CYCLES);
		p.level().playSound(null, p.blockPosition(), SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.3f, 1.4f);
		p.level().playSound(null, p.blockPosition(), SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 1.0f, 0.8f);
		notice(p, Component.translatable("extractor.infinity_gauntlet.captured", stoneName(s.stone)), true);
		p.stopUsingItem();
	}

	/** Breaks the running cycle; keeps the finished ones unless the target is gone. */
	private static void abort(ServerPlayer p, ItemStack extractor, Session s, LivingEntity target, boolean dropProgress) {
		s.channeling = false;
		if (dropProgress) SESSIONS.remove(p.getUUID());
		setState(extractor, dropProgress ? 0 : idleState(s));
		fx(p, FX_FAIL, target, s.stone, s.cycles);
		p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
		if (p.isUsingItem()) p.stopUsingItem();
	}

	/** A stone in the chamber goes back into the user's own gauntlet. */
	private static void returnStone(ServerPlayer p, ItemStack extractor) {
		ItemStack inside = chamber(extractor);
		ItemStack gauntlet = GauntletState.activeGauntlet(p);
		if (gauntlet.isEmpty()) {
			notice(p, Component.translatable("extractor.infinity_gauntlet.chamber_full", inside.getHoverName()), false);
			return;
		}
		if (!GauntletState.putStone(gauntlet, inside)) {
			notice(p, Component.translatable("extractor.infinity_gauntlet.slot_taken", inside.getHoverName()), false);
			return;
		}
		extractor.remove(DataComponents.CONTAINER);
		setState(extractor, 0);
		fx(p, FX_RETURNED, p, stoneOf(inside), 0);
		p.level().playSound(null, p.blockPosition(), SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 1.0f, 1.3f);
		notice(p, Component.translatable("extractor.infinity_gauntlet.returned", inside.getHoverName()), true);
	}

	private static boolean stoneInUse(LivingEntity target, ItemStack gauntlet, Stone stone) {
		if (GauntletState.inUse(gauntlet).contains(stone)) return true;
		if (!(target instanceof ServerPlayer player)) return false;
		if (InfinityBeam.active(player) || InfinitySnap.casting(player)) return true;
		return switch (stone) {
			case POWER -> AbilityController.chargeTicks(player) > 0;
			case SPACE -> player.hasAttached(CosmosTravel.FLIGHT);
			case REALITY -> RealityStone.active(player);
			case MIND -> MindStone.holding(player);
			case TIME -> TimeAbility.isRewinding(player);
			case SOUL -> SoulStone.extractingWithGauntlet(player);
		};
	}

	private static boolean canExtract(ServerPlayer owner, LivingEntity target) {
		return !(target instanceof ServerPlayer) || GauntletConfig.get().extractorPlayerTheft && CoopRules.canAffect(owner, target);
	}

	private static LivingEntity findTarget(ServerPlayer p) {
		return pick(p, e -> canExtract(p, e));
	}

	/**
	 * The gauntlet wearer the extractor hooks onto: the one nearest to the crosshair inside a forgiving cone (8 degrees
	 * plus the angular size of the target, so close or big targets are easy to catch), in line of sight and in range.
	 * The client uses the same rule for its reticle, so what is shown is what will be hooked.
	 */
	public static LivingEntity pick(net.minecraft.world.entity.player.Player p, java.util.function.Predicate<LivingEntity> allow) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getViewVector(1f);
		AABB box = p.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(4.0);
		LivingEntity best = null;
		double bestDot = -2;
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box,
				e -> e != p && !e.isSpectator() && e.isAlive() && !GauntletState.activeGauntlet(e).isEmpty() && allow.test(e))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double dist = to.length();
			if (dist > RANGE || dist < 0.4) continue;
			double dot = to.scale(1 / dist).dot(look);
			double radius = Math.max(e.getBbWidth(), e.getBbHeight() * 0.5) * 0.5;
			double minDot = Math.cos(Math.toRadians(8) + Math.atan2(radius, dist));
			if (dot < minDot || !p.hasLineOfSight(e)) continue;
			if (dot > bestDot) {
				best = e;
				bestDot = dot;
			}
		}
		return best;
	}

	private static void serverTick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) return;
		long now = server.overworld().getGameTime();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Session s = SESSIONS.get(p.getUUID());
			if (s != null && !s.channeling && now - s.touched > KEEP_TICKS) SESSIONS.remove(p.getUUID());
			s = SESSIONS.get(p.getUUID());
			// A cycle that cannot continue (item switched, dropped...) must not stay "running".
			if (s != null && s.channeling && (!p.isUsingItem() || !p.getUseItem().is(ModItems.INFINITY_EXTRACTOR))) s.channeling = false;
			// Keep the model state honest: a stone inside = 4, progress is shown only on the extractor in the main hand.
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				ItemStack stack = p.getInventory().getItem(i);
				if (!stack.is(ModItems.INFINITY_EXTRACTOR)) continue;
				int want;
				if (!chamber(stack).isEmpty()) want = 4;
				else if (s != null && stack == s.extractor && stack == p.getMainHandItem()) want = s.channeling ? Math.max(1, s.cycles + 1) : idleState(s);
				else want = 0;
				setState(stack, want);
			}
		}
	}

	private static Stone stoneOf(ItemStack stack) {
		for (Stone s : Stone.values()) if (s.matches(stack)) return s;
		return Stone.POWER;
	}

	private static Component stoneName(Stone stone) {
		return Component.translatable(stone.item().getDescriptionId());
	}

	/** Status line, throttled so a held button does not flood the HUD. */
	private static void notice(ServerPlayer p, Component text, boolean always) {
		long now = p.level().getGameTime();
		Long last = LAST_NOTICE.get(p.getUUID());
		if (!always && last != null && now - last < 30) return;
		LAST_NOTICE.put(p.getUUID(), now);
		ModStatus.send(p, text);
	}

	private static void fx(ServerPlayer owner, int kind, LivingEntity target, Stone stone, int cycles) {
		ModNetwork.ExtractorFx packet = new ModNetwork.ExtractorFx(kind, owner.getId(), target == null ? -1 : target.getId(), stone.ordinal(), cycles);
		for (ServerPlayer viewer : PlayerLookup.around(owner.level(), owner.position(), 64)) ServerPlayNetworking.send(viewer, packet);
	}

	private StoneExtraction() {}
}
