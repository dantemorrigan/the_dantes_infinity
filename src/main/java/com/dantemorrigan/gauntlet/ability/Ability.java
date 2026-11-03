package com.dantemorrigan.gauntlet.ability;

import com.dantemorrigan.gauntlet.Stone;
import java.util.*;

/** Stable IDs are shared by the wheel, server validation and saved selection. */
public enum Ability {
    STRIKE("power_strike",Stone.POWER,true), PLANET("planet_destruction",Stone.POWER,true), CELESTIAL("celestial_destruction",Stone.POWER,true), METEOR_STREAM("meteor_stream",Stone.POWER,true),
    PORTAL("portal",Stone.SPACE,false), BLINK("blink",Stone.SPACE,false), FLIGHT("flight",Stone.SPACE,false),
    DESTINATIONS("destinations",Stone.SPACE,false), WARP("warp",Stone.SPACE,false),
    REALITY_STRIKE("reality_strike",Stone.REALITY,false), INVERSION("reality_inversion",Stone.REALITY,true),
    REWIND("time_rewind",Stone.TIME,false), DAY_NIGHT("time_shift",Stone.TIME,false),
    GRAB("mind_grab",Stone.MIND,false), TEAR("mind_tear",Stone.MIND,false), DROP("mind_release",Stone.MIND,false),
    SOUL("soul_extract",Stone.SOUL,false), SOUL_MASS("soul_mass",Stone.SOUL,false), BEAM("infinity_beam",null,true), SNAP("infinity_snap",null,false);
    public final String id; public final Stone stone; public final boolean hold;
    Ability(String id,Stone stone,boolean hold) { this.id=id;this.stone=stone;this.hold=hold; }
    public String key() { return "ability.infinity_gauntlet."+id; }
    public boolean power() { return this==STRIKE || this==PLANET || this==CELESTIAL; }
    public static Ability byId(String id) { for(var a:values()) if(a.id.equals(id)) return a; return null; }
    public static List<Ability> of(Stone stone,boolean cosmos) {
        return switch(stone) {
            case POWER -> List.of(STRIKE,PLANET,CELESTIAL,METEOR_STREAM);
            case SPACE -> cosmos?List.of(FLIGHT,WARP):List.of(PORTAL,BLINK,FLIGHT);
            case REALITY -> List.of(REALITY_STRIKE,INVERSION);
            case TIME -> List.of(REWIND,DAY_NIGHT);
            case MIND -> List.of(GRAB,TEAR,DROP);
            case SOUL -> List.of(SOUL,SOUL_MASS);
        };
    }
}
