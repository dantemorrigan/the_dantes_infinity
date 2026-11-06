package com.dantemorrigan.gauntlet.boss;

import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.GauntletEffects;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
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

/** A voluntary late-game trial; progression, attacks, confiscation and death are owned by the server. */
public final class TribunalEncounter {
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, InfinityGauntlet.id("tribunal"));
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
	public static final int AUTO_DELAY = 60;
    private static final Map<UUID,Integer> ARRIVING = new HashMap<>();
    private static final java.util.Set<UUID> VISITED = new java.util.HashSet<>();
    private static final AttachmentType<Long> RETRY = AttachmentRegistry.<Long>builder().persistent(Codec.LONG).copyOnDeath()
            .buildAndRegister(InfinityGauntlet.id("tribunal_retry_at"));
    private static int boltId;
    private static final AttachmentType<Boolean> NOTIFIED = AttachmentRegistry.<Boolean>builder().persistent(Codec.BOOL).copyOnDeath()
			.buildAndRegister(InfinityGauntlet.id("tribunal_notified"));
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
		float healthCeiling;
        int flightAge;
        final java.util.List<Bolt> bolts = new java.util.ArrayList<>();
		Trial(ServerPlayer p, LivingTribunal boss) {
			player = p.getUUID();
			this.boss = boss;
			origin = p.position();
			yaw = p.getYRot();
			pitch = p.getXRot();
			health = p.getHealth();
			healthCeiling = health;
			flight = p.getAttached(CosmosTravel.FLIGHT);
		}
	}

    private static final class Bolt {
        int id, age; Vec3 position, velocity;
        Bolt(Vec3 p, Vec3 v) { id=++boltId; position=p; velocity=v; }
    }
    private static TribunalSpace.Point point(Vec3 p) { return new TribunalSpace.Point(p.x,p.y,p.z); }
    private static Vec3 vector(TribunalSpace.Point p) { return new Vec3(p.x(),p.y(),p.z()); }
    public static boolean eligible(ServerPlayer p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator() && CosmosTravel.inCosmos(p)
            && p.hasAttached(CosmosTravel.FLIGHT) && fullGauntlet(p) && GauntletEffects.state(p).phase==GauntletEffects.Phase.AWAKENED
            && ThanosEncounter.defeatedForever(p.level().getServer()) && StoneRegistry.released(p.level().getServer(),Stone.MIND);
    }
    private static void autoArrival(MinecraftServer server) {
        for(ServerPlayer p:server.getPlayerList().getPlayers()) {
            UUID id=p.getUUID();
            if(!CosmosTravel.inCosmos(p)) { ARRIVING.remove(id); VISITED.remove(id); continue; }
            Long retry=p.getAttached(RETRY);
            if(!eligible(p) || awaitingReturn(p) || retry!=null && server.overworld().getGameTime()<retry) { ARRIVING.remove(id); continue; }
            if(VISITED.contains(id) || TRIALS.containsKey(server)) continue;
            int age=ARRIVING.merge(id,1,Integer::sum);
            if(age==1 && !Boolean.TRUE.equals(p.getAttached(NOTIFIED))) {p.setAttached(NOTIFIED,true);p.sendSystemMessage(Component.translatable("tribunal.infinity_gauntlet.available"));}
            if(age>=AUTO_DELAY && start(p).equals("started")) { ARRIVING.remove(id); VISITED.add(id); }
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
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Trial trial = TRIALS.get(server);
			if (trial != null) close(server, trial);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> { TRIALS.remove(server); ARRIVING.clear(); VISITED.clear(); });
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Trial trial = TRIALS.get(server);
			ARRIVING.remove(handler.getPlayer().getUUID()); VISITED.remove(handler.getPlayer().getUUID());
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
			player.setAttached(RETURN, checkpoint);
			oldPlayer.removeAttached(RETURN);
			oldPlayer.removeAttached(CosmosTravel.FLIGHT);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> {
			Checkpoint checkpoint = player.getAttached(RETURN);
			if (!alive && checkpoint != null) restore(player, checkpoint);
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer p)) return true;
			Trial trial = trial(p);
			if (trial == null || trial.executing) return true;
			if (trial.verdictAge > 0) return false;
			if (source.is(DAMAGE) && p.getHealth() - amount <= 2) { beginVerdict(trial, p); return false; }
			return true;
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer p)) return true;
			Trial trial = trial(p);
			if (trial == null || trial.executing) return true;
			beginVerdict(trial, p);
			return false;
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

	public static String start(ServerPlayer p) {
		ServerLevel level = p.level();
		MinecraftServer server = level.getServer();
		if (!CosmosTravel.inCosmos(p) || !p.hasAttached(CosmosTravel.FLIGHT) || !p.isAlive() || p.isSpectator()
				|| p.isCreative() || level.getDifficulty() == Difficulty.PEACEFUL) return "space_only";
		if (awaitingReturn(p) || TRIALS.containsKey(server) || PlanetDestruction.isBusy(p) || com.dantemorrigan.gauntlet.power.TimeAbility.isRewinding(p)
				|| com.dantemorrigan.gauntlet.snap.InfinitySnap.casting(p)) return "busy";
		if (fullGauntlet(p) && GauntletEffects.state(p).phase != GauntletEffects.Phase.AWAKENED) return "awakening";
		if (!ThanosEncounter.defeatedForever(server) || !StoneRegistry.released(server, Stone.MIND) || !fullGauntlet(p)) return "locked";
		LivingTribunal boss = ModEntities.TRIBUNAL.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (boss == null) return "busy";
		Vec3 heading = p.getLookAngle().multiply(1, 0, 1).normalize();
		if (heading.horizontalDistanceSqr() < .1) heading = Vec3.directionFromRotation(0, p.getYRot());
		TribunalSpace.Point spawn = TribunalSpace.orbit(point(p.position()), Math.atan2(heading.z,heading.x));
        if(spawn==null) return "busy";
        Vec3 base = vector(spawn).add(0,-TribunalSpace.HEIGHT/2,0);
		// Load and retain the local scene so a giant on the view-distance edge cannot unload mid-intro.
        boss.keepActiveAt(base);
		level.getChunkAt(BlockPos.containing(base));
		float yaw = Loki.yawTo(base, p.position());
		boss.snapTo(base.x, base.y, base.z, yaw, 0);
		boss.setYBodyRot(yaw);
		boss.challenger(p.getId());
		if (!level.addFreshEntity(boss)) return "busy";
		TRIALS.put(server, new Trial(p, boss));
		VISITED.add(p.getUUID()); ARRIVING.remove(p.getUUID());
		AbilityController.cancel(p);
		CosmosTravel.cancelWarp(p);
		p.closeContainer();
		ModSounds.play(level, p.position(), ModSounds.TRIBUNAL_ARRIVAL, 3);
		level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 3, .5f);
		return "started";
	}

	private static boolean fullGauntlet(ServerPlayer player) {
		ItemStack stack = GauntletState.activeGauntlet(player);
		return !stack.isEmpty() && GauntletState.inserted(stack).size() == Stone.values().length;
	}

	private static void tick(MinecraftServer server) {
        autoArrival(server);
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
		if (!fullGauntlet(p) || p.distanceToSqr(boss) > 320 * 320) {
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
		move(trial,p);
        tickBolts(trial,p);
        if(trial.verdictAge>0) return;
		// Health is sealed by the Tribunal: food, potions and other healing cannot erase his previous hits.
		p.setHealth(Math.min(p.getHealth(), trial.healthCeiling));
		tickBattle(trial, p);
		trial.healthCeiling = Math.min(trial.healthCeiling, p.getHealth());
	}


    private static void move(Trial t, ServerPlayer p) {
        double angle=++t.flightAge*.008;
        var desired=TribunalSpace.orbit(point(p.position()), angle);
        if(desired==null) return;
        Vec3 center=t.boss.position().add(0,TribunalSpace.HEIGHT/2,0);
        Vec3 next=vector(TribunalSpace.step(point(center),desired,1.35));
        Vec3 feet=next.add(0,-TribunalSpace.HEIGHT/2,0);
        t.boss.keepActiveAt(feet);t.boss.setPos(feet);
        float yaw=Loki.yawTo(t.boss.position(),p.position());
        t.boss.setYRot(yaw); t.boss.setYBodyRot(yaw); t.boss.setYHeadRot(yaw);
    }
    private static void boltPacket(Trial t, Bolt b, int ticks) {
        var packet=new com.dantemorrigan.gauntlet.net.ModNetwork.TribunalBolt(b.id,b.position,b.position.add(b.velocity.scale(ticks)),ticks);
        for(ServerPlayer witness:t.boss.level().getServer().getPlayerList().getPlayers())
            if(witness.level()==t.boss.level() && witness.distanceToSqr(t.boss)<400*400)
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(witness,packet);
    }
    private static void shoot(Trial t, ServerPlayer p) {
        Vec3 from=t.boss.position().add(0,TribunalSpace.HEIGHT*.67,0);
        Vec3 aim=p.getEyePosition().add(p.getDeltaMovement().scale(10));
        Bolt b=new Bolt(from,aim.subtract(from).normalize().scale(3.6));t.bolts.add(b);boltPacket(t,b,65);
    }
    private static void tickBolts(Trial t, ServerPlayer p) {
        var it=t.bolts.iterator();
        while(it.hasNext()) {
            Bolt b=it.next(); Vec3 next=b.position.add(b.velocity);
            boolean impact=p.getBoundingBox().inflate(2.4).clip(b.position,next).isPresent() || p.getBoundingBox().inflate(2.4).contains(b.position);
            if(impact || ++b.age>65 || !TribunalSpace.clear(point(b.position),point(next),1)) {
                boltPacket(t,b,0);it.remove();
                if(impact) { hit(t,p,p.getMaxHealth()*.18f); if(t.verdictAge>0) return; }
            } else b.position=next;
        }
    }
    private static final int[] PATTERN={LivingTribunal.EDICT,LivingTribunal.VOLLEY,LivingTribunal.GRAVITY,
        LivingTribunal.LANCE,LivingTribunal.WAVE,LivingTribunal.PRISON,LivingTribunal.NOVA};
    private static void tickBattle(Trial t, ServerPlayer p) {
        LivingTribunal boss=t.boss;ServerLevel level=p.level();
        if(boss.attack()==0) {
            if(--boss.nextAttack>0) return;
            int kind=PATTERN[boss.attackCycle++%PATTERN.length];
            boss.attack(kind,p.position());boss.age(0);
            level.playSound(null,p.blockPosition(),SoundEvents.WARDEN_SONIC_CHARGE,SoundSource.HOSTILE,2.4f,.72f);
            return;
        }
        int age=boss.sceneAge()+1;boss.age(age);int kind=boss.attack();
        Vec3 delta=boss.mark().subtract(p.position());double distance=delta.length();
        if(kind==LivingTribunal.LANCE && age<24) boss.attack(kind,p.position());
        if(kind==LivingTribunal.VOLLEY && age>=20 && age<=60 && age%10==0) shoot(t,p);
        if(kind==LivingTribunal.GRAVITY && age>10 && age<LivingTribunal.WINDUP && distance<36) {
            p.setDeltaMovement(p.getDeltaMovement().scale(.65).add(delta.normalize().scale(.57)));p.hurtMarked=true;
        }
        if(age==LivingTribunal.WINDUP && kind!=LivingTribunal.VOLLEY && kind!=LivingTribunal.WAVE && kind!=LivingTribunal.PRISON) {
            double radius=kind==LivingTribunal.NOVA?30:kind==LivingTribunal.GRAVITY?18:kind==LivingTribunal.LANCE?5:10;
            if(distance<=radius) hit(t,p,p.getMaxHealth()*(kind==LivingTribunal.NOVA?.65f:.32f));
            Vec3 at=boss.mark();level.sendParticles(GOLD,at.x,at.y,at.z,80,radius*.3,2,radius*.3,.08);
            level.playSound(null,p.blockPosition(),SoundEvents.WARDEN_SONIC_BOOM,SoundSource.HOSTILE,2,.55f);
        }
        if(kind==LivingTribunal.WAVE && age>=34 && age<=74 && Math.abs(distance-(age-34)*1.6)<2.8 && age%6==0) hit(t,p,p.getMaxHealth()*.25f);
        if(kind==LivingTribunal.PRISON && age>=34 && age<=94 && age%15==0 && distance>14 && distance<29) hit(t,p,p.getMaxHealth()*.18f);
        int end=kind==LivingTribunal.PRISON?100:kind==LivingTribunal.WAVE?80:kind==LivingTribunal.VOLLEY?70:50;
        if(t.verdictAge==0 && age>=end) { boss.attack(0,Vec3.ZERO);boss.age(0);boss.exposedUntil=level.getGameTime()+32;boss.nextAttack=26; }
    }

	private static void hit(Trial trial, ServerPlayer p, float damage) {
		if (p.getHealth() - damage <= 2) {
			beginVerdict(trial, p);
			return;
		}
		p.hurtServer(p.level(), new DamageSource(p.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE), trial.boss), damage);
		if (p.isAlive() && p.getHealth() <= 2) beginVerdict(trial, p);
	}

	private static void beginVerdict(Trial trial, ServerPlayer p) {
		if (trial.verdictAge > 0) return;
		ItemStack gauntlet = GauntletState.activeGauntlet(p);
		if (gauntlet.isEmpty()) return;
		AbilityController.cancel(p);
		CosmosTravel.cancelWarp(p);
		p.closeContainer();
		for(Bolt b:trial.bolts) boltPacket(trial,b,0); trial.bolts.clear();
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
		p.setHealth(2);
		p.closeContainer();
		if (age == 2 || age % 4 == 0) {
			Vec3 anchor = trial.verdictOrigin;
			Vec3 focus = trial.boss.position().add(0, TribunalSpace.HEIGHT*.65, 0);
			Vec3 look = trial.boss.mark().lerp(focus, Mth.clamp((age - 5) / 65.0, .1, 1)).subtract(anchor.add(0, p.getEyeHeight(), 0));
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
		GauntletEffects.restoreAwakened(p, checkpoint.health);
		CosmosTravel.restoreFlight(p, checkpoint.flight);
		p.removeAttached(RETURN);
		p.setAttached(RETRY,p.level().getServer().overworld().getGameTime()+1200);
		p.inventoryMenu.broadcastChanges();
	}

	/** Movement packets cannot carry a challenger out of the verdict scene. */
	public static void enforce(ServerPlayer p) {
		Trial trial = trial(p);
		if (trial == null || trial.verdictAge == 0 || trial.executing) return;
		Vec3 at = trial.verdictOrigin;
		if (p.position().distanceToSqr(at) > .01) p.connection.teleport(at.x, at.y, at.z, p.getYRot(), p.getXRot());
		p.setDeltaMovement(Vec3.ZERO);
	}

	static void defeated(LivingTribunal boss) {
		if (!(boss.level() instanceof ServerLevel level)) return;
		Trial trial = TRIALS.get(level.getServer());
		if (trial == null || trial.boss != boss) return;
		for(Bolt bolt:trial.bolts) boltPacket(trial,bolt,0);trial.bolts.clear();
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
		for(Bolt b:trial.bolts) boltPacket(trial,b,0); trial.bolts.clear();
		trial.boss.discard();
	}

	private TribunalEncounter() {}
}
