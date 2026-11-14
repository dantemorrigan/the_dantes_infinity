package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.client.CompactHealth;
import net.minecraft.client.gui.Gui;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The awakened Infinity Gauntlet gives 200 health: all hundred hearts are drawn. Only a pool beyond CompactHealth.ROWS_HP
 * is scaled down to the share of health, with the exact numbers beside the hearts (see CompactHealth).
 */
@Mixin(Gui.class)
public abstract class GuiHealthMixin {
	@Redirect(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getHealth()F"))
	private float infinityGauntlet$health(Player player) {
		return player.getHealth() * CompactHealth.scale(player);
	}

	@Redirect(method = "extractPlayerHealth", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
	private double infinityGauntlet$maxHealth(Player player, Holder<Attribute> attribute) {
		double value = player.getAttributeValue(attribute);
		return attribute == Attributes.MAX_HEALTH ? value * CompactHealth.scale(player) : value;
	}

	@Redirect(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getAbsorptionAmount()F"))
	private float infinityGauntlet$absorption(Player player) {
		return player.getAbsorptionAmount() * CompactHealth.scale(player);
	}
}
