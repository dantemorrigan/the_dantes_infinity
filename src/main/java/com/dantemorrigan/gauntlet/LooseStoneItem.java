package com.dantemorrigan.gauntlet;

import com.dantemorrigan.gauntlet.power.MindStone;
import com.dantemorrigan.gauntlet.power.SpaceStone;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

final class LooseStoneItem extends Item {
	private final Stone stone;

	LooseStoneItem(Properties properties, Stone stone) {
		super(properties);
		this.stone = stone;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			if (!activate(serverPlayer)) return InteractionResult.FAIL;
			player.getCooldowns().addCooldown(player.getItemInHand(hand), 100);
		}
		return InteractionResult.SUCCESS;
	}

	private boolean activate(ServerPlayer player) {
		if (stone == Stone.SPACE) return SpaceStone.blinkLoose(player);
		LivingEntity target = MindStone.lookTarget(player, 8);
		if (target == null || !player.hasLineOfSight(target)) return false;

		return switch (stone) {
			case MIND -> {
				target.setDeltaMovement(player.position().subtract(target.position()).normalize().scale(0.8).add(0, 0.2, 0));
				target.hurtMarked = true;
				yield true;
			}
			case REALITY -> target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40), player);
			case SOUL -> {
				float health = target.getHealth();
				if (!target.hurtServer(player.level(), player.damageSources().magic(), 4)) yield false;
				player.heal(Math.min(4, health - target.getHealth()));
				yield true;
			}
			default -> false;
		};
	}
}
