package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.net.ModStatus;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A bare Power Stone held in a hand (main or off) tears its holder apart within {@link GauntletConfig#powerStoneHoldTicks}.
 * Carrying it in the backpack or in the gauntlet is safe. Creative and spectator players are spared.
 */
public final class PowerStoneCurse {
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, InfinityGauntlet.id("power_stone"));
	private static final Map<UUID, Integer> HOLDING = new HashMap<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(PowerStoneCurse::tick);
	}

	public static boolean holding(ServerPlayer p) {
		return p.getMainHandItem().is(ModItems.POWER_STONE) || p.getOffhandItem().is(ModItems.POWER_STONE);
	}

	private static void tick(MinecraftServer server) {
		int limit = Math.max(1, GauntletConfig.get().powerStoneHoldTicks);
		HOLDING.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!p.isAlive() || p.isSpectator() || p.isCreative() || !holding(p)) {
				HOLDING.remove(p.getUUID());
				continue;
			}
			int t = HOLDING.merge(p.getUUID(), 1, Integer::sum);
			ServerLevel level = (ServerLevel) p.level();
			if (t == 1) {
				level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 1.6F);
				level.playSound(null, p.blockPosition(), com.dantemorrigan.gauntlet.ModSounds.STONE_OMINOUS, SoundSource.PLAYERS, 1.5F, 1.0F);
				ModStatus.send(p, Component.translatable("msg.infinity_gauntlet.power_curse"));
			}
			float k = t / (float) limit;
			level.sendParticles(new DustParticleOptions(Stone.POWER.color, 1.0F + k * 1.6F), p.getX(), p.getY() + 1.0, p.getZ(),
					4 + (int) (k * 14), 0.35 + k * 0.4, 0.6, 0.35 + k * 0.4, 0.04 + k * 0.1);
			if (t % 10 == 0) level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 1.6F + k);
			if (t >= limit) {
				HOLDING.remove(p.getUUID());
				level.sendParticles(new DustParticleOptions(Stone.POWER.color, 3.0F), p.getX(), p.getY() + 1.0, p.getZ(), 120, 0.8, 1.0, 0.8, 0.4);
				level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5F, 0.7F);
				DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE));
				p.hurtServer(level, source, Float.MAX_VALUE);
			}
		}
	}

	private PowerStoneCurse() {}
}
