package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.client.lang.ModLang;
import com.dantemorrigan.gauntlet.text.BossText;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.EntitySpawnReason;
import org.lwjgl.glfw.GLFW;
import java.util.Optional;

public final class BossTypographyTest implements FabricClientGameTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static boolean font(Component text,FontDescription expected){
        boolean[] found={false};text.visit((Style style,String value)->{if(!value.isBlank() && style.getFont().equals(expected))found[0]=true;return Optional.empty();},Style.EMPTY);return found[0];
    }
    @Override public void runTest(ClientGameTestContext ctx){
        try(var sp=ctx.worldBuilder().create()){
            sp.getClientLevel().waitForChunksRender();var server=sp.getServer();server.runCommand("gamemode creative @p");
            server.runCommand("fill -12 99 -12 12 99 12 minecraft:stone");server.runCommand("tp @p 0.5 100 0.5 0 0");
            server.runOnServer(s->{var loki=ModEntities.LOKI.create(s.overworld(),EntitySpawnReason.MOB_SUMMONED);loki.setNoAi(true);loki.snapTo(.5,100,8.5,180,0);s.overworld().addFreshEntity(loki);});ctx.waitTicks(10);
            for(String language:new String[]{"ru","en"}){
                ctx.runOnClient(mc->{ModLang.set(language);mc.getToastManager().clear();mc.gui.getChat().clearMessages(true);
                    var source=Component.translatable("msg.infinity_gauntlet.loki.defeated");var rich=BossText.chat(source);
                    check(font(rich,BossText.NAME) && font(rich,BossText.ACTION),"Boss notice lacks distinct name/action fonts");
                    check(rich.getString().equals(source.getString()),"Styling changed the translated content");
                    check(source.getStyle().getFont().equals(FontDescription.DEFAULT),"Formatting mutated source component");
                    var normal=Component.literal("Ordinary chat / Обычный чат").withStyle(s->s.withInsertion("keep-me"));
                    check(BossText.chat(normal).getStyle().equals(normal.getStyle()),"Normal chat style changed");
                    check(mc.font.width(BossText.name(Component.literal("LOKI / ТАНОС")))!=mc.font.width(Component.literal("LOKI / ТАНОС")),"Custom name font fell back to vanilla");
                    check(mc.font.width(BossText.action(Component.literal("Gravity / Гравитация")))>0,"Action font glyphs missing");
                });
                server.runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();
                    p.sendSystemMessage(Component.translatable("entity.infinity_gauntlet.thanos.title"));
                    p.sendSystemMessage(Component.translatable("msg.infinity_gauntlet.thanos.warning"));
                    p.sendSystemMessage(Component.translatable("msg.infinity_gauntlet.loki.defeated"));
                    p.sendSystemMessage(Component.translatable("tribunal.infinity_gauntlet.available"));
                    p.sendSystemMessage(Component.translatable("tribunal.infinity_gauntlet.attack.2"));
                    p.sendSystemMessage(Component.literal("Ordinary chat / Обычный чат: ABC abc 0123"));
                });
                ctx.waitTicks(8);ctx.getInput().pressKey(GLFW.GLFW_KEY_T);ctx.waitTicks(3);ctx.takeScreenshot("boss_typography_chat_"+language);
                ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);ctx.waitTicks(2);
            }
            System.out.println("BOSS TYPOGRAPHY: custom RU/EN glyphs, name/action weights, source preservation, ordinary chat and live chat/HUD passed");
        }
    }
}
