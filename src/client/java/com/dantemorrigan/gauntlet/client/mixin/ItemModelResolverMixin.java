package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.snap.InfinitySnap;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @ModifyVariable(method = "updateForTopItem", at = @At("HEAD"), argsOnly = true)
    private ItemStack infinityGauntlet$pulseDamagedTimeStone(ItemStack stack) {
        if ((!stack.is(ModItems.TIME_STONE) && !stack.is(ModItems.GAUNTLET)) || !InfinitySnap.snapped(stack)) return stack;
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (stack.is(ModItems.GAUNTLET)
                && (data == null || !Boolean.TRUE.equals(data.getBoolean(Stone.TIME.ordinal())))) return stack;
        if (data == null) data = new CustomModelData(List.of(), List.of(), List.of(), List.of());

        double pulse = (1 - Math.cos((Util.getMillis() % 2200L) * (2 * Math.PI / 2200))) / 2;
        int dim = 150 + (int) (pulse * 105);
        int green = 180 + (int) (pulse * 75);
        ItemStack visual = stack.copy();
        visual.set(DataComponents.CUSTOM_MODEL_DATA,
            new CustomModelData(data.floats(), data.flags(), data.strings(), List.of(ARGB.color(255, dim, green, dim))));
        return visual;
    }
}
