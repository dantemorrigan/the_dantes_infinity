package com.dantemorrigan.gauntlet.boss;

import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModSounds;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.ability.AbilityController;
import com.dantemorrigan.gauntlet.cosmos.CosmosTravel;
import com.dantemorrigan.gauntlet.space.PlanetDestruction;
import com.dantemorrigan.gauntlet.stones.StoneRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TribunalEncounter {
	private record Checkpoint(Vec3 position, float yaw, float pitch, float health, CosmosTravel.Flight flight, ItemStack gauntlet) {
		static final Codec<Checkpoint> CODEC = RecordCodecBuilder.create(i -> i.group(
				Vec3.CODEC.fieldOf("position").forGetter(Checkpoint::position),
				Codec.FLOAT.fieldOf("yaw").forGetter(Checkpoint::yaw),
				Codec.FLOAT.fieldOf("pitch").forGetter(Checkpoint::pitch),
				Codec.FLOAT.fieldOf("health").forGetter(Checkpoint::health),
				CosmosTravel.Flight.CODEC.fieldOf("flight").forGetter(Checkpoint::flight),
				ItemStack.CODEC.fieldOf("gauntlet").forGetter(Checkpoint::gauntlet)).apply(i, Checkpoint::new));
	}

	private static final AttachmentType<Checkpoint> RETURN = AttachmentRegistry.<Checkpoint>builder().persistent(Checkpoint.CODEC)
			.buildAndRegister(InfinityGauntlet.id("tribunal_checkpoint"));
	private static final Map<MinecraftServer, Trial> TRIALS = new HashMap<>();
	private static final DustParticleOptions GOLD = new DustParticleOptions(0xffd18b, 2.4f);

	private static final class Trial {
		final UUID player;
		final LivingTribunal boss;
		final Vec3 origin;
		final float yaw, pitch, health;
		final CosmosTravel.Flight flight;
		Vec3 verdictOrigin;
		int verdictAge;
		boolean executing;
		Trial(ServerPlayer p, LivingTribunal boss) {
			player = p.getUUID();
			this.boss = boss;
			origin = p.position();
			yaw = p.getYRot();
			pitch = p.getXRot();
			health = p.getHealth();
			flight = p.getAttached(CosmosTravel.FLIGHT);
		}
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
				dispatcher.register(Commands.literal("tribunal").then(Commands.literal("challenge").executes(context -> {
					ServerPlayer player = context.getSource().getPlayerOrException();
					String reason = start(player);
					context.getSource().sendSystemMessage(Component.translatable("tribunal.infinity_gauntlet." + reason));
					return reason.equals("started") ? 1 : 0;
				}))));
		ServerTickEvents.END_SERVER_TICK.register(TribunalEncounter::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(TRIALS::remove);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Trial trial = TRIALS.get(server);
			if (trial != null && trial.player.equals(handler.getPlayer().getUUID())) close(server, trial);
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			Checkpoint checkpoint = player.getAttached(RETURN);
			if (checkpoint != null && player.isAlive()) {
				TeleportTransition target = respawn(player, TeleportTransition.DO_NOTHING);
				if (target != null) player.teleport(target);
				restore(player, checkpoint);
			}
		});
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, player, alive) -> {
			if (alive) return;
			Checkpoint checkpoint = oldPlayer.getAttached(RETURN);
			if (checkpoint == null) return;
			player.getInventory().replaceWith(oldPlayer.getInventory());
			player.experienceLevel = oldPlayer.experienceLevel;
			player.totalExperience = oldPlayer.totalExperience;
			player.experienceProgress = oldPlayer.experienceProgress;
			restore(player, checkpoint);
			oldPlayer.removeAttached(RETURN);
			oldPlayer.removeAttached(CosmosTravel.FLIGHT);
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer p)) return true;
			Trial trial = trial(p);
			return trial == null || trial.verdictAge == 0 || trial.executing;
		});
	}

	public static boolean inTrial(ServerPlayer p) { return trial(p) != null; }
	public static boolean awaitingReturn(ServerPlayer p) { return p.hasAttached(RETURN); }
	public static boolean isChallenger(LivingTribunal boss, ServerPlayer p) {
		Trial trial = trial(p);
		return trial != null && trial.boss == boss && trial.verdictAge == 0;
	}

	private static Trial trial(ServerPlayer player) {
		Trial trial = TRIALS.get(player.level().getServer());
		return trial != null && trial.player.equals(player.getUUID()) ? trial : null;
	}

	static String start(ServerPlayer p) {
		ServerLevel level = p.level();
		MinecraftServer server = level.getServer();
		if (!CosmosTravel.inCosmos(p) || !p.hasAttached(CosmosTravel.FLIGHT) || !p.isAlive() || p.isSpectator()
				|| p.isCreative() || level.getDifficulty() == Difficulty.PEACEFUL) return "space_only";
		if (awaitingReturn(p) || TRIALS.containsKey(server) || PlanetDestruction.isBusy(p)) return "busy";
		if (!ThanosEncounter.defeatedForever(server) || !StoneRegistry.released(server, Stone.MIND) || !fullGauntlet(p)) return "locked";
		LivingTribunal boss = ModEntities.TRIBUNAL.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (boss == null) return "busy";
		Vec3 heading = p.getLookAngle().multiply(1, 0, 1).normalize();
		if (heading.horizontalDistanceSqr() < .1) heading = Vec3.directionFromRotation(0, p.getYRot());
		Vec3 base = p.position().add(heading.scale(53)).add(0, -25, 0);
		// Load only the spawn chunk so the entity can begin ticking immediately.
		level.getChunkAt(BlockPos.containing(base));
		float yaw = Loki.yawTo(base, p.position());
		boss.snapTo(base.x, base.y, base.z, yaw, 0);
		boss.setYBodyRot(yaw);
		if (!level.addFreshEntity(boss)) return "busy";
		TRIALS.put(server, new Trial(p, boss));
		AbilityController.cancel(p);
		CosmosTravel.cancelWarp(p);
		ModSounds.play(level, p.position(), ModSounds.TRIBUNAL_ARRIVAL, 3);
		level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 3, .5f);
		return "started";
	}

	private static boolean fullGauntlet(ServerPlayer player) {
		ItemStack stack = GauntletState.activeGauntlet(player);
		return !stack.isEmpty() && GauntletState.inserted(stack).size() == Stone.values().length;
	}

	private static void tick(MinecraftServer server) {
		Trial trial = TRIALS.get(server);
		if (trial == null) return;
		ServerPlayer p = server.getPlayerList().getPlayer(trial.player);
		LivingTribunal boss = trial.boss;
		if (p == null || boss.isRemoved() || !p.isAlive() || p.level() != boss.level() || p.isSpectator() || p.isCreative()) {
			if (p != null && p.isAlive() && p.hasAttached(RETURN)) {
				Checkpoint checkpoint = p.getAttached(RETURN);
				TeleportTransition target = respawn(p, TeleportTransition.DO_NOTHING);
				if (target != null) p.teleport(target);
				restore(p, checkpoint);
			}
			close(server, trial);
			return;
		}
		if (trial.verdictAge > 0) {
			runVerdict(trial, p);
			return;
		}
		if (!fullGauntlet(p) || p.distanceToSqr(boss) > 105 * 105) {
			close(server, trial);
			return;
		}
		if (boss.phase() == LivingTribunal.ARRIVAL) {
			boss.age(boss.sceneAge() + 1);
			if (boss.sceneAge() >= LivingTribunal.INTRO_TICKS) boss.phase(LivingTribunal.BATTLE);
			return;
		}
		if (p.getHealth() <= 2) {
			beginVerdict(trial, p);
			return;
		}
		tickBattle(trial, p);
	}

	private static void tickBattle(Trial trial, ServerPlayer p) {
		LivingTribunal boss = trial.boss;
		ServerLevel level = p.level();
		if (boss.attack() == 0) {
			if (--boss.nextAttack > 0) return;
			int kind = ++boss.attackCycle % 4 == 0 ? 3 : boss.attackCycle % 2 == 0 ? 2 : 1;
			boss.attack(kind, p.position());
			boss.age(0);
			level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 2.4f, .72f);
			if (boss.attackCycle == 3) ModSounds.play(level, p.position(), ModSounds.TRIBUNAL_SURRENDER, 3);
			return;
		}
		int age = boss.sceneAge() + 1;
		boss.age(age);
		Vec3 delta = boss.mark().subtract(p.position());
		int kind = boss.attack();
		double radius = kind == 3 ? 20 : kind == 2 ? 13 : 8;
		if (kind == 2 && age > 10 && age < LivingTribunal.WINDUP && delta.lengthSqr() < 24 * 24) {
			p.setDeltaMovement(p.getDeltaMovement().scale(.65).add(delta.normalize().scale(.57)));
			p.hurtMarked = true;
		}
		if (age == LivingTribunal.WINDUP) {
			Vec3 mark = boss.mark();
			level.sendParticles(GOLD, mark.x, mark.y, mark.z, 100, radius * .32, 2, radius * .32, .08);
			level.playSound(null, BlockPos.containing(mark), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 3, .55f);
			if (delta.lengthSqr() <= radius * radius) hit(trial, p, kind == 3 ? 19 : kind == 2 ? 12 : 14);
			boss.exposedUntil = level.getGameTime() + 33;
		}
		if (trial.verdictAge == 0 && age >= LivingTribunal.WINDUP + 12) {
			boss.attack(0, Vec3.ZERO);
			boss.age(0);
			boss.nextAttack = boss.getHealth() < boss.getMaxHealth() * .35f ? 10 : 23;
		}
	}

	private static void hit(Trial trial, ServerPlayer p, float damage) {
		if (p.getHealth() - damage <= 2) {
			beginVerdict(trial, p);
			return;
		}
		p.hurtServer(p.level(), p.damageSources().indirectMagic(trial.boss, trial.boss), damage);
		if (p.isAlive() && p.getHealth() <= 2) beginVerdict(trial, p);
	}

	private static void beginVerdict(Trial trial, ServerPlayer p) {
		ItemStack gauntlet = GauntletState.activeGauntlet(p);
		if (gauntlet.isEmpty()) return;
		AbilityController.cancel(p);
		CosmosTravel.cancelWarp(p);
		trial.verdictAge = 1;
		trial.verdictOrigin = p.position();
		p.setHealth(2);
		p.setAttached(RETURN, new Checkpoint(trial.origin, trial.yaw, trial.pitch, trial.health, trial.flight, gauntlet.copy()));
		p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		trial.boss.phase(LivingTribunal.VERDICT);
		trial.boss.attack(0, p.position().add(0, 1.3, 0));
		ModSounds.play(p.level(), p.position(), ModSounds.TRIBUNAL_SURRENDER, 3);
	}

	private static void runVerdict(Trial trial, ServerPlayer p) {
		int age = ++trial.verdictAge;
		trial.boss.age(age);
		p.setDeltaMovement(Vec3.ZERO);
		if (age == 2 || age % 4 == 0) {
			Vec3 anchor = trial.verdictOrigin;
			Vec3 focus = trial.boss.position().add(0, 29, 0);
			Vec3 look = trial.boss.mark().lerp(focus, Mth.clamp((age - 5) / 36.0, .1, 1)).subtract(anchor.add(0, p.getEyeHeight(), 0));
			float pitch = (float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance()));
			p.connection.teleport(anchor.x, anchor.y, anchor.z, Loki.yawTo(anchor, focus), pitch);
		}
		if (age == LivingTribunal.SHATTER_TICK) {
			p.level().playSound(null, trial.boss.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 5, .45f);
		}
		if (age >= LivingTribunal.VERDICT_TICKS) {
			trial.executing = true;
			p.kill(p.level());
			close(p.level().getServer(), trial);
		}
	}

	public static TeleportTransition respawn(ServerPlayer old, TeleportTransition.PostTeleportTransition post) {
		Checkpoint checkpoint = old.getAttached(RETURN);
		if (checkpoint == null) return null;
		ServerLevel space = old.level().getServer().getLevel(CosmosTravel.DIMENSION);
		return space == null ? null : new TeleportTransition(space, checkpoint.position, Vec3.ZERO, checkpoint.yaw, checkpoint.pitch, post);
	}

	private static void restore(ServerPlayer p, Checkpoint checkpoint) {
		ItemStack displaced = p.getOffhandItem();
		p.setItemInHand(InteractionHand.OFF_HAND, checkpoint.gauntlet.copy());
		if (!displaced.isEmpty() && !p.getInventory().add(displaced)) p.drop(displaced, false);
		p.setHealth(Math.min(checkpoint.health, p.getMaxHealth()));
		p.setAttached(CosmosTravel.FLIGHT, checkpoint.flight);
		p.removeAttached(RETURN);
	}

	static void defeated(LivingTribunal boss) {
		if (!(boss.level() instanceof ServerLevel level)) return;
		Trial trial = TRIALS.get(level.getServer());
		if (trial == null || trial.boss != boss) return;
		TRIALS.remove(level.getServer());
		boss.phase(LivingTribunal.DEPARTURE);
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(trial.player);
		if (p != null) {
			ModSounds.play(level, p.position(), ModSounds.TRIBUNAL_DEFEAT, 3);
			p.sendSystemMessage(Component.translatable("tribunal.infinity_gauntlet.victory"));
			net.minecraft.world.entity.ExperienceOrb.award(level, p.position(), 1600);
		}
	}

	private static void close(MinecraftServer server, Trial trial) {
		TRIALS.remove(server);
		trial.boss.discard();
	}

	private TribunalEncounter() {}
}
