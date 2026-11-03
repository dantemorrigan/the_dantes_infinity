package com.dantemorrigan.gauntlet.power;

import com.dantemorrigan.gauntlet.CoopRules;
import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.mixin.FallingBlockEntityAccessor;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.net.ModStatus;
import com.dantemorrigan.gauntlet.space.SpaceManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mind stone — telekinesis.
 * <ul>
 *   <li>Z on terrain: a chunk of real blocks (anything but unbreakable ones such as bedrock) is torn out and held in
 *       front of the player. Z again throws it: a ballistic cluster that bursts on impact, damages everything around
 *       and scatters back into real falling blocks.</li>
 *   <li>Z on a mob: the mob is lifted and held. Z throws it, Shift+Z tears it apart.</li>
 * </ul>
 * While held, the blocks exist only as data (clients draw them from the real block models), so they follow the view
 * smoothly; nothing is lost on logout, death or dimension change — the cluster is dropped as falling blocks.
 */
public final class MindStone {
	private static final int LIFT_TICKS = 14;

	private static final class Hold {
		final List<BlockState> states = new ArrayList<>();
		final List<BlockPos> offsets = new ArrayList<>();
		LivingEntity mob;
		ServerLevel level;
		Vec3 lastPoint;
		boolean hadNoAi, hadNoGravity;
		double radius;
		int tick;
		int tear = -1;
	}

	private static final class Flight {
		final ServerLevel level;
		final UUID owner;
		final int id;
		final List<BlockState> states;
		final List<BlockPos> offsets;
		final double radius;
		Vec3 pos, vel;
		int tick;

		Flight(ServerLevel level, UUID owner, int id, List<BlockState> states, List<BlockPos> offsets, double radius, Vec3 pos, Vec3 vel) {
			this.level = level;
			this.owner = owner;
			this.id = id;
			this.states = states;
			this.offsets = offsets;
			this.radius = radius;
			this.pos = pos;
			this.vel = vel;
		}
	}

	private record ThrownMob(LivingEntity mob, ServerLevel level, UUID owner, Set<Integer> hit, int[] tick, boolean loose) {}

