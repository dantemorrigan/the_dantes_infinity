package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.text.BossText;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
abstract class BossChatMixin {
    @ModifyVariable(method={"addServerSystemMessage","addClientSystemMessage"},at=@At("HEAD"),argsOnly=true)
    private Component infinityGauntlet$bossTypography(Component text){return BossText.chat(text);}
}
