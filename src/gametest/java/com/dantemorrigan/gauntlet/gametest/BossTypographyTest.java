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
            // ---- Regression over every line of every boss in both languages: a highlighted name is always a whole word,
            // Doom is only highlighted as a speaker, and nothing about Doombots, the gate or the castle is set in a boss font.
            for(String language:new String[]{"en","ru"}){
                ctx.runOnClient(mc->{
                    ModLang.set(language);
                    var gson=new com.google.gson.Gson();
                    java.util.Map<String,String> lang;
                    try(var in=BossTypographyTest.class.getResourceAsStream("/assets/infinity_gauntlet/lang/"+(language.equals("ru")?"ru_ru":"en_us")+".json");
                        var reader=new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)){
                        lang=gson.<java.util.Map<String,String>>fromJson(reader,new com.google.gson.reflect.TypeToken<java.util.Map<String,String>>(){}.getType());
                    } catch(java.io.IOException e){throw new AssertionError(e);}
                    int themed=0,plain=0;
                    for(String key:lang.keySet()){
                        int args=0;for(int i=0;i<lang.get(key).length()-1;i++)if(lang.get(key).charAt(i)=='%' && lang.get(key).charAt(i+1)=='s')args++;
                        Object[] values=new Object[args];java.util.Arrays.fill(values,Component.translatable("entity.infinity_gauntlet.doombot.soldier"));
                        var source=Component.translatable(key,values);var kind=BossText.kind(source);var rich=BossText.chat(source);
                        String whole=rich.getString();
                        check(whole.equals(source.getString()),"Styling changed the text of "+key);
                        java.util.List<int[]> names=new java.util.ArrayList<>();int[] at={0};
                        rich.visit((Style style,String value)->{
                            if(style.getFont().equals(BossText.NAME) && !value.isBlank())names.add(new int[]{at[0],value.length()});
                            at[0]+=value.length();return Optional.empty();},Style.EMPTY);
                        for(int[] n:names){
                            boolean before=n[0]==0 || !Character.isLetterOrDigit(whole.charAt(n[0]-1));
                            boolean after=n[0]+n[1]>=whole.length() || !Character.isLetterOrDigit(whole.charAt(n[0]+n[1]));
                            check(before && after,"A boss name is highlighted inside a word in "+key+" ("+language+"): "+whole);
                            if(kind==BossText.Kind.SOVEREIGN)check(n[0]==0 && whole.startsWith(whole.substring(0,n[1])+":"),"Doom is highlighted outside the speaker position in "+key+": "+whole);
                        }
                        if(key.startsWith("msg.infinity_gauntlet.latveria.") && !key.startsWith("msg.infinity_gauntlet.latveria.doom"))
                            check(kind==null && !font(rich,BossText.NAME) && !font(rich,BossText.ACTION),"A Latveria notice is set in a boss font: "+key);
                        if(kind!=null)themed++;else plain++;
                    }
                    check(themed>20 && plain>200,"The language file scan looks wrong: themed "+themed+", plain "+plain);
                    var guard=BossText.chat(Component.translatable("msg.infinity_gauntlet.latveria.bot.guard",Component.translatable("entity.infinity_gauntlet.doombot.bastion")));
                    check(!font(guard,BossText.NAME) && !font(guard,BossText.ACTION),"A Doombot's name is set in a boss font");
                    var speech=BossText.chat(Component.translatable("msg.infinity_gauntlet.latveria.doom.intro_1"));
                    check(font(speech,BossText.NAME) && font(speech,BossText.ACTION),"Doom's speech lost its type");
                    var inline=BossText.chat(Component.translatable("msg.infinity_gauntlet.latveria.conquered"));
                    check(!font(inline,BossText.NAME),"'Doom' is highlighted in a notice that is not his speech");
                });
            }
            System.out.println("BOSS TYPOGRAPHY: custom RU/EN glyphs, name/action weights, source preservation, ordinary chat and live chat/HUD passed");
        }
    }
}
