package com.dantemorrigan.gauntlet.client.ability;

import com.dantemorrigan.gauntlet.*;
import com.dantemorrigan.gauntlet.ability.Ability;
import com.dantemorrigan.gauntlet.client.*;
import com.dantemorrigan.gauntlet.client.fx.*;
import com.dantemorrigan.gauntlet.client.cosmos.CosmosClient;
import com.dantemorrigan.gauntlet.client.cutscene.Cutscene;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;

/** Ability selection, activation edges and the separate Latveria preview. */
public final class AbilityInput {
    public static final KeyMapping WHEEL=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.infinity_gauntlet.ability_wheel",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_V,ClientPower.CATEGORY));
    public static final KeyMapping FAVORITES = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.infinity_gauntlet.favorites", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, ClientPower.CATEGORY));
    public static final KeyMapping CAST=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.infinity_gauntlet.ability_cast",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_R,ClientPower.CATEGORY));
    private static Ability selected=Ability.STRIKE;
    private static JsonObject states=new JsonObject();
    private static int holding,charge,route,portal,ticks;
    private static boolean button,releaseGate,migrated;
    private static Level lastLevel;
    private static final Map<Stone,Ability> LAST=new EnumMap<>(Stone.class);
    private static final Stone[] SCROLL_ORDER={Stone.POWER,Stone.SPACE,Stone.REALITY,Stone.MIND,Stone.TIME,Stone.SOUL};
    /** Chosen by the mouse wheel and not yet sent / not yet confirmed by the server. */
    private static Ability scrollQueued,scrollPending;
    private static long scrollUntil;
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.AbilityState.TYPE,(p,c)->{
            Ability a=Ability.byId(p.selected());
            // While the wheel is being scrolled the server is a few packets behind: its older answers must not
            // snap the choice back. The first answer that matches what we asked for ends the wait.
            if(scrollPending!=null && System.nanoTime()<scrollUntil) {
                if(a==scrollPending) scrollPending=null;
                a=selected;
            } else scrollPending=null;
            selected=a==null?Ability.STRIKE:a;
            try { states=JsonParser.parseString(p.states()).getAsJsonObject(); } catch(RuntimeException e) { states=new JsonObject(); }
            holding=p.holding();charge=p.charge();route=p.route();portal=p.portal();
            if(selected.stone!=null) LAST.put(selected.stone,selected);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h,mc)->reset());
        ClientTickEvents.END_CLIENT_TICK.register(AbilityInput::tick);
    }
    public static boolean equipped() {
        var p=Minecraft.getInstance().player;return p!=null && !GauntletState.activeGauntlet(p).isEmpty();
    }
    public static Ability selected() { return selected; }
    public static int holding() { return holding; }
    public static int route() { return route; }
    public static int portal() { return portal; }
    public static boolean latveriaActive() { return states.has("latveriaActive") && states.get("latveriaActive").getAsBoolean(); }
    public static boolean latveriaSealed() { return states.has("latveriaSealed") && states.get("latveriaSealed").getAsBoolean(); }
    /** The Mind Stone has read Doom's seal: the sealed option is a way in. */
    public static boolean latveriaOpen() { return states.has("latveriaOpen") && states.get("latveriaOpen").getAsBoolean(); }
    public static int powerCharge() { return selected.power() && button?charge:0; }
    public static Ability last(Stone s) { return LAST.getOrDefault(s,Ability.of(s,CosmosClient.inCosmos()).getFirst()); }
    public static String reason(Ability a) {
        if(!equipped()) return "gauntlet";
        var p=Minecraft.getInstance().player;
        var stones=GauntletState.inserted(GauntletState.activeGauntlet(p));
        if(a.stone!=null && !stones.contains(a.stone) || (a==Ability.BEAM || a==Ability.SNAP) && stones.size()!=6) return "stone";
        var v=states.getAsJsonObject(a.id);return v==null?"":v.get("reason").getAsString();
    }
    public static boolean selectable(Ability a) { return equipped(); }
    public static int cooldown(Ability a) { var v=states.getAsJsonObject(a.id);return v==null?0:v.get("cooldown").getAsInt(); }
    public static Component title(Ability a) {
        if(a==Ability.GRAB && holding>0) return Component.translatable("ability.infinity_gauntlet.throw");
        if(a==Ability.PORTAL) return Component.translatable("ability.infinity_gauntlet.portal_target",Component.translatable("ability.infinity_gauntlet.dimension."+new String[]{"nether","end","overworld"}[Math.floorMod(portal,3)]));
        if(a==Ability.FLIGHT) return Component.translatable(CosmosClient.inCosmos()?"ability.infinity_gauntlet.return_atmosphere":CosmosClient.flying()?"ability.infinity_gauntlet.flight_disable":a.key());
        return Component.translatable(a.key());
    }
    public static Component hint(Ability a) {
        String why=reason(a);
        if(!why.isEmpty()) return why.equals("cooldown")?Component.translatable("ability.infinity_gauntlet.cooldown",(cooldown(a)+19)/20):Component.translatable("ability.infinity_gauntlet.reason."+why);
        if(a==selected && a.hold && charge>0 && button) return Component.translatable("ability.infinity_gauntlet.charge",charge);
        if(a==Ability.PORTAL) return Component.translatable("ability.infinity_gauntlet.portal_hint",CAST.getTranslatedKeyMessage(),CAST.getTranslatedKeyMessage());
        if(a==Ability.GRAB && holding==2) return Component.translatable("ability.infinity_gauntlet.tear_hint",CAST.getTranslatedKeyMessage(),CAST.getTranslatedKeyMessage());
        return Component.translatable(a.hold?"ability.infinity_gauntlet.hold_key":"ability.infinity_gauntlet.press_key",CAST.getTranslatedKeyMessage());
    }
    public static void choose(Ability a,int destination) {
        if(!selectable(a)) return;
        cancel();scrollQueued=scrollPending=null;
        if (a != Ability.PORTAL || destination != com.dantemorrigan.gauntlet.power.SpaceStone.LATVERIA_OPTION) {
            selected=a;if(a.stone!=null) LAST.put(a.stone,a);
        }
        ClientPlayNetworking.send(new ModNetwork.AbilitySelect(a.id,destination));releaseGate=true;
    }
    /** Every ability the wheel can step through, in wheel order: only stones that are in the gauntlet, then the two six-stone powers. */
    public static List<Ability> scrollable() {return scrollable(false);}
    /** @param destinations keep the "Destinations" entry that only opens the cosmic route menu (wanted by the wheel, not by the quick switch) */
    public static List<Ability> scrollable(boolean destinations) {
        var p=Minecraft.getInstance().player;
        if(p==null) return List.of();
        var stones=GauntletState.inserted(GauntletState.activeGauntlet(p));
        var out=new ArrayList<Ability>();
        for(Stone s:SCROLL_ORDER) if(stones.contains(s)) for(Ability a:Ability.of(s,CosmosClient.inCosmos())) if(destinations || a!=Ability.DESTINATIONS) out.add(a);
        if(stones.size()==6) {out.add(Ability.BEAM);out.add(Ability.SNAP);}
        return out;
    }
    /** The mouse wheel is allowed to steer the gauntlet right now (same locks as casting). */
    public static boolean scrollAllowed() {
        var mc=Minecraft.getInstance();
        return equipped() && mc.player.isAlive() && mc.screen==null && mc.isWindowActive()
            && !com.dantemorrigan.gauntlet.client.boss.TribunalClient.inputLocked() && !Cutscene.inputLocked() && !TimeRewindClient.inputLocked();
    }
    /**
     * Steps the selection by {@code steps} (positive = next) with no wheel and no round trip: the choice changes on
     * this very frame and the server hears about it at the next tick.
     * @return the new ability, or null when there is nothing to switch between
     */
    public static Ability scroll(int steps) {
        var list=scrollable();
        if(list.size()<2 || steps==0) return null;
        int i=list.indexOf(selected);
        Ability a=list.get(i<0?(steps>0?0:list.size()-1):Math.floorMod(i+steps,list.size()));
        if(a==selected) return a;
        if(button || charge>0) cancel(); // a charge in progress must not follow the new choice
        selected=a;if(a.stone!=null) LAST.put(a.stone,a);
        scrollQueued=scrollPending=a;scrollUntil=System.nanoTime()+700_000_000L;
        return a;
    }
    public static void inventory() {cancel();ClientPlayNetworking.send(new ModNetwork.AbilitySelect("inventory",-1));}
    public static void cancel() {
        if(Minecraft.getInstance().player!=null && ClientPlayNetworking.canSend(ModNetwork.AbilityAction.TYPE)) ClientPlayNetworking.send(new ModNetwork.AbilityAction(2));
        button=false;charge=0;releaseGate=true;
    }
    private static void reset() {scrollQueued=scrollPending=null;selected=Ability.STRIKE;states=new JsonObject();holding=charge=0;button=false;releaseGate=true;lastLevel=null;}
    private static void tick(Minecraft mc) {
        if(!migrated && mc.options!=null) {migrated=true;migrate(mc);}
        if(mc.player==null || mc.level==null) {reset();return;}
        if(lastLevel!=mc.level) {cancel();lastLevel=mc.level;scrollQueued=scrollPending=null;}
        if(scrollQueued!=null) { // one packet per tick however fast the wheel spins; the last choice wins
            if(ClientPlayNetworking.canSend(ModNetwork.AbilitySelect.TYPE)) ClientPlayNetworking.send(new ModNetwork.AbilitySelect(scrollQueued.id,-1));
            scrollQueued=null;
        }
        boolean open=false;while(WHEEL.consumeClick()) open=true;
        boolean openFavorites = false;
        while (FAVORITES.consumeClick()) openFavorites = true;
        if ((open || openFavorites) && mc.screen == null && equipped()
                && !com.dantemorrigan.gauntlet.client.boss.TribunalClient.inputLocked() && !Cutscene.inputLocked() && !TimeRewindClient.inputLocked()) {
            cancel();
            mc.setScreen(open ? new AbilityWheelScreen() : new AbilityFavoritesScreen());
        }
        // A gauntlet burnt out by the snap has no abilities left: the wheel key just opens it to take the stones out.
        else if(open && mc.screen==null && !equipped() && com.dantemorrigan.gauntlet.snap.InfinitySnap.snapped(GauntletState.wornGauntlet(mc.player))) inventory();
        if(!CAST.isDown()) releaseGate=false;
        if(!equipped() || !mc.player.isAlive() || mc.screen!=null || !mc.isWindowActive() || com.dantemorrigan.gauntlet.client.boss.TribunalClient.inputLocked() || Cutscene.inputLocked() || TimeRewindClient.inputLocked()) {
            if(button) cancel();
            while(CAST.consumeClick()) {} return;
        }
        if(!CAST.isDown()) releaseGate=false;
        boolean click=false;while(CAST.consumeClick()) click=true;
        if(click && !button && !releaseGate) {
            boolean alternate=mc.options.keyShift.isDown() && (selected==Ability.PORTAL || selected==Ability.GRAB && holding==2);
            ClientPlayNetworking.send(new ModNetwork.AbilityAction(alternate?3:0));button=true;ticks=0;
        }
        if(button) {
            ticks++;
            if(!CAST.isDown()) {ClientPlayNetworking.send(new ModNetwork.AbilityAction(1));button=false;charge=0;}
        }
    }
    /** Unbind only historical factory defaults once; keep explicit custom assignments. */
    private static void migrate(Minecraft mc) {
        Path marker=FabricLoader.getInstance().getConfigDir().resolve("infinity_gauntlet_wheel_bindings_v1");
        if(Files.exists(marker)) return;
        KeyMapping[] keys={ClientPower.TOGGLE_MODE,SpaceStoneClient.PORTAL,SpaceStoneClient.BLINK,TimeRewindClient.REWIND,TimeShiftClient.SHIFT,RealityClient.KEY,MindClient.GRAB,SoulClient.EXTRACT,InfinityBeamClient.BEAM,CosmosClient.FLIGHT,CosmosClient.NEXT,CosmosClient.WARP};
        int[] defaults={GLFW.GLFW_KEY_V,GLFW.GLFW_KEY_B,GLFW.GLFW_KEY_N,GLFW.GLFW_KEY_R,GLFW.GLFW_KEY_Y,GLFW.GLFW_KEY_G,GLFW.GLFW_KEY_Z,GLFW.GLFW_KEY_O,GLFW.GLFW_KEY_H,GLFW.GLFW_KEY_J,GLFW.GLFW_KEY_K,GLFW.GLFW_KEY_U};
        for(int i=0;i<keys.length;i++) if(KeyMappingHelper.getBoundKeyOf(keys[i]).getValue()==defaults[i]) keys[i].setKey(InputConstants.UNKNOWN);
        KeyMapping.resetMapping();mc.options.save();
        try {Files.createDirectories(marker.getParent());Files.writeString(marker,"wheel-v1\n");}catch(java.io.IOException e){InfinityGauntlet.LOGGER.warn("Could not save wheel key migration marker",e);}
    }
    private AbilityInput() {}
}
