package com.dantemorrigan.gauntlet.text;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import java.util.ArrayList;
import java.util.List;

/** Scoped boss typography. Physical font weights avoid Minecraft's doubled bold/italic glyphs. */
public final class BossText {
    public static final FontDescription NAME=new FontDescription.Resource(InfinityGauntlet.id("boss_name"));
    public static final FontDescription ACTION=new FontDescription.Resource(InfinityGauntlet.id("boss_action"));
    public enum Kind {
        TITAN(0xefc76c,0xeadcf5,"entity.infinity_gauntlet.thanos"),
        TRICKSTER(0x94e5b1,0xd8efdc,"entity.infinity_gauntlet.loki"),
        COSMIC(0xf7d98f,0xe5e2fa,"entity.infinity_gauntlet.living_tribunal"),
        SOVEREIGN(0x8ed9ca,0xdce7e3,"");
        final int accent,body;final String name;
        Kind(int accent,int body,String name){this.accent=accent;this.body=body;this.name=name;}
    }
    public static Kind kind(Component text) {
        if(!(text.getContents() instanceof TranslatableContents t))return null;
        String k=t.getKey();
        if(k.startsWith("tribunal.infinity_gauntlet.") || k.equals("entity.infinity_gauntlet.living_tribunal"))return Kind.COSMIC;
        if(k.startsWith("thanos.infinity_gauntlet.line.") || k.startsWith("msg.infinity_gauntlet.thanos.") || (k.equals("entity.infinity_gauntlet.thanos") || k.equals("entity.infinity_gauntlet.thanos.title")) || k.startsWith("boss.infinity_gauntlet.thanos.") || k.startsWith("command.infinity_gauntlet.thanos_"))return Kind.TITAN;
        if(k.equals("entity.infinity_gauntlet.loki") || k.startsWith("boss.infinity_gauntlet.loki.") || k.startsWith("msg.infinity_gauntlet.loki.") || k.startsWith("msg.infinity_gauntlet.mind_quest.") || k.startsWith("command.infinity_gauntlet.loki."))return Kind.TRICKSTER;
        // Only what Doom himself says is set in his type. Messages about Latveria, its gate or the Doombots are plain.
        if(k.equals("ability.infinity_gauntlet.latveria_sealed") || k.equals("msg.infinity_gauntlet.latveria.doom") || k.startsWith("msg.infinity_gauntlet.latveria.doom."))return Kind.SOVEREIGN;
        return null;
    }
    public static MutableComponent name(Component text) {
        return text.copy().withStyle(s->s.withFont(NAME).withBold(false).withItalic(false));
    }
    public static MutableComponent action(Component text) {
        return text.copy().withStyle(s->s.withFont(ACTION).withBold(false).withItalic(false));
    }
    public static Component notice(Component text) {return kind(text)==null?text:action(text);}
    /** Index of the first occurrence of {@code alias} as a whole word, or -1: "Doom" is not found inside "Doombot". */
    public static int word(String value,String alias) {
        if(alias.isEmpty())return -1;
        for(int i=value.indexOf(alias);i>=0;i=value.indexOf(alias,i+1)) {
            int end=i+alias.length();
            boolean before=i==0 || !Character.isLetterOrDigit(value.charAt(i-1));
            boolean after=end>=value.length() || !Character.isLetterOrDigit(value.charAt(end));
            if(before && after)return i;
        }
        return -1;
    }
    private static boolean title(String key){return key.startsWith("entity.") || key.startsWith("boss.");}
    /** Only called on system-chat entry points; player-authored messages never pass through this formatter. */
    public static Component chat(Component text) {
        Kind theme=kind(text);MutableComponent out;
        if(theme!=null) {
            String key=((TranslatableContents)text.getContents()).getKey();
            if(title(key))out=name(text.plainCopy().setStyle(text.getStyle())).withStyle(s->s.withColor(theme.accent));
            else {
                out=action(Component.empty()).setStyle(text.getStyle().withFont(ACTION).withBold(false).withItalic(false).withColor(theme.body));
                String value=text.plainCopy().getString();List<String> aliases=new ArrayList<>();
                if(!theme.name.isEmpty())aliases.add(Component.translatable(theme.name).getString());
                if(theme==Kind.TITAN)aliases.addAll(List.of("Таноса","Таносу","Таносом","Таносе","Безумный Титан","Безумного Титана","Mad Titan"));
                if(theme==Kind.COSMIC)aliases.addAll(List.of("Трибунала","Трибуналу","Трибуналом","Трибунале","Трибунал","Tribunal"));
                if(theme==Kind.TRICKSTER)aliases.addAll(List.of("Фенрира","Фенриру","Фенриром","Фенрир","Fenrir"));
                // Doom is highlighted only as the speaker ("Doctor Doom: ..."), never in the middle of a sentence.
                if(theme==Kind.SOVEREIGN){for(String speaker:List.of("Доктор Дум","Doctor Doom"))if(value.startsWith(speaker+":")){aliases.add(speaker);break;}}
                while(!value.isEmpty()) {
                    int at=Integer.MAX_VALUE;String match=null;
                    for(String alias:aliases){int i=word(value,alias);if(!alias.isEmpty() && i>=0 && (i<at || i==at && (match==null || alias.length()>match.length()))){at=i;match=alias;}}
                    if(match==null){out.append(Component.literal(value));break;}
                    if(at>0)out.append(Component.literal(value.substring(0,at)));
                    out.append(name(Component.literal(match)).withStyle(s->s.withColor(theme.accent)));
                    value=value.substring(at+match.length());
                }
            }
        } else if(text.getContents() instanceof TranslatableContents t) {
            Object[] args=t.getArgs().clone();for(int i=0;i<args.length;i++)if(args[i] instanceof Component c)args[i]=chat(c);
            out=(t.getFallback()==null?Component.translatable(t.getKey(),args):Component.translatableWithFallback(t.getKey(),t.getFallback(),args)).setStyle(text.getStyle());
        } else out=text.plainCopy().setStyle(text.getStyle());
        for(Component sibling:text.getSiblings())out.append(chat(sibling));
        return out;
    }
    private BossText() {}
}
