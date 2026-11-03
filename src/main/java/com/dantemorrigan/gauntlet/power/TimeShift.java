package com.dantemorrigan.gauntlet.power;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.celestial.CelestialApocalypse;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.net.ModStatus;
import com.dantemorrigan.gauntlet.space.SpaceManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Time stone: day/night switch. The overworld clock is driven forward along a smooth ease-in-out curve, so the
 * sun races over the sky and the moon rises (or the other way round) within a few seconds.
 */
public final class TimeShift {
	private static final long DAY = 24000L, NIGHT_TARGET = 18000L, DAY_TARGET = 2000L;
	/** Time of day that counts as "day" (sunrise .. just before dusk). */
	private static final long DAY_END = 12500L, DAY_START = 23000L;

	private static boolean active;
	private static long distance;
	private static int duration, tick;
	private static long applied;
	private static UUID caster;
	private static net.minecraft.core.BlockPos pivot = net.minecraft.core.BlockPos.ZERO;
	private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(TimeShift::tick);
	}

	public static void request(ServerPlayer player) {
		GauntletConfig cfg = GauntletConfig.get();
		ItemStack gauntlet = GauntletState.activeGauntlet(player);
		if (gauntlet.isEmpty() || !GauntletState.inserted(gauntlet).contains(Stone.TIME)) {
			ModStatus.send(player, Component.literal("Нужна перчатка с Камнем Времени в левой руке"));
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (player.level().dimension() != Level.OVERWORLD || SpaceManager.inSpace(player)) {
			ModStatus.send(player, Component.literal("§2Смена времени суток работает только в обычном мире"));
			return;
		}
		if (active || TimeAbility.isRewinding(player)) return;
		long now = server.overworld().getGameTime();
		Long until = COOLDOWN.get(player.getUUID());
		if (until != null && now < until && !player.isCreative()) {
			ModStatus.send(player, Component.literal("§2Камень Времени: " + (until - now) / 20 + " с"));
			return;
		}
		var clock = server.registryAccess().get(net.minecraft.world.clock.WorldClocks.OVERWORLD);
		if (clock.isEmpty()) return;
		long tod = Math.floorMod(server.clockManager().getTotalTicks(clock.get()), DAY);
		boolean toNight = tod < DAY_END || tod >= DAY_START;
		if (!toNight && CelestialApocalypse.isDestroyed(server, "sun")) {
			ModStatus.send(player, Component.literal("§2Солнца больше нет — день не наступит"));
			return;
		}
		long target = toNight ? NIGHT_TARGET : DAY_TARGET;
		distance = Math.floorMod(target - tod, DAY);
		if (distance < 3000) distance += DAY;
		duration = cfg.timeShiftTicks;
		tick = 0;
		applied = 0;
		active = true;
		caster = player.getUUID();
		pivot = player.blockPosition();
		COOLDOWN.put(player.getUUID(), now + cfg.timeShiftCooldownTicks);
		GauntletState.setInUse(gauntlet, EnumSet.of(Stone.TIME));
		ModNetwork.TimeShiftFx fx = new ModNetwork.TimeShiftFx(duration, toNight);
		for (ServerPlayer p : server.overworld().players()) ServerPlayNetworking.send(p, fx);
		server.overworld().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0F, 0.5F);
	}

	private static void tick(MinecraftServer server) {
		if (!active) return;
		var clock = server.registryAccess().get(net.minecraft.world.clock.WorldClocks.OVERWORLD);
		if (clock.isEmpty()) {
			active = false;
			return;
		}
		tick++;
		float x = Math.min(1f, tick / (float) duration);
		float eased = x * x * x * (x * (x * 6 - 15) + 10); // smootherstep: slow start, fast middle, slow end
		long want = tick >= duration ? distance : (long) (distance * eased);
		long add = want - applied;
		if (add > 0) server.clockManager().addTicks(clock.get(), (int) add);
		applied = want;
		if (tick % 8 == 0) server.overworld().playSound(null, pivot, SoundEvents.AMETHYST_BLOCK_RESONATE,
				SoundSource.AMBIENT, 0.4F, 0.5F + eased);
		if (tick >= duration) {
			active = false;
			ServerPlayer p = caster == null ? null : server.getPlayerList().getPlayer(caster);
			if (p != null) {
				ItemStack g = GauntletState.activeGauntlet(p);
				if (!g.isEmpty()) GauntletState.setInUse(g, EnumSet.noneOf(Stone.class));
				p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2F, 1.6F);
			}
		}
	}

	private TimeShift() {}
    public static int cooldown(ServerPlayer p) { return (int)Math.max(0, COOLDOWN.getOrDefault(p.getUUID(),0L)-p.level().getGameTime()); }

}
