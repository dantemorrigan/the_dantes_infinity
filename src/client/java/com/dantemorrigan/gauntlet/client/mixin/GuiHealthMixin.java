package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.client.CompactHealth;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Gui;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The awakened Infinity Gauntlet gives 200 health: all hundred hearts are drawn. Only a pool beyond CompactHealth.ROWS_HP
 * is scaled down to the share of health, with the exact numbers beside the hearts (see CompactHealth).
 */
@Mixin(Gui.class)
public abstract class GuiHealthMixin {
	@WrapOperation(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getHealth()F"), require = 0)
	private float infinityGauntlet$health(Player player, Operation<Float> original) {
		return original.call(player) * CompactHealth.scale(player);
	}

	@WrapOperation(method = "extractPlayerHealth", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;getAttributeValue(Lnet/minecraft/core/Holder;)D"), require = 0)
	private double infinityGauntlet$maxHealth(Player player, Holder<Attribute> attribute, Operation<Double> original) {
		double value = original.call(player, attribute);
		return attribute == Attributes.MAX_HEALTH ? value * CompactHealth.scale(player) : value;
	}

	@WrapOperation(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getAbsorptionAmount()F"), require = 0)
	private float infinityGauntlet$absorption(Player player, Operation<Float> original) {
		return original.call(player) * CompactHealth.scale(player);
	}
}
