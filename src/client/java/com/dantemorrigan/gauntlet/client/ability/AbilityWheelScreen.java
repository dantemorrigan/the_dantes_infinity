package com.dantemorrigan.gauntlet.client.ability;

import com.dantemorrigan.gauntlet.*;
import com.dantemorrigan.gauntlet.ability.Ability;
import com.dantemorrigan.gauntlet.cosmos.*;
import com.dantemorrigan.gauntlet.client.cosmos.CosmosClient;
import com.dantemorrigan.gauntlet.power.SpaceStone;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import com.dantemorrigan.gauntlet.client.ui.Smooth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ScrollWheelHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Non-pausing radial screen. Mouse release chooses; only a later R press can cast. */
public final class AbilityWheelScreen extends Screen {
    private static final Stone[] ORDER={Stone.POWER,Stone.SPACE,Stone.REALITY,Stone.MIND,Stone.TIME,Stone.SOUL};
    private Stone stone;
    private Ability hovered,keyboard;
    private int submenu; // 0 abilities, 1 portal dimensions, 2 cosmic destinations
    private int target=-1;
    private double mouseX=-100,mouseY=-100;
    private long hoverSince;
    private Stone pending;
    private boolean finished;
    // Smooth feel: everything below eases by real time, so it is the same at any frame rate.
    private final Map<String,Float> glow=new HashMap<>();
    private float open,switchIn=1,dt;
    private long lastFrame;
    private Stone shown;
    private final ScrollWheelHandler wheel=new ScrollWheelHandler();
    /** The wheel or a key chose the focus: a little hand jitter on the mouse must not take it away. */
    private boolean scrolled;
    private double anchorX,anchorY;
    public AbilityWheelScreen() {super(Component.translatable("ability.infinity_gauntlet.wheel"));stone=AbilityInput.selected().stone==null?Stone.POWER:AbilityInput.selected().stone;}
    @Override public boolean isPauseScreen() {return false;}
    private float scale() {return Math.max(.2f,Math.min(1.05f,Math.min((width-16)/360f,(height-95)/360f)));}
    private double cx() {return width/2.0;}
    private double cy() {return (height-55)/2.0+9;}
    private boolean hit(double mx,double my,double x,double y,double r) {return Math.pow((mx-cx())/scale()-x,2)+Math.pow((my-cy())/scale()-y,2)<=r*r;}
    public double[] abilityPoint(Ability a) { // Used by visual tests and keyboard accessibility, same geometry as the renderer.
        var list=Ability.of(stone,CosmosClient.inCosmos());int i=list.indexOf(a);if(i<0)return new double[]{cx(),cy()};
        double angle=Math.toRadians(-90+index(stone)*60+(i-(list.size()-1)/2.0)*25);
        return new double[]{cx()+Math.cos(angle)*150*scale(),cy()+Math.sin(angle)*150*scale()};
    }
    private static int index(Stone s) {return Arrays.asList(ORDER).indexOf(s);}
    private static int color(Stone s) {return switch(s) {case POWER->0xFFBB72FF;case SPACE->0xFF51B8FF;case REALITY->0xFFFF6678;case MIND->0xFFFFDF63;case TIME->0xFF65E79D;case SOUL->0xFFFFAB58;};}
    private int portalCount() {
        var player=Minecraft.getInstance().player;
        return SpaceStone.DESTINATIONS.length + (player!=null
                && GauntletState.inserted(GauntletState.activeGauntlet(player)).contains(Stone.SPACE) ? 1 : 0);
    }
    private void pointer(double mx,double my) {
        if(mx==mouseX && my==mouseY) return;
        if(scrolled && Math.hypot(mx-anchorX,my-anchorY)<10) return;
        scrolled=false;
        mouseX=mx;mouseY=my;keyboard=null;hovered=null;target=-1;
        if(submenu==2) {
            for(int i=0;i<CosmosRoutes.ALL.size();i++) {int[] r=destinationBounds(i);if(mx>=r[0] && mx<r[0]+r[2] && my>=r[1] && my<r[1]+r[3]) target=i;}
            return;
        }
        if(submenu==1) {
            int count=portalCount();
            for(int i=0;i<count;i++) {double a=Math.toRadians(-90+360.0*i/count);if(hit(mx,my,Math.cos(a)*115,Math.sin(a)*115,29)) target=i;}
            return;
        }
        Stone under=null;
        for(int i=0;i<6;i++) {double a=Math.toRadians(-90+i*60);if(hit(mx,my,Math.cos(a)*88,Math.sin(a)*88,27)) under=ORDER[i];}
        if(under!=null) {
            if(pending!=under) {pending=under;hoverSince=System.nanoTime();}
        } else pending=null;
        for(Ability a:Ability.of(stone,CosmosClient.inCosmos())) {
            double[] p=abilityPoint(a);if(Math.hypot(mx-p[0],my-p[1])<=24*scale()) hovered=a;
        }
        if(hit(mx,my,-140,135,24)) hovered=Ability.BEAM;
        if(hit(mx,my,140,135,24)) hovered=Ability.SNAP;
    }
    @Override public void tick() {
        if(!AbilityInput.equipped() || Minecraft.getInstance().player==null || !Minecraft.getInstance().player.isAlive()) {onClose();return;}
        if(pending!=null && pending!=stone && System.nanoTime()-hoverSince>=140_000_000L) {stone=pending;hovered=null;}
    }
    private void finish() {
        if(finished) return;
        Ability a=keyboard!=null?keyboard:hovered;
        if(submenu==0 && (a==Ability.PORTAL || a==Ability.DESTINATIONS)) {
            submenu=a==Ability.PORTAL?1:2;target=-1;keyboard=null;hovered=null;pending=null;
            return; // Keep destinations visible after releasing V; click or Enter confirms.
        }
        if(submenu!=0 && (target<0 || !destinationAllowed(target))) return;
        finished=true;
        if(submenu!=0 && target>=0) {
            Ability chosen=submenu==1?Ability.PORTAL:Ability.WARP;
            if(destinationAllowed(target)) AbilityInput.choose(chosen,target);
        } else if(a!=null && a!=Ability.DESTINATIONS) AbilityInput.choose(a,-1);
        else if(pending!=null && submenu==0) AbilityInput.choose(AbilityInput.last(pending),-1);
        Minecraft.getInstance().setScreen(null);
    }
    @Override public void onClose() {finished=true;Minecraft.getInstance().setScreen(null);}
    @Override public boolean keyReleased(KeyEvent event) {
        if(event.key()==KeyMappingHelper.getBoundKeyOf(AbilityInput.WHEEL).getValue() && !GauntletConfig.get().abilityWheelToggle) {
            if(submenu!=1 || target!=SpaceStone.LATVERIA_OPTION) finish();
            return true;
        }
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        int key=event.key();
        if(key==GLFW.GLFW_KEY_ESCAPE) {if(submenu!=0){submenu=0;target=-1;}else onClose();return true;}
        if(key==KeyMappingHelper.getBoundKeyOf(AbilityInput.WHEEL).getValue() && GauntletConfig.get().abilityWheelToggle) {
            if(submenu!=1 || target!=SpaceStone.LATVERIA_OPTION) finish();
            return true;
        }
        int favoriteSlot = AbilityFavorites.slotForKey(key);
        if (submenu == 0 && favoriteSlot >= 0) {
            Ability ability = keyboard != null ? keyboard : hovered;
            if (ability == null) ability = AbilityInput.last(stone);
            if (ability != Ability.DESTINATIONS) AbilityFavorites.toggle(favoriteSlot, ability);
            return true;
        }
        if(key==GLFW.GLFW_KEY_PAGE_DOWN || key==GLFW.GLFW_KEY_PAGE_UP) {
            stone=ORDER[Math.floorMod(index(stone)+(key==GLFW.GLFW_KEY_PAGE_DOWN?1:-1),6)];keyboard=null;hovered=null;pending=null;return true;
        }
        if(key==GLFW.GLFW_KEY_TAB || key==GLFW.GLFW_KEY_RIGHT || key==GLFW.GLFW_KEY_LEFT || key==GLFW.GLFW_KEY_DOWN || key==GLFW.GLFW_KEY_UP) {
            int direction=key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_UP?-1:1;
            if(submenu!=0) target=Math.floorMod(target+direction,submenu==1?portalCount():CosmosRoutes.ALL.size());
            else {var list=new ArrayList<>(Ability.of(stone,CosmosClient.inCosmos()));list.add(Ability.BEAM);list.add(Ability.SNAP);Ability a=keyboard==null?(hovered==null?AbilityInput.last(stone):hovered):keyboard;keyboard=list.get(Math.floorMod(list.indexOf(a)+direction,list.size()));}
            return true;
        }
        if(key==GLFW.GLFW_KEY_ENTER || key==GLFW.GLFW_KEY_KP_ENTER) {
            Ability a=keyboard!=null?keyboard:hovered;
            if(submenu==0 && (a==Ability.PORTAL || a==Ability.DESTINATIONS)) {submenu=a==Ability.PORTAL?1:2;target=-1;keyboard=null;hovered=null;}
            else finish();return true;
        }
        return true; // No gameplay key events escape an open wheel.
    }
    /** The mouse wheel walks the abilities (down = next), crossing from one stone to the next without a stop. */
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy) {
        var steps=wheel.onMouseScroll(dx,dy);
        int delta=steps.y!=0?steps.y:-steps.x;
        if(delta==0) return true;
        int dir=Math.max(-3,Math.min(3,-delta));
        if(submenu!=0) {
            int n=submenu==1?portalCount():CosmosRoutes.ALL.size();
            target=Math.floorMod((target<0?(dir>0?-1:0):target)+dir,n);
        } else {
            var flat=AbilityInput.scrollable(true);
            if(flat.isEmpty()) return true;
            Ability sel=AbilityInput.selected();
            Ability cur=keyboard!=null?keyboard:hovered!=null?hovered:sel.stone==stone?sel:AbilityInput.last(stone);
            int i=flat.indexOf(cur);
            Ability next=flat.get(i<0?(dir>0?0:flat.size()-1):Math.floorMod(i+dir,flat.size()));
            keyboard=next;hovered=null;pending=null;
            if(next.stone!=null) stone=next.stone;
        }
        scrolled=true;anchorX=mouseX;anchorY=mouseY;
        AbilityScroll.tick(dir);
        return true;
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        pointer(event.x(),event.y());
        if(event.button()==1) {if(submenu!=0){submenu=0;target=-1;}else onClose();return true;}
        if(event.button()!=0)return true;
        if(submenu!=2 && hit(event.x(),event.y(),0,0,34)) {
            if(submenu!=0){submenu=0;target=-1;}else {finished=true;Minecraft.getInstance().setScreen(null);AbilityInput.inventory();}return true;
        }
        Ability a=keyboard!=null?keyboard:hovered;
        if(submenu==0 && (a==Ability.PORTAL || a==Ability.DESTINATIONS)) {submenu=a==Ability.PORTAL?1:2;target=-1;keyboard=null;hovered=null;return true;}
        if(submenu!=0 && target>=0 || a!=null && a!=Ability.DESTINATIONS) finish();
        return true;
    }
    private boolean destinationAllowed(int i) {
        if(submenu==2)return AbilityInput.selectable(Ability.WARP);
        if(i==SpaceStone.LATVERIA_OPTION) return i<portalCount() && !AbilityInput.latveriaActive() && (!AbilityInput.latveriaSealed() || AbilityInput.latveriaOpen());
        var l=Minecraft.getInstance().level;
        return AbilityInput.selectable(Ability.PORTAL) && l!=null && !(i==0&&l.dimension().equals(Level.NETHER)||i==1&&l.dimension().equals(Level.END)||i==2&&l.dimension().equals(Level.OVERWORLD));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        pointer(mx,my);
        long now=System.nanoTime();dt=Smooth.dt(lastFrame,now);lastFrame=now;
        open=Smooth.approach(open,1f,16f,dt);
        if(shown!=stone) {if(shown!=null) switchIn=0;shown=stone;}
        switchIn=Smooth.approach(switchIn,1f,13f,dt);
        g.fill(0,0,width,height,ARGB.color((int)(0x9C*open),0x09,0x08,0x11));
        tooltipLine(g,Component.translatable("ability.infinity_gauntlet.wheel").withStyle(net.minecraft.ChatFormatting.BOLD),8,0xFFF4D68D);
        if(submenu==2) { drawDestinations(g); return; }
        blit(g,"wheel_frame",(int)(cx()-128*scale()),(int)(cy()-128*scale()),256*scale(),256*scale(),256,256,fadeColor());
        if(submenu==0) {
            for(int i=0;i<6;i++) {
                Stone s=ORDER[i];double a=Math.toRadians(-90+i*60);
                button(g,"s"+s,Math.cos(a)*88,Math.sin(a)*88,new ItemStack(s.item()),null,s==stone,GauntletState.inserted(GauntletState.activeGauntlet(Minecraft.getInstance().player)).contains(s),s,54);
            }
            for(Ability a:Ability.of(stone,CosmosClient.inCosmos())) {
                double[] p=abilityPoint(a);button(g,"a"+a.id,(p[0]-cx())/scale(),(p[1]-cy())/scale(),null,a,a==(keyboard!=null?keyboard:hovered),AbilityInput.selectable(a),stone,48);
                if(stone==Stone.MIND) {
                    float labelScale=Math.min(1f,scale()*1.65f);
                    String key=a==Ability.GRAB&&AbilityInput.holding()>0?"throw":a==Ability.GRAB?"grab":a==Ability.TEAR?"tear":"drop";
                    g.pose().pushMatrix();g.pose().scale(labelScale,labelScale);
                    text(g,Component.translatable("ability.infinity_gauntlet.short."+key),(int)(p[0]/labelScale),(int)((cy()+180*scale())/labelScale),AbilityInput.selectable(a)?color(stone):0xFF85818A);
                    g.pose().popMatrix();
                }
            }
            button(g,"a"+Ability.BEAM.id,-140,135,null,Ability.BEAM,(keyboard!=null?keyboard:hovered)==Ability.BEAM,AbilityInput.selectable(Ability.BEAM),Stone.MIND,48);
            button(g,"a"+Ability.SNAP.id,140,135,null,Ability.SNAP,(keyboard!=null?keyboard:hovered)==Ability.SNAP,AbilityInput.selectable(Ability.SNAP),Stone.MIND,48);
            button(g,null,0,0,GauntletState.activeGauntlet(Minecraft.getInstance().player),null,false,true,stone,68);
        } else {
            int count=portalCount();
            for(int i=0;i<count;i++) {
                double a=Math.toRadians(-90+360.0*i/count);double x=Math.cos(a)*115,y=Math.sin(a)*115;
                boolean enabled=destinationAllowed(i);
                button(g,"d"+i,x,y,null,null,i==target,enabled,Stone.SPACE,54);
                Component label;
                if(i==SpaceStone.LATVERIA_OPTION) {
                    boolean open=AbilityInput.latveriaOpen();
                    boolean sealed=AbilityInput.latveriaSealed() && !open;
                    g.pose().pushMatrix();
                    g.pose().translate((float)(cx()+x*scale()),(float)(cy()+y*scale()));
                    g.pose().scale(2*scale(),2*scale());
                    text(g,Component.literal(sealed?"×":open?"◆":"?").withStyle(net.minecraft.ChatFormatting.BOLD),0,-4,
                        sealed?0xFF85818A:open?0xFF6CFF9A:0xFFC7E69E);
                    g.pose().popMatrix();
                    label=sealed||open?Component.translatable("ability.infinity_gauntlet.dimension.latveria"):Component.empty();
                } else {
                    String id=SpaceStone.DESTINATIONS[i];
                    blit(g,"icons_64/portal_"+id,(float)(cx()+(x-16)*scale()),(float)(cy()+(y-16)*scale()),32*scale(),32*scale(),64,64);
                    label=Component.translatable("ability.infinity_gauntlet.dimension."+id);
                }
                text(g,label,(int)(cx()+x*scale()),(int)(cy()+(y+34)*scale()),enabled?0xFFDEE8F9:0xFF85818A);
            }
            button(g,null,0,0,null,null,false,true,Stone.SPACE,62);
            blit(g,"icons_64/back",(int)(cx()-16*scale()),(int)(cy()-16*scale()),32*scale(),32*scale(),64,64);
        }
        Ability focus=keyboard!=null?keyboard:hovered;
        if(focus==null && submenu==0) focus=AbilityInput.last(stone);
        int panelTop = height - 58;
        blit(g, "tooltip_panel", 8, panelTop, width - 16, 55, 320, 80);
        int firstLine = panelTop + 8;
        if (submenu == 0 && hit(mx,my,0,0,34)) {
            tooltipLine(g, Component.translatable("container.infinity_gauntlet.gauntlet"), firstLine, 0xFFF4D68D);
            tooltipLine(g, Component.translatable("ability.infinity_gauntlet.open_inventory"), firstLine + 10, 0xFFE1D9E8);
        } else if (focus != null && submenu == 0) {
            tooltipLine(g, AbilityInput.title(focus), firstLine, color(focus.stone == null ? Stone.MIND : focus.stone));
            Component hint = focus == Ability.DESTINATIONS || focus == Ability.PORTAL
                ? Component.translatable("ability.infinity_gauntlet.open_submenu", AbilityInput.WHEEL.getTranslatedKeyMessage())
                : AbilityInput.hint(focus);
            tooltipLine(g, hint, firstLine + 10, 0xFFE1D9E8);
            int slot = AbilityFavorites.find(focus);
            Component binding = slot < 0
                ? Component.translatable("ability.infinity_gauntlet.favorites_bind")
                : Component.translatable("ability.infinity_gauntlet.favorites_bound", slot + 1);
            tooltipLine(g, binding, firstLine + 20, 0xFFF4D68D);
        } else {
            String key=submenu==1 && target==SpaceStone.LATVERIA_OPTION && AbilityInput.latveriaOpen()
                ?"ability.infinity_gauntlet.latveria_open"
                :submenu==1 && target==SpaceStone.LATVERIA_OPTION && AbilityInput.latveriaSealed()
                ?"ability.infinity_gauntlet.latveria_sealed":"ability.infinity_gauntlet.pick_destination";
            tooltipLine(g, Component.translatable(key), firstLine, 0xFFDED7EA);
        }
        tooltipLine(g, Component.translatable("ability.infinity_gauntlet.wheel_help",
            AbilityInput.WHEEL.getTranslatedKeyMessage()), firstLine + 30, 0xFF9BA7BB);
    }

    /** Shared bounds for rendering, pointer hit testing and resolution regression checks. */
    public int[] destinationBounds(int index) {
        int panel=Math.min(420,width-24), gap=6, cell=(panel-gap)/2;
        int row=Math.max(18,Math.min(42,(height-104)/5));
        return new int[]{(width-panel)/2+(index%2)*(cell+gap),32+(index/2)*row,cell,row-4};
    }
    private void drawDestinations(GuiGraphicsExtractor g) {
        var font=Minecraft.getInstance().font;
        for(int i=0;i<CosmosRoutes.ALL.size();i++) {
            int[] r=destinationBounds(i);boolean active=i==target;
            g.fill(r[0],r[1],r[0]+r[2],r[1]+r[3],active?0xff30465d:0xf0141826);
            g.fill(r[0],r[1],r[0]+2,r[1]+r[3],active?0xff78d8ff:0xff46506c);
            int size=Math.min(26,r[3]-4);
            blit(g,"icons_64/"+destinationIcon(CosmosRoutes.ALL.get(i).id()),r[0]+5,r[1]+(r[3]-size)/2f,size,size,64,64);
            Component name=Component.translatable("cosmos.infinity_gauntlet."+CosmosRoutes.ALL.get(i).id());
            float fit=Math.min(1,(float)(r[2]-size-16)/Math.max(1,font.width(name)));
            g.pose().pushMatrix();g.pose().translate(r[0]+size+11,r[1]+(r[3]-8*fit)/2);g.pose().scale(fit,fit);
            g.text(font,name,0,0,active?0xffffffff:0xffcddceb,true);g.pose().popMatrix();
        }
        int top=height-58;
        blit(g,"tooltip_panel",8,top,width-16,55,320,80);
        tooltipLine(g,Component.translatable("ability.infinity_gauntlet.pick_destination"),top+8,0xffffdfa0);
        if(target>=0) tooltipLine(g,Component.translatable("cosmos.infinity_gauntlet."+CosmosRoutes.ALL.get(target).id()),top+20,0xff86d8ff);
        tooltipLine(g,Component.translatable("ability.infinity_gauntlet.wheel_help",AbilityInput.WHEEL.getTranslatedKeyMessage()),top+34,0xffb1bacd);
    }

    private void tooltipLine(GuiGraphicsExtractor graphics, Component label, int y, int color) {
        var font = Minecraft.getInstance().font;
        int textWidth = font.width(label);
        int inset = Math.max(16, width / 16);
        int availableWidth = Math.max(1, width - inset * 2);
        float fit = Math.max(.6f, Math.min(1f, (float) availableWidth / Math.max(1, textWidth)));
        if (textWidth * fit > availableWidth) {
            label = Component.literal(font.substrByWidth(label, (int) (availableWidth / fit) - font.width("…")).getString() + "…");
            textWidth = font.width(label);
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(width / 2f, y);
        graphics.pose().scale(fit, fit);
        graphics.text(font, label, -textWidth / 2, 0, color, true);
        graphics.pose().popMatrix();
    }

    private static void text(GuiGraphicsExtractor g,Component text,int center,int y,int color) {
        var font=Minecraft.getInstance().font;g.text(font,text,center-font.width(text)/2,y,color,true);
    }
    private int fadeColor() {return ARGB.color((int)(255*Smooth.smoothstep(open)),255,255,255);}
    private float glow(String key,boolean on) {
        if(key==null) return on?1f:0f;
        float v=Smooth.approach(glow.getOrDefault(key,on?1f:0f),on?1f:0f,22f,dt);glow.put(key,v);return v;
    }
    /** {@code key} names the button for hover easing (null = no easing): the disc swells and its halo fades in and out instead of snapping. */
    private void button(GuiGraphicsExtractor g,String key,double x,double y,ItemStack stack,Ability ability,boolean selected,boolean enabled,Stone stone,int size) {
        float gl=glow(key,selected);
        boolean fresh=ability!=null && submenu==0 && ability!=Ability.BEAM && ability!=Ability.SNAP;
        float appear=fresh?.62f+.38f*Smooth.outBack(switchIn):1f;
        float s=size*scale()*(1f+.12f*gl)*appear*(.85f+.15f*Smooth.smoothstep(open));
        float cxp=(float)(cx()+x*scale()),cyp=(float)(cy()+y*scale());
        float px=cxp-s/2,py=cyp-s/2;
        float a=Smooth.smoothstep(open)*(fresh?Math.min(1f,switchIn*1.6f):1f);
        int color=ARGB.color((int)(255*a),255,255,255);
        blit(g,"button_disc",px,py,s,s,64,64,color);
        if(gl>0.01f) {float h=s*.07f;blit(g,"selection_halo",px-h,py-h,s+2*h,s+2*h,64,64,ARGB.color((int)(255*a*gl),255,255,255));}
        if(stack!=null) item(g,stack,px+s*.2f,py+s*.2f,s*.6f);
        else if(ability!=null) icon(g,ability,px+s*.2f,py+s*.2f,s*.6f,color);
        if(ability!=null && AbilityInput.cooldown(ability)>0) {text(g,Component.literal(""+((AbilityInput.cooldown(ability)+19)/20)),(int)cxp,(int)(cyp-4),0xFFFFDE82);}
    }
    private static String destinationIcon(String id) {
        return switch(id) {
            case "sun","mercury","venus","earth","moon","mars","jupiter","saturn","uranus","neptune" -> "route_"+id;
            default -> "destinations";
        };
    }
    /** Texture name (under icons_64/) of an ability as the wheel and the quick-switch strip draw it. */
    public static String iconName(Ability a) {
        return a==Ability.WARP?"route_earth":a==Ability.PORTAL?"portal_"+new String[]{"nether","end","overworld"}[Math.floorMod(AbilityInput.portal(),3)]:a==Ability.FLIGHT&&CosmosClient.flying()?"flight_disable":a==Ability.GRAB&&AbilityInput.holding()>0?"mind_throw":a.id;
    }
    public static void icon(GuiGraphicsExtractor g,Ability a,float x,float y,float size) {icon(g,a,x,y,size,-1);}
    public static void icon(GuiGraphicsExtractor g,Ability a,float x,float y,float size,int color) {
        blit(g,"icons_64/"+iconName(a),x,y,size,size,64,64,color);
    }
    private static void item(GuiGraphicsExtractor g,ItemStack stack,float x,float y,float size) {
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(size/16,size/16);g.item(stack,0,0);g.pose().popMatrix();
    }
    private static void blit(GuiGraphicsExtractor g,String name,float x,float y,float width,float height,int sourceW,int sourceH) {blit(g,name,x,y,width,height,sourceW,sourceH,-1);}
    private static void blit(GuiGraphicsExtractor g,String name,float x,float y,float width,float height,int sourceW,int sourceH,int color) {
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(width/sourceW,height/sourceH);
        g.blit(RenderPipelines.GUI_TEXTURED,InfinityGauntlet.id("textures/gui/ability_wheel/"+name+".png"),0,0,0f,0f,sourceW,sourceH,sourceW,sourceH,sourceW,sourceH,color);
        g.pose().popMatrix();
    }
}
