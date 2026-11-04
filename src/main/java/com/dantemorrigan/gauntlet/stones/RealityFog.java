package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.net.ModStatus;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reality Stone: in the Nether a player may be wrapped in red mist, with the Reality Stone darting through it.
 * A melee hit on the fleeing stone catches it; an arrow scatters the mist and drops the stone at the player's feet.
 * If nobody catches it in time, it slips away (the mist can come back later). One stone per world.
 */
public final class RealityFog {
	private record Event(UUID player, RealityWisp wisp, long end) {}

	private static Event event;

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(RealityFog::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> event = null);
	}

	public static boolean active() { return event != null; }
	public static RealityWisp wisp() { return event == null ? null : event.wisp(); }

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (event != null) {
			ServerPlayer p = server.getPlayerList().getPlayer(event.player());
			if (p == null || event.wisp().isRemoved() || p.level() != event.wisp().level() || now >= event.end()) {
				if (p != null && !event.wisp().isRemoved()) ModStatus.send(p, Component.translatable("msg.infinity_gauntlet.reality.escaped"));
				end(server, p, false);
			}
			return;
		}
		GauntletConfig cfg = GauntletConfig.get();
		if (now % 1200 != 0 || !cfg.realityFogEnabled || StoneRegistry.found(server, Stone.REALITY)) return;
		ServerLevel nether = server.getLevel(Level.NETHER);
		if (nether == null) return;
		for (ServerPlayer p : nether.players()) {
			if (p.isAlive() && !p.isSpectator() && nether.getRandom().nextFloat() < cfg.realityFogChancePerMinute) {
				start(p);
				return;
			}
		}
	}

	/** Wraps the player in the mist and releases the stone near them. */
	public static boolean start(ServerPlayer p) {
		if (event != null) return false;
		ServerLevel level = (ServerLevel) p.level();
		RealityWisp wisp = ModEntities.REALITY_WISP.create(level, EntitySpawnReason.EVENT);
		if (wisp == null) return false;
		Vec3 at = p.getEyePosition().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(7)).add(0, 1, 0);
		wisp.setPos(at);
		wisp.setOwner(p.getUUID());
		level.addFreshEntity(wisp);
		event = new Event(p.getUUID(), wisp, level.getGameTime() + GauntletConfig.get().realityFogSeconds * 20L);
		ServerPlayNetworking.send(p, new ModNetwork.RealityFogFx(true));
		level.playSound(null, p.blockPosition(), SoundEvents.AMBIENT_CRIMSON_FOREST_MOOD.value(), SoundSource.AMBIENT, 2.0F, 0.6F);
		level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 1.5F, 0.5F);
		ModStatus.send(p, Component.translatable("msg.infinity_gauntlet.reality.fog"));
		return true;
	}

	/** Melee: the stone is caught straight into the backpack. */
	static void caught(RealityWisp wisp, ServerPlayer p) {
		if (event == null || event.wisp() != wisp) { wisp.discard(); return; }
		ServerLevel level = (ServerLevel) wisp.level();
		level.sendParticles(ModParticles.QUEST_REALITY_ARROW_HIT, wisp.getX(), wisp.getY(), wisp.getZ(), 1, 0, 0, 0, 0);
		level.playSound(null, wisp.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 2.0F, 0.6F);
		if (StoneRegistry.markReleased(level.getServer(), Stone.REALITY)) {
			OrbItem.giveIntoBackpack(p, new ItemStack(ModItems.REALITY_STONE));
			ModStatus.send(p, Component.translatable("msg.infinity_gauntlet.reality.caught"));
		}
		end(level.getServer(), level.getServer().getPlayerList().getPlayer(event.player()), true);
	}

	/** Arrow: the mist scatters and the stone falls next to the player. */
	static void shot(RealityWisp wisp) {
		if (event == null || event.wisp() != wisp) { wisp.discard(); return; }
		ServerLevel level = (ServerLevel) wisp.level();
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(event.player());
		level.sendParticles(ModParticles.QUEST_REALITY_ARROW_HIT, wisp.getX(), wisp.getY(), wisp.getZ(), 1, 0, 0, 0, 0);
		level.playSound(null, wisp.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.0F, 0.7F);
		if (p != null && StoneRegistry.markReleased(level.getServer(), Stone.REALITY)) {
			Vec3 drop = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5)).add(0, 2.5, 0);
			ItemEntity stone = new ItemEntity(level, drop.x, drop.y, drop.z, new ItemStack(ModItems.REALITY_STONE), 0, -0.1, 0);
			stone.setGlowingTag(true);
			level.addFreshEntity(stone);
			ModStatus.send(p, Component.translatable("msg.infinity_gauntlet.reality.shot"));
		}
		end(level.getServer(), p, true);
	}

	private static void end(MinecraftServer server, ServerPlayer p, boolean dispel) {
		if (event == null) return;
		if (!event.wisp().isRemoved()) event.wisp().discard();
		event = null;
		if (p != null) {
			ServerPlayNetworking.send(p, new ModNetwork.RealityFogFx(false));
			if (dispel) {
				((ServerLevel) p.level()).sendParticles(ModParticles.QUEST_REALITY_FOG_DISPEL, p.getX(), p.getY() + 1.2, p.getZ(), 1, 0, 0, 0, 0);
				p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.AMBIENT, 1.5F, 0.6F);
			}
		}
	}

	private RealityFog() {}
}
