package com.dantemorrigan.gauntlet;

import com.dantemorrigan.gauntlet.power.MindStone;
import com.dantemorrigan.gauntlet.power.SpaceStone;
import com.dantemorrigan.gauntlet.power.SoulStone;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class LooseStoneItem extends Item {
	private static final Map<UUID, Hold> HOLDS = new HashMap<>();
	private static final Map<LivingEntity, Long> SLAMS = new HashMap<>();

	private record Hold(LivingEntity target, InteractionHand hand, Vec3 anchor, long until) {}

	static {
		ServerTickEvents.END_SERVER_TICK.register(LooseStoneItem::tick);
	}

	private final Stone stone;

	LooseStoneItem(Properties properties, Stone stone) {
		super(properties);
		this.stone = stone;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			if (!activate(serverPlayer, hand)) return InteractionResult.FAIL;
		}
		return InteractionResult.SUCCESS;
	}

	private boolean activate(ServerPlayer player, InteractionHand hand) {
		if (stone == Stone.SPACE) return SpaceStone.blinkLoose(player);
		if (stone == Stone.SOUL) {
			if (!SoulStone.extractLoose(player)) return false;
			player.getCooldowns().addCooldown(player.getItemInHand(hand), 400);
			return true;
		}

		if (stone == Stone.MIND) {
			Hold hold = HOLDS.get(player.getUUID());
			if (hold != null && hold.hand() == hand && validHold(player, hold)) {
				HOLDS.remove(player.getUUID());
				launch(hold.target(), player.getLookAngle().scale(2).add(0, 0.2, 0));
				player.getCooldowns().addCooldown(player.getItemInHand(hand), 100);
				return true;
			}
			if (hold != null && validHold(player, hold)) return false;
		}

		LivingEntity target = MindStone.lookTarget(player, 8);
		if (target == null || !player.hasLineOfSight(target) || SLAMS.containsKey(target)
				|| HOLDS.values().stream().anyMatch(hold -> hold.target() == target)) return false;
		long now = player.level().getGameTime();
		if (stone == Stone.REALITY) {
			launch(target, new Vec3(0, 1.7, 0));
			SLAMS.put(target, now + 19);
			player.getCooldowns().addCooldown(player.getItemInHand(hand), 100);
			return true;
		}
		if (stone == Stone.MIND) {
			HOLDS.put(player.getUUID(), new Hold(target, hand, target.position().add(0, 2, 0), now + 80));
			return true;
		}
		return false;
	}

	private static boolean validHold(ServerPlayer player, Hold hold) {
		LivingEntity target = hold.target();
		return player != null && player.isAlive() && target.isAlive() && !target.isRemoved()
				&& target.level() == player.level() && target.distanceToSqr(player) <= 144
				&& Stone.MIND.matches(player.getItemInHand(hold.hand()))
				&& player.level().getGameTime() < hold.until();
	}

	private static void tick(MinecraftServer server) {
		HOLDS.entrySet().removeIf(entry -> {
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			Hold hold = entry.getValue();
			if (!validHold(owner, hold)) return true;
			launch(hold.target(), hold.anchor().subtract(hold.target().position()).scale(0.45));
			return false;
		});
		SLAMS.entrySet().removeIf(entry -> {
			LivingEntity target = entry.getKey();
			if (!target.isAlive() || target.isRemoved()) return true;
			if (target.level().getGameTime() < entry.getValue()) return false;
			launch(target, new Vec3(0, -2.8, 0));
			return true;
		});
	}

	private static void launch(LivingEntity target, Vec3 velocity) {
		target.setDeltaMovement(velocity);
		target.hurtMarked = true;
	}
}
