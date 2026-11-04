package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.ModSounds;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Soul Stone: bring a tamed wolf to the Temple of Soul and let it fall into the chasm. The sky turns amber, a beam
 * strikes the clouds, everything darkens and the owner wakes in the soul world: a dark shallow sea under a red sun.
 * The Soul Stone rises from the water; taking it shows "Use the Soul Stone to return to the temple", and using it
 * brings the player back to the summit. Jumping in yourself just kills you.
 */
public final class SoulQuest {
	public static final ResourceKey<Level> SOUL_WORLD = ResourceKey.create(Registries.DIMENSION, InfinityGauntlet.id("soul_world"));
	public static final int RITUAL_TICKS = 150;
	private static final Vec3 ARRIVAL = new Vec3(0.5, 3.0, 0.5);
	private static final Vec3 STONE_AT = new Vec3(0.5, 2.6, 7.5);
	private static final double STONE_TOP = 4.4;

	private record Ritual(UUID owner, long start, BlockPos beam) {}

	private static Ritual ritual;
	private static ItemEntity rising;
	private static UUID seeker;
	private static long arrived;
	private static boolean stoneAlerted;

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SoulQuest::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> { ritual = null; rising = null; seeker = null; });
	}

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		if (ritual == null && now % 2 == 0 && SoulTemple.built(server) && !StoneRegistry.found(server, Stone.SOUL)) watchChasm(server);
		if (ritual != null) runRitual(server, now);
		if (seeker != null) runSoulWorld(server, now);
	}

	// ------------------------------------------------------------------ the sacrifice

	private static void watchChasm(MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos site = SoulTemple.site(server);
		AABB chasm = SoulTemple.chasm(site);
		if (!level.hasChunkAt(BlockPos.containing(chasm.getCenter()))) return;
		for (Wolf wolf : level.getEntitiesOfClass(Wolf.class, chasm, w -> w.isTame() && w.isAlive())) {
			if (!(wolf.getOwner() instanceof ServerPlayer owner) || owner.level() != level || owner.distanceToSqr(wolf) > 80 * 80) continue;
			sacrifice(owner, wolf);
			return;
		}
	}

	/** Starts the ritual (also the debug command without a wolf). */
	public static boolean sacrifice(ServerPlayer owner, Wolf wolf) {
		if (ritual != null || seeker != null) return false;
		MinecraftServer server = owner.level().getServer();
		ServerLevel level = server.overworld();
		BlockPos site = SoulTemple.site(server);
		BlockPos beam = BlockPos.containing(SoulTemple.chasm(site).getCenter().x, site.getY() - 20, SoulTemple.chasm(site).getCenter().z);
		if (wolf != null) {
			level.sendParticles(ModParticles.SOUL_ABSORPTION, wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 1, 0, 0, 0, 0);
			wolf.discard();
		}
		StoneRegistry.markFound(server, Stone.SOUL);
		ritual = new Ritual(owner.getUUID(), level.getGameTime(), beam);
		ModNetwork.SoulRitualFx fx = new ModNetwork.SoulRitualFx(0, beam, SoulTemple.summitY(site), RITUAL_TICKS);
		for (ServerPlayer p : PlayerLookup.around(level, Vec3.atCenterOf(beam), 400)) ServerPlayNetworking.send(p, fx);
		level.playSound(null, owner.blockPosition(), ModSounds.STONE_OMINOUS, SoundSource.AMBIENT, 3.0F, 0.8F);
		level.playSound(null, beam, SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT, 8.0F, 0.5F);
		return true;
	}

	private static void runRitual(MinecraftServer server, long now) {
		ServerPlayer owner = server.getPlayerList().getPlayer(ritual.owner());
		long t = now - ritual.start();
		ServerLevel level = server.overworld();
		if (t == 40) level.playSound(null, ritual.beam().above(80), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 20.0F, 0.5F);
		if (t < RITUAL_TICKS) return;
		ritual = null;
		if (owner == null || !owner.isAlive()) { StoneRegistry.forget(server, Stone.SOUL); return; }
		ServerLevel soul = server.getLevel(SOUL_WORLD);
		if (soul == null) return;
		owner.teleport(new TeleportTransition(soul, ARRIVAL, Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
		seeker = owner.getUUID();
		arrived = now;
		stoneAlerted = false;
		ServerPlayNetworking.send(owner, new ModNetwork.SoulRitualFx(1, BlockPos.ZERO, 0, 60));
	}

	// ------------------------------------------------------------------ the soul world

	private static void runSoulWorld(MinecraftServer server, long now) {
		ServerLevel soul = server.getLevel(SOUL_WORLD);
		ServerPlayer p = server.getPlayerList().getPlayer(seeker);
		boolean present = p != null && p.level() == soul;
		if (!present) {
			// Left (died, logged out) before taking the stone: the stone sinks back, the temple waits again.
			if (rising != null && !rising.isRemoved()) { rising.discard(); StoneRegistry.forget(server, Stone.SOUL); }
			else if (rising == null) StoneRegistry.forget(server, Stone.SOUL);
			rising = null;
			seeker = null;
			return;
		}
		long t = now - arrived;
		if (t == 50 && rising == null) {
			rising = new ItemEntity(soul, STONE_AT.x, STONE_AT.y, STONE_AT.z, new ItemStack(ModItems.SOUL_STONE), 0, 0, 0);
			rising.setNoGravity(true);
			rising.setNeverPickUp();
			rising.setGlowingTag(true);
			soul.addFreshEntity(rising);
			soul.playSound(null, BlockPos.containing(STONE_AT), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 2.0F, 0.6F);
		}
		if (rising != null && !rising.isRemoved()) {
			if (rising.getY() < STONE_TOP) {
				rising.setPos(STONE_AT.x, Math.min(STONE_TOP, rising.getY() + 0.025), STONE_AT.z);
				rising.setDeltaMovement(Vec3.ZERO);
				if (t % 3 == 0) soul.sendParticles(ModParticles.QUEST_SOUL_STONE_RISE, rising.getX(), rising.getY(), rising.getZ(), 1, 0, 0, 0, 0);
				if (t % 12 == 0) soul.sendParticles(ModParticles.QUEST_SOUL_WATER_RIPPLE, STONE_AT.x, 4.02, STONE_AT.z, 0, 0, 1, 0, 1.0);
				if (rising.getY() >= STONE_TOP) {
					rising.setPickUpDelay(10);
					StoneRegistry.markReleased(server, Stone.SOUL);
				}
			}
		}
		if (!stoneAlerted && p.getInventory().contains(s -> s.is(ModItems.SOUL_STONE))) {
			stoneAlerted = true;
			ServerPlayNetworking.send(p, new ModNetwork.QuestAlert(Component.translatable("message.infinity_gauntlet.quest.soul.home"), 1));
		}
	}

	public static boolean inSoulWorld(ServerPlayer p) { return p.level().dimension().equals(SOUL_WORLD); }

	/** Using the Soul Stone in the soul world: back to the summit of the temple. */
	public static void returnHome(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		((ServerLevel) p.level()).sendParticles(ModParticles.QUEST_SOUL_RETURN_FLARE, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
		Vec3 home = SoulTemple.summitStand(SoulTemple.site(server));
		p.teleport(new TeleportTransition(server.overworld(), home, Vec3.ZERO, p.getYRot(), 0, TeleportTransition.DO_NOTHING));
		server.overworld().sendParticles(ModParticles.QUEST_SOUL_RETURN_FLARE, home.x, home.y + 1, home.z, 1, 0, 0, 0, 0);
		server.overworld().playSound(null, BlockPos.containing(home), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 1.5F, 0.7F);
		seeker = null;
		rising = null;
	}

	private SoulQuest() {}
}
