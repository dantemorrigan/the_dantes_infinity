package com.dantemorrigan.gauntlet.mixin;

import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.stormbreaker.Stormbreaker;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(CraftingMenu.class)
abstract class StormbreakerCraftingMixin {
    @ModifyExpressionValue(method = "slotChangedCraftingGrid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/CraftingRecipe;assemble(Lnet/minecraft/world/item/crafting/RecipeInput;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack infinityGauntlet$preview(ItemStack result, AbstractContainerMenu menu, ServerLevel level, Player player) {
        return Stormbreaker.craftingPreview(level, result);
    }
}

@Mixin(ResultSlot.class)
abstract class StormbreakerResultMixin {
    @Shadow @Final private Player player;

    // Unlike Item#onCraftedBy, this call is only reached after the result has been removed or moved successfully.
    @Inject(method = "checkTakeAchievements", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;onCraftedBy(Lnet/minecraft/world/entity/player/Player;I)V"))
    private void infinityGauntlet$bindOnTake(ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayer p) Stormbreaker.finishCraft(p, stack);
    }
}

@Mixin(AbstractContainerMenu.class)
abstract class StormbreakerCraftClickMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void infinityGauntlet$rejectStaleResult(int slot, int button, ContainerInput action, Player player, CallbackInfo ci) {
        if (slot != CraftingMenu.RESULT_SLOT || !(player instanceof ServerPlayer p)
                || !((Object) this instanceof AbstractCraftingMenu menu)
                || !menu.getResultSlot().getItem().is(ModItems.STORMBREAKER)) return;
        if (Stormbreaker.exists(p.level().getServer())) {
            menu.getResultSlot().set(ItemStack.EMPTY);
            menu.broadcastFullState();
            ci.cancel();
        } else if (action == ContainerInput.CLONE) ci.cancel();
    }
}

@Mixin(CrafterBlock.class)
abstract class StormbreakerCrafterMixin {
    @Inject(method = "getPotentialResults", at = @At("RETURN"), cancellable = true)
    private static void infinityGauntlet$requiresWielder(ServerLevel level, CraftingInput input,
            CallbackInfoReturnable<Optional<RecipeHolder<CraftingRecipe>>> cir) {
        if (cir.getReturnValue().filter(recipe -> recipe.id().identifier().equals(
                com.dantemorrigan.gauntlet.InfinityGauntlet.id("stormbreaker"))).isPresent()) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
