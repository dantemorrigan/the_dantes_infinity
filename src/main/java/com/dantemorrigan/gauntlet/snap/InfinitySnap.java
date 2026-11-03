package com.dantemorrigan.gauntlet.snap;

import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModComponents;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.net.ModStatus;
import com.dantemorrigan.gauntlet.space.SpaceManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The Infinity Snap. With all six stones the gauntlet hand turns a little and snaps: the universe is erased and every
 * player is moved to the White Void, an endless white room where they can only walk. The snapping gauntlet and its
 * stones burn out ({@link ModComponents#SNAPPED}) and no longer work. The only way back is to take the (burnt) Time
 * stone out of the gauntlet and use it on its own: that rewinds everyone to the moment before the snap and gives the
 * snapper the gauntlet back as it was.
 */
public final class InfinitySnap {
	public static final ResourceKey<Level> WHITE_VOID = ResourceKey.create(Registries.DIMENSION, InfinityGauntlet.id("white_void"));
	public static final Identifier SOUND_ID = InfinityGauntlet.id("infinity_snap");
	public static final SoundEvent SOUND = Registry.register(BuiltInRegistries.SOUND_EVENT, SOUND_ID, SoundEvent.createVariableRangeEvent(SOUND_ID));

	/** Ticks from the cast: the snap sound (its click is right at the start), and the moment the universe is gone. */
	public static final int SNAP_TICK = 9, ERASE_TICK = 50;
	/** Length of the Time stone rewind back to the old world. */
	public static final int REWIND_TICKS = 50;
	/** Floor (invisible barrier layer) is at y = 0. */
	private static final Vec3 ROOM = new Vec3(0.5, 1.0, 0.5);

	/** Where a player stood when the universe was erased; the snapper also keeps the gauntlet as it was. */
	public record SnapRecord(long event, ResourceKey<Level> dimension, Vec3 pos, float yRot, float xRot, Optional<ItemStack> gauntlet) {
		public static final Codec<SnapRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("event").forGetter(SnapRecord::event),
				ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(SnapRecord::dimension),
				Vec3.CODEC.fieldOf("pos").forGetter(SnapRecord::pos),
				Codec.FLOAT.fieldOf("y_rot").forGetter(SnapRecord::yRot),
				Codec.FLOAT.fieldOf("x_rot").forGetter(SnapRecord::xRot),
				ItemStack.CODEC.optionalFieldOf("gauntlet").forGetter(SnapRecord::gauntlet)
		).apply(i, SnapRecord::new));
	}

	public static final AttachmentType<SnapRecord> RECORD = AttachmentRegistry.<SnapRecord>builder()
			.persistent(SnapRecord.CODEC)
			.copyOnDeath()
			.buildAndRegister(InfinityGauntlet.id("snap_record"));

	private record Cast(int started, ItemStack before) {}

	private static final Map<UUID, Cast> CASTS = new HashMap<>();
	private static final Map<UUID, Integer> REWINDS = new HashMap<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(InfinitySnap::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {CASTS.clear();REWINDS.clear();});
		// In the White Void you can only walk: no building, breaking, fighting or using things,
		// except the gauntlet (to take the Time stone out) and the Time stone itself.
		AttackBlockCallback.EVENT.register((p, level, hand, pos, dir) -> inVoid(p) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((p, level, hand, hit) -> inVoid(p) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackEntityCallback.EVENT.register((p, level, hand, e, hit) -> inVoid(p) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((p, level, hand, e, hit) -> inVoid(p) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((p, level, hand) -> {
			ItemStack stack = p.getItemInHand(hand);
			return !inVoid(p) || stack.is(ModItems.TIME_STONE) || stack.is(ModItems.GAUNTLET) ? InteractionResult.PASS : InteractionResult.FAIL;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, source, amount) -> !(e instanceof ServerPlayer p && (inVoid(p) || REWINDS.containsKey(p.getUUID()))));
	}

	public static boolean inVoid(Player player) {
		return player.level().dimension().equals(WHITE_VOID);
	}

	public static boolean snapped(ItemStack stack) {
		return Boolean.TRUE.equals(stack.get(ModComponents.SNAPPED));
	}

	public static boolean casting(ServerPlayer player) {
		return CASTS.containsKey(player.getUUID());
	}

	/** Starts the snap (already validated: six stones, gauntlet intact, normal world). */
	public static void cast(ServerPlayer player) {
		ItemStack gauntlet = GauntletState.activeGauntlet(player);
		if (gauntlet.isEmpty() || snapped(gauntlet) || CASTS.containsKey(player.getUUID())) return;
		CASTS.put(player.getUUID(), new Cast(player.tickCount, gauntlet.copy()));
		GauntletState.setInUse(gauntlet, EnumSet.allOf(Stone.class));
		ModNetwork.GestureFx gesture = new ModNetwork.GestureFx(player.getId(), 1);
		for (ServerPlayer p : player.level().players()) ServerPlayNetworking.send(p, gesture);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
			Cast cast = CASTS.get(p.getUUID());
			if (cast != null) castTick(server, p, cast);
			Integer rewind = REWINDS.get(p.getUUID());
			if (rewind != null) {
				if (rewind <= 1) {REWINDS.remove(p.getUUID());restore(p);}
				else REWINDS.put(p.getUUID(), rewind - 1);
				continue;
			}
			SnapRecord record = p.getAttached(RECORD);
			if (record != null && !inVoid(p) && p.isAlive() && p.tickCount > 20) sendToVoid(p); // relog / respawn / other exits
			if (inVoid(p)) {
				p.fallDistance = 0;
				if (p.getY() < 0.5 || p.getY() > 200) teleport(p, p.level(), ROOM, p.getYRot(), p.getXRot());
			}
		}
		CASTS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	private static void castTick(MinecraftServer server, ServerPlayer p, Cast cast) {
		int age = p.tickCount - cast.started();
		if (age == SNAP_TICK) {
			p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SOUND, SoundSource.PLAYERS, 4.0f, 1.0f);
			burn(GauntletState.wornGauntlet(p));
			ModNetwork.SnapFx fade = new ModNetwork.SnapFx(0, ERASE_TICK - SNAP_TICK);
			for (ServerPlayer viewer : server.getPlayerList().getPlayers()) if (affected(viewer)) ServerPlayNetworking.send(viewer, fade);
		}
		if (age < ERASE_TICK) return;
		CASTS.remove(p.getUUID());
		long event = server.overworld().getGameTime();
		for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
			if (!affected(viewer)) continue;
			viewer.setAttached(RECORD, new SnapRecord(event, viewer.level().dimension(), viewer.position(), viewer.getYRot(), viewer.getXRot(),
					viewer == p ? Optional.of(cast.before()) : Optional.empty()));
			sendToVoid(viewer);
		}
		ModStatus.send(p, Component.translatable("snap.infinity_gauntlet.erased"));
	}

	/** Players outside Dead Space and not already erased. */
	private static boolean affected(ServerPlayer p) {
		return p.isAlive() && !p.isSpectator() && !inVoid(p) && !p.hasAttached(RECORD) && !SpaceManager.inSpace(p) && !p.hasAttached(SpaceManager.RECORD);
	}

	/** The gauntlet and every stone inside burn out. */
	public static void burn(ItemStack gauntlet) {
		if (gauntlet.isEmpty()) return;
		NonNullList<ItemStack> items = NonNullList.withSize(Stone.values().length, ItemStack.EMPTY);
		gauntlet.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
		for (ItemStack stone : items) if (!stone.isEmpty()) stone.set(ModComponents.SNAPPED, true);
		gauntlet.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
		gauntlet.set(ModComponents.SNAPPED, true);
		GauntletState.setInUse(gauntlet, EnumSet.noneOf(Stone.class));
	}

	private static void sendToVoid(ServerPlayer p) {
		ServerLevel room = p.level().getServer().getLevel(WHITE_VOID);
		if (room == null) {
			InfinityGauntlet.LOGGER.error("White Void dimension is missing (datapack not loaded?)");
			return;
		}
		if (p.isUsingItem()) p.stopUsingItem();
		p.closeContainer();
		Vec3 offset = new Vec3((p.getRandom().nextDouble() - 0.5) * 4, 0, (p.getRandom().nextDouble() - 0.5) * 4);
		teleport(p, room, ROOM.add(offset), p.getYRot(), 0);
		ServerPlayNetworking.send(p, new ModNetwork.SnapFx(1, 40));
	}

	/**
	 * Time stone used on its own in the White Void: rewind everyone erased by the same snap.
	 * Returns false when there is nothing to rewind.
	 */
	public static boolean rewind(ServerPlayer user) {
		SnapRecord record = user.getAttached(RECORD);
		if (record == null || !inVoid(user)) return false;
		if (REWINDS.containsKey(user.getUUID())) return true;
		for (ServerPlayer p : user.level().getServer().getPlayerList().getPlayers()) {
			SnapRecord r = p.getAttached(RECORD);
			if (r == null || r.event() != record.event() || REWINDS.containsKey(p.getUUID())) continue;
			REWINDS.put(p.getUUID(), REWIND_TICKS);
			ServerPlayNetworking.send(p, new ModNetwork.TimeRewindFx(p.getId(), false, REWIND_TICKS));
			ServerPlayNetworking.send(p, new ModNetwork.SnapFx(2, REWIND_TICKS));
		}
		user.level().playSound(null, user.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0f, 0.5f);
		return true;
	}

	/** Back to the moment before the snap; the snapper gets the intact gauntlet back and the burnt pieces vanish. */
	private static void restore(ServerPlayer p) {
		SnapRecord record = p.removeAttached(RECORD);
		if (record == null) return;
		MinecraftServer server = p.level().getServer();
		ServerLevel target = server.getLevel(record.dimension());
		Vec3 pos = record.pos();
		if (target == null) {
			target = server.overworld();
			pos = Vec3.atBottomCenterOf(target.getRespawnData().pos());
		}
		p.closeContainer();
		if (record.gauntlet().isPresent()) {
			var inv = p.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack s = inv.getItem(i);
				if (snapped(s)) inv.setItem(i, ItemStack.EMPTY);
			}
			ItemStack gauntlet = record.gauntlet().get().copy();
			GauntletState.setInUse(gauntlet, EnumSet.noneOf(Stone.class));
			ItemStack off = p.getOffhandItem();
			if (!off.isEmpty() && !p.getInventory().add(off)) p.drop(off, false);
			p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, gauntlet);
		}
		p.fallDistance = 0;
		p.setDeltaMovement(Vec3.ZERO);
		teleport(p, target, pos, record.yRot(), record.xRot());
		target.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 1.4f);
		ServerPlayNetworking.send(p, new ModNetwork.SnapFx(3, 30));
	}

	private static void teleport(ServerPlayer p, ServerLevel level, Vec3 pos, float yRot, float xRot) {
		p.teleport(new TeleportTransition(level, pos, Vec3.ZERO, yRot, xRot, TeleportTransition.DO_NOTHING));
	}

	private InfinitySnap() {}
}
