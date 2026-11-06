package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.client.boss.BossBarArt;
import com.dantemorrigan.gauntlet.client.boss.ThanosClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

@Mixin(BossHealthOverlay.class)
abstract class BossHealthOverlayMixin {
	@Shadow @Final private Minecraft minecraft;
	@Shadow @Final private Map<UUID, LerpingBossEvent> events;
	@Shadow private void extractBar(GuiGraphicsExtractor graphics, int x, int y, BossEvent event) { throw new AssertionError(); }

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void infinityGauntlet$drawLokiBars(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (events.values().stream().noneMatch(event -> infinityGauntlet$art(event) != null)) return;
		ci.cancel();
		graphics.nextStratum();
		int center = graphics.guiWidth() / 2;
		int y = ThanosClient.hasBossBar() ? 59 : 4;
		for (LerpingBossEvent event : events.values()) {
			BossBarArt art = infinityGauntlet$art(event);
			if (art == null) {
				extractBar(graphics, center - 91, y + 9, event);
				graphics.centeredText(minecraft.font, event.getName(), center, y, 0xffffffff);
				y += 19;
			} else {
				graphics.centeredText(minecraft.font, event.getName(), center, y, art == BossBarArt.FENRIR ? 0xffc8ffdb : 0xffffe5a5);
				art.draw(graphics, y + 7, event.getProgress(), event.getProgress(), 255);
				y += BossBarArt.ROW_HEIGHT;
			}
			if (y >= graphics.guiHeight() / 3) break;
		}
	}

	private static BossBarArt infinityGauntlet$art(BossEvent event) {
		if (!(event.getName().getContents() instanceof TranslatableContents title)) return null;
		return switch (title.getKey()) {
			case "entity.infinity_gauntlet.loki" -> BossBarArt.LOKI;
			case "boss.infinity_gauntlet.loki.rage" -> BossBarArt.FENRIR;
			default -> null;
		};
	}
}