	private static final Map<UUID, Hold> HOLDS = new HashMap<>();
	private static final List<Flight> FLIGHTS = new ArrayList<>();
	private static final List<ThrownMob> THROWN = new ArrayList<>();
	private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
	private static int nextFlight = 1;

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(MindStone::tick);
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> drop(h.getPlayer()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) drop(p);
			HOLDS.clear();
			FLIGHTS.clear();
			THROWN.clear();
		});
	}

	public static boolean holding(ServerPlayer player) {
		return HOLDS.containsKey(player.getUUID());
	}

	public static void action(ServerPlayer player, int action) {
		Hold hold = HOLDS.get(player.getUUID());
		if (hold != null) {
			if (hold.level != player.level() || hold.mob != null && hold.mob.level() != player.level()) {
				drop(player);
				return;
			}
			if (action == 1 && hold.mob != null && !(hold.mob instanceof ServerPlayer)) startTear(player, hold);
			else if (action == 1) drop(player);
			else if (hold.tear < 0) throwHeld(player, hold);
			return;
		}
		if (action != 0) return;
		ItemStack g = GauntletState.activeGauntlet(player);
		if (g.isEmpty() || !GauntletState.inserted(g).contains(Stone.MIND)) {
			ModStatus.send(player, Component.literal("Нужна перчатка с Камнем Разума в левой руке"));
			return;
		}
		if (SpaceManager.inSpace(player) || player.isSpectator()) return;
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now < COOLDOWN.getOrDefault(player.getUUID(), 0L) && !player.isCreative()) return;
		GauntletConfig cfg = GauntletConfig.get();

		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1f);
		BlockHitResult bh = level.clip(new ClipContext(eye, eye.add(look.scale(cfg.mindRange)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		double reach = bh.getType() == HitResult.Type.MISS ? cfg.mindRange : bh.getLocation().distanceTo(eye);
		LivingEntity target = lookTarget(player, reach, cfg.mindPlayerGrab);
		if (target != null) {
			grabMob(player, target);
		} else if (bh.getType() == HitResult.Type.BLOCK) {
			grabBlocks(player, bh.getBlockPos());
		} else {
			return;
		}
		if (HOLDS.containsKey(player.getUUID())) {
			COOLDOWN.put(player.getUUID(), now + cfg.mindCooldownTicks);
			GauntletState.setInUse(g, EnumSet.of(Stone.MIND));
			player.swing(net.minecraft.world.InteractionHand.OFF_HAND, true);
		}
	}

	/** The living entity under the crosshair (bosses and players excluded), not farther than {@code reach}. */
	public static LivingEntity lookTarget(ServerPlayer player, double reach) {
		return lookTarget(player, reach, false);
	}

	static LivingEntity lookTarget(ServerPlayer player, double reach, boolean allowPlayers) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1f);
		Vec3 end = eye.add(look.scale(reach));
		AABB box = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.5);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
				e -> e instanceof LivingEntity l && l.isAlive() && !e.isSpectator() && (!(e instanceof Player) || allowPlayers && CoopRules.canAffect(player, e)) && !isBoss(e), reach * reach);
		return hit != null ? (LivingEntity) hit.getEntity() : null;
	}

	public static boolean isBoss(Entity e) {
		return e instanceof EnderDragon || e instanceof WitherBoss;
	}

	// ------------------------------------------------------------------ grab

	private static void grabBlocks(ServerPlayer player, BlockPos center) {
		if (!GauntletConfig.get().abilityBlockChanges) return;
		ServerLevel level = player.level();
		GauntletConfig cfg = GauntletConfig.get();
		double r = cfg.mindGrabRadius;
		int ir = (int) Math.ceil(r);
		var rnd = level.getRandom();
		record Cand(BlockPos pos, double d) {}
		List<Cand> cands = new ArrayList<>();
		for (int dx = -ir; dx <= ir; dx++)
			for (int dy = -ir; dy <= ir; dy++)
				for (int dz = -ir; dz <= ir; dz++) {
					double d = Math.sqrt(dx * dx + dy * dy * 1.3 + dz * dz) + rnd.nextDouble() * 0.9; // ragged chunk
					if (d > r) continue;
					BlockPos p = center.offset(dx, dy, dz);
					if (grabbable(level, player, p)) cands.add(new Cand(p, d));
				}
		if (cands.isEmpty()) {
			ModStatus.send(player, Component.literal("§eЭто не поднять"));
			return;
		}
		cands.sort(Comparator.comparingDouble(Cand::d));
		Hold hold = new Hold();
		double maxD = 0;
		for (Cand c : cands) {
			if (hold.states.size() >= cfg.mindMaxBlocks) break;
			BlockState s = level.getBlockState(c.pos());
			hold.states.add(s);
			BlockPos off = c.pos().subtract(center);
			hold.offsets.add(off);
			maxD = Math.max(maxD, Math.sqrt(off.distSqr(BlockPos.ZERO)));
		}
		hold.radius = maxD + 0.8;
		// Tear it out (fluids that were inside, e.g. waterlogging, stay behind).
		for (int i = 0; i < hold.states.size(); i++) {
			BlockPos p = center.offset(hold.offsets.get(i));
			level.setBlock(p, hold.states.get(i).getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
		}
		HOLDS.put(player.getUUID(), hold);
		Vec3 origin = Vec3.atCenterOf(center);
		hold.level = level;
		hold.lastPoint = origin;
		send(level, origin, new ModNetwork.MindFx(0, player.getId(), 0, origin, Vec3.ZERO, ids(hold.states), List.copyOf(hold.offsets)));
		level.playSound(null, center, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7F, 0.6F);
		level.playSound(null, center, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.6F, 0.5F);
	}

	private static boolean grabbable(ServerLevel level, ServerPlayer player, BlockPos p) {
		if (!CoopRules.canEdit(player, level, p)) return false;
		BlockState s = level.getBlockState(p);
		return !s.isAir() && !(s.getBlock() instanceof LiquidBlock) && !s.hasBlockEntity()
				&& s.getDestroySpeed(level, p) >= 0;
	}

	public static boolean holdsEntity(LivingEntity entity) {
		return HOLDS.values().stream().anyMatch(hold -> hold.mob == entity);
	}

	private static void grabMob(ServerPlayer player, LivingEntity mob) {
		if (holdsEntity(mob) || SoulStone.isExtracting(mob)) return;
		if (mob instanceof ServerPlayer victim && victim.isShiftKeyDown()) return;
		THROWN.removeIf(t -> t.mob() == mob);
		Hold hold = new Hold();
		hold.mob = mob;
		hold.level = player.level();
		hold.radius = Math.max(mob.getBbWidth(), mob.getBbHeight()) * 0.6 + 0.4;
		hold.hadNoGravity = mob.isNoGravity();
		if (mob instanceof Mob m) {
			hold.hadNoAi = m.isNoAi();
			m.setNoAi(true);
		}
		if (!(mob instanceof ServerPlayer)) mob.setNoGravity(true);
		HOLDS.put(player.getUUID(), hold);
		ServerLevel level = player.level();
		send(level, mob.position(), new ModNetwork.MindFx(1, player.getId(), mob.getId(), mob.position(), Vec3.ZERO, List.of(), List.of()));
		level.playSound(null, mob.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.2F, 0.7F);
	}

	// ------------------------------------------------------------------ release

	public static void throwLoose(ServerPlayer player, LivingEntity mob) {
		mob.setDeltaMovement(player.getLookAngle().scale(2).add(0, 0.2, 0));
		mob.hurtMarked = true;
		THROWN.removeIf(thrown -> thrown.mob() == mob);
		THROWN.add(new ThrownMob(mob, player.level(), player.getUUID(), new HashSet<>(), new int[1], true));
	}

	private static void throwHeld(ServerPlayer player, Hold hold) {
		ServerLevel level = player.level();
		GauntletConfig cfg = GauntletConfig.get();
		Vec3 look = player.getViewVector(1f);
		if (hold.level != level || hold.mob instanceof ServerPlayer victim && !playerHoldValid(player, victim, hold)) {
			drop(player);
			return;
		}
		end(player);
		if (hold.mob != null) {
			LivingEntity mob = hold.mob;
			restore(hold);
			mob.setDeltaMovement(look.scale(cfg.mindMobThrowSpeed).add(0, 0.15, 0));
			mob.hurtMarked = true;
			THROWN.add(new ThrownMob(mob, player.level(), player.getUUID(), new HashSet<>(), new int[1], false));
		} else {
			Vec3 start = holdPoint(player, hold);
			Flight f = new Flight(level, player.getUUID(), nextFlight++, hold.states, hold.offsets, hold.radius, start, look.scale(cfg.mindThrowSpeed));
			FLIGHTS.add(f);
			send(level, start, new ModNetwork.MindFx(3, player.getId(), f.id, start, f.vel, ids(f.states), List.copyOf(f.offsets)));
		}
		level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.5F, 0.6F);
		player.swing(net.minecraft.world.InteractionHand.OFF_HAND, true);
	}

	/** Lets go without throwing: blocks fall as real falling blocks, a mob simply drops. */
	public static void drop(ServerPlayer player) {
		Hold hold = HOLDS.get(player.getUUID());
		if (hold == null) return;
		end(player);
		if (hold.mob != null) {
			restore(hold);
			return;
		}
		scatter(hold.level, hold.lastPoint, hold.states, hold.offsets, Vec3.ZERO, 0.05);
	}

	private static void end(ServerPlayer player) {
		Hold hold = HOLDS.remove(player.getUUID());
		ServerLevel level = hold == null ? player.level() : hold.level;
		Vec3 at = level == player.level() ? player.position() : hold.lastPoint;
		if (at == null && hold != null && hold.mob != null) at = hold.mob.position();
		if (at == null) at = player.position();
		send(level, at, new ModNetwork.MindFx(2, player.getId(), 0, Vec3.ZERO, Vec3.ZERO, List.of(), List.of()));
		ItemStack g = GauntletState.activeGauntlet(player);
		if (!g.isEmpty()) GauntletState.setInUse(g, EnumSet.noneOf(Stone.class));
	}

	private static void restore(Hold hold) {
		LivingEntity mob = hold.mob;
		if (mob instanceof Mob m) m.setNoAi(hold.hadNoAi);
		if (!(mob instanceof ServerPlayer)) mob.setNoGravity(hold.hadNoGravity);
		mob.fallDistance = 0;
	}

	private static void startTear(ServerPlayer player, Hold hold) {
		if (hold.tear >= 0) return;
		hold.tear = 0;
		send(player.level(), hold.mob.position(), new ModNetwork.MindFx(5, player.getId(), hold.mob.getId(), hold.mob.position(), Vec3.ZERO, List.of(), List.of()));
		player.level().playSound(null, hold.mob.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 0.6F);
	}

	// ------------------------------------------------------------------ tick

	/** Where the held thing floats: in front of the eyes, farther for bigger chunks. */
	private static Vec3 holdPoint(ServerPlayer player, Hold hold) {
		return player.getEyePosition().add(player.getViewVector(1f).scale(3.0 + hold.radius * 1.2));
	}

	private static void tick(MinecraftServer server) {
		// A copy: dropping, throwing and tearing apart remove entries while we walk.
		for (Map.Entry<UUID, Hold> e : new ArrayList<>(HOLDS.entrySet())) {
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			Hold h = e.getValue();
			if (p == null) { // left the game: a mob is let go; blocks were already dropped on disconnect
				if (h.mob != null) restore(h);
				else scatter(h.level, h.lastPoint, h.states, h.offsets, Vec3.ZERO, 0.05);
				HOLDS.remove(e.getKey());
				continue;
			}
			ItemStack g = GauntletState.activeGauntlet(p);
			boolean lost = !p.isAlive() || h.level != p.level() || g.isEmpty() || !GauntletState.inserted(g).contains(Stone.MIND)
					|| h.mob != null && (!h.mob.isAlive() || h.mob.level() != p.level() || h.mob.position().distanceTo(p.position()) > 64);
			if (lost || h.mob instanceof ServerPlayer victim && !playerHoldValid(p, victim, h)) {
				drop(p);
				continue;
			}
			h.tick++;
			if (h.mob != null) holdMob(p, h);
			else h.lastPoint = holdPoint(p, h);
		}
		// Thrown clusters
		for (Iterator<Flight> it = FLIGHTS.iterator(); it.hasNext(); ) {
			Flight f = it.next();
			if (stepFlight(server, f)) it.remove();
		}
		// Thrown mobs
		for (Iterator<ThrownMob> it = THROWN.iterator(); it.hasNext(); ) {
			ThrownMob t = it.next();
			LivingEntity mob = t.mob();
			int tick = ++t.tick()[0];
			if (!mob.isAlive() || mob.level() != t.level() || tick > 80) {
				it.remove();
				continue;
			}
			mob.fallDistance = 0;
			ServerLevel level = (ServerLevel) mob.level();
			ServerPlayer owner = server.getPlayerList().getPlayer(t.owner());
			if (!CoopRules.canAffect(owner, mob)) {
				it.remove();
				continue;
			}
			double speed = mob.getDeltaMovement().length();
			for (Entity other : level.getEntities(mob, mob.getBoundingBox().inflate(0.4), x -> x instanceof LivingEntity && x != owner && CoopRules.canAffect(owner, x) && !t.hit().contains(x.getId()))) {
				t.hit().add(other.getId());
				other.hurtServer(level, owner != null ? level.damageSources().playerAttack(owner) : level.damageSources().magic(), GauntletConfig.get().mindMobThrowDamage * 0.6f);
				other.push(mob.getDeltaMovement().scale(0.8));
				if (t.loose()) {
					mob.hurtServer(level, owner != null ? level.damageSources().playerAttack(owner) : level.damageSources().magic(),
							GauntletConfig.get().mindMobThrowDamage * 0.6f);
					break;
				}
			}
			if (t.loose() && !t.hit().isEmpty()) {
				it.remove();
				continue;
			}
			if (tick > 2 && (mob.horizontalCollision || mob.onGround() || mob.verticalCollision)) {
				float dmg = (float) (GauntletConfig.get().mindMobThrowDamage * Math.min(1.5, 0.4 + speed));
				mob.hurtServer(level, owner != null ? level.damageSources().playerAttack(owner) : level.damageSources().magic(), dmg);
				Vec3 at = mob.position().add(0, mob.getBbHeight() / 2, 0);
				send(level, at, new ModNetwork.MindFx(7, owner != null ? owner.getId() : -1, mob.getId(), at, Vec3.ZERO, List.of(), List.of()));
				level.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_BIG_FALL, SoundSource.HOSTILE, 1.5F, 0.7F);
				it.remove();
			}
		}
	}

	private static boolean playerHoldValid(ServerPlayer owner, ServerPlayer victim, Hold hold) {
		return GauntletConfig.get().mindPlayerGrab && hold.tick < 40 && !victim.isShiftKeyDown()
				&& victim.level().getEntity(victim.getUUID()) == victim && CoopRules.canAffect(owner, victim)
				&& owner.hasLineOfSight(victim);
	}

	private static void holdMob(ServerPlayer p, Hold h) {
		LivingEntity mob = h.mob;
		Vec3 target = holdPoint(p, h).subtract(0, mob.getBbHeight() / 2, 0);
		Vec3 now = mob.position();
		if (mob instanceof ServerPlayer) {
			Vec3 pull = target.subtract(now).scale(0.25);
			if (pull.lengthSqr() > 0.49) pull = pull.normalize().scale(0.7);
			mob.setDeltaMovement(pull);
			mob.fallDistance = 0;
			mob.hurtMarked = true;
			return;
		}
		// Rise smoothly from where it stood, then follow the view.
		float k = Math.min(1f, h.tick / (float) LIFT_TICKS);
		Vec3 pos = now.add(target.subtract(now).scale(0.15 + 0.35 * k));
		if (h.tear >= 0) {
			h.tear++;
			var rnd = mob.getRandom();
			double shake = 0.04 + 0.12 * h.tear / (double) GauntletConfig.get().mindRuptureTicks;
			pos = pos.add((rnd.nextDouble() - 0.5) * shake, (rnd.nextDouble() - 0.5) * shake, (rnd.nextDouble() - 0.5) * shake);
			if (h.tear % 5 == 0) p.level().playSound(null, mob.blockPosition(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.HOSTILE, 1.2F, 0.5F + h.tear * 0.03F);
			if (h.tear >= GauntletConfig.get().mindRuptureTicks) {
				rupture(p, h);
				return;
			}
		}
		mob.setPos(pos.x, pos.y, pos.z);
		mob.setDeltaMovement(Vec3.ZERO);
		mob.fallDistance = 0;
		mob.hurtMarked = true;
	}

	private static void rupture(ServerPlayer p, Hold h) {
		LivingEntity mob = h.mob;
		ServerLevel level = p.level();
		end(p);
		restore(h);
		Vec3 at = mob.position().add(0, mob.getBbHeight() / 2, 0);
		// The visual first: clients snapshot the model before the entity disappears.
		send(level, at, new ModNetwork.MindFx(6, p.getId(), mob.getId(), at, Vec3.ZERO, List.of(), List.of()));
		level.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 1.5F);
		level.playSound(null, mob.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 2.0F, 0.5F);
		int xp = mob instanceof Mob m && m.shouldDropExperience() ? mob.getExperienceReward(level, p) : 0;
		mob.hurtServer(level, level.damageSources().playerAttack(p), Float.MAX_VALUE);
		if (!mob.isAlive()) {
			if (xp > 0) net.minecraft.world.entity.ExperienceOrb.award(level, at, xp);
			mob.discard(); // torn apart: no corpse, the pieces are drawn by the clients
		}
	}

	/** One tick of a thrown cluster; true when it is finished. */
	private static boolean stepFlight(MinecraftServer server, Flight f) {
		f.tick++;
		Vec3 next = f.pos.add(f.vel);
		ServerPlayer owner = server.getPlayerList().getPlayer(f.owner);
		boolean impact = false;
		Vec3 at = next;
		BlockHitResult bh = f.level.clip(new ClipContext(f.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
		if (bh.getType() == HitResult.Type.BLOCK) {
			impact = true;
			at = bh.getLocation();
		}
		AABB sweep = new AABB(f.pos, next).inflate(f.radius * 0.8);
		List<Entity> hit = f.level.getEntities((Entity) null, sweep, x -> x instanceof LivingEntity l && l.isAlive() && x != owner && !x.isSpectator() && CoopRules.canAffect(owner, x));
		if (!hit.isEmpty()) impact = true;
		if (!impact && (f.tick > 200 || next.y < f.level.getMinY() - 16)) return true; // lost in the void
		if (!impact) {
			f.pos = next;
			f.vel = f.vel.scale(0.99).add(0, -0.05, 0);
			return false;
		}
		impact(f, at, owner);
		return true;
	}

	private static void impact(Flight f, Vec3 at, ServerPlayer owner) {
		ServerLevel level = f.level;
		GauntletConfig cfg = GauntletConfig.get();
		float dmg = cfg.mindThrowDamage * (1f + f.states.size() / 32f) * (float) Math.min(1.4, 0.5 + f.vel.length() / 2);
		double r = f.radius + 2;
		for (Entity e : level.getEntities((Entity) null, new AABB(at, at).inflate(r), x -> x instanceof LivingEntity && x != owner && x.isAlive() && !x.isSpectator() && CoopRules.canAffect(owner, x))) {
			double d = e.position().distanceTo(at);
			if (d > r) continue;
			float k = (float) (1 - d / r * 0.6);
			e.hurtServer(level, owner != null ? level.damageSources().playerAttack(owner) : level.damageSources().magic(), dmg * k);
			Vec3 push = e.position().subtract(at).normalize().scale(0.9 * k).add(f.vel.scale(0.25)).add(0, 0.35, 0);
			e.push(push);
			e.hurtMarked = true;
		}
		send(level, at, new ModNetwork.MindFx(4, owner != null ? owner.getId() : -1, f.id, at, f.vel, List.of(), List.of()));
		level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6F, 0.75F);
		level.playSound(null, BlockPos.containing(at), SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 2.0F, 0.4F);
		// Back to real blocks: debris thrown around the impact point, settling as normal blocks.
		scatter(level, at.subtract(f.vel.normalize().scale(0.6)), f.states, f.offsets, f.vel.scale(0.25), 0.45);
	}

	private static void scatter(ServerLevel level, Vec3 center, List<BlockState> states, List<BlockPos> offsets, Vec3 base, double spread) {
		var rnd = level.getRandom();
		for (int i = 0; i < states.size(); i++) {
			BlockPos off = offsets.get(i);
			Vec3 pos = center.add(off.getX() * 0.8, off.getY() * 0.8, off.getZ() * 0.8);
			// Never spawn inside solid terrain: step up out of it.
			BlockPos bp = BlockPos.containing(pos);
			for (int up = 0; up < 6 && !level.getBlockState(bp).getCollisionShape(level, bp).isEmpty(); up++) bp = bp.above();
			pos = new Vec3(pos.x, Math.max(pos.y, bp.getY()), pos.z);
			FallingBlockEntity fb = FallingBlockEntityAccessor.infinityGauntlet$create(level, pos.x, pos.y, pos.z, states.get(i));
			Vec3 out = new Vec3(off.getX(), off.getY() + 0.5, off.getZ());
			out = out.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : out.normalize();
			fb.setDeltaMovement(base.add(out.scale(spread * (0.5 + rnd.nextDouble()))).add(0, spread * 0.6, 0));
			fb.dropItem = false;
			fb.setHurtsEntities(1.5f, 20);
			level.addFreshEntity(fb);
		}
	}

	private static List<Integer> ids(List<BlockState> states) {
		List<Integer> ids = new ArrayList<>(states.size());
		for (BlockState s : states) ids.add(Block.getId(s));
		return ids;
	}

	private static void send(ServerLevel level, Vec3 at, ModNetwork.MindFx fx) {
		for (ServerPlayer p : PlayerLookup.around(level, at, 160)) ServerPlayNetworking.send(p, fx);
	}

	private MindStone() {}
    public static int cooldown(ServerPlayer p) { return (int)Math.max(0, COOLDOWN.getOrDefault(p.getUUID(),0L)-p.level().getGameTime()); }

    public static boolean holdingMob(ServerPlayer p) { Hold h=HOLDS.get(p.getUUID()); return h!=null && h.mob!=null && !(h.mob instanceof ServerPlayer) && h.tear<0; }

}
