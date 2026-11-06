package com.dantemorrigan.gauntlet.client.ability;

import com.dantemorrigan.gauntlet.*;
import com.dantemorrigan.gauntlet.ability.Ability;
import com.dantemorrigan.gauntlet.cosmos.*;
import com.dantemorrigan.gauntlet.client.cosmos.CosmosClient;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
    public AbilityWheelScreen() {super(Component.translatable("ability.infinity_gauntlet.wheel"));stone=AbilityInput.selected().stone==null?Stone.POWER:AbilityInput.selected().stone;}
    @Override public boolean isPauseScreen() {return false;}
    private float scale() {return Math.max(.35f,Math.min(1.05f,Math.min((width-16)/360f,(height-95)/360f)));}
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
    private void pointer(double mx,double my) {
        if(mx==mouseX && my==mouseY) return;
        mouseX=mx;mouseY=my;keyboard=null;hovered=null;target=-1;
        if(submenu==2) {
            for(int i=0;i<CosmosRoutes.ALL.size();i++) {int[] r=destinationBounds(i);if(mx>=r[0] && mx<r[0]+r[2] && my>=r[1] && my<r[1]+r[3]) target=i;}
            return;
        }
        if(submenu!=0) {
            int count=submenu==1?3:CosmosRoutes.ALL.size();
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
        if(event.key()==KeyMappingHelper.getBoundKeyOf(AbilityInput.WHEEL).getValue() && !GauntletConfig.get().abilityWheelToggle) {finish();return true;}
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        int key=event.key();
        if(key==GLFW.GLFW_KEY_ESCAPE) {if(submenu!=0){submenu=0;target=-1;}else onClose();return true;}
        if(key==KeyMappingHelper.getBoundKeyOf(AbilityInput.WHEEL).getValue() && GauntletConfig.get().abilityWheelToggle) {finish();return true;}
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
            if(submenu!=0) target=Math.floorMod(target+direction,submenu==1?3:CosmosRoutes.ALL.size());
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
        var l=Minecraft.getInstance().level;
        return AbilityInput.selectable(Ability.PORTAL) && l!=null && !(i==0&&l.dimension().equals(Level.NETHER)||i==1&&l.dimension().equals(Level.END)||i==2&&l.dimension().equals(Level.OVERWORLD));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        pointer(mx,my);g.fill(0,0,width,height,0x9C090811);
        tooltipLine(g,Component.translatable("ability.infinity_gauntlet.wheel").withStyle(net.minecraft.ChatFormatting.BOLD),8,0xFFF4D68D);
        if(submenu==2) { drawDestinations(g); return; }
        blit(g,"wheel_frame",(int)(cx()-128*scale()),(int)(cy()-128*scale()),256*scale(),256*scale(),256,256);
        if(submenu==0) {
            for(int i=0;i<6;i++) {
                Stone s=ORDER[i];double a=Math.toRadians(-90+i*60);
                button(g,Math.cos(a)*88,Math.sin(a)*88,new ItemStack(s.item()),null,s==stone,GauntletState.inserted(GauntletState.activeGauntlet(Minecraft.getInstance().player)).contains(s),s,54);
            }
            for(Ability a:Ability.of(stone,CosmosClient.inCosmos())) {
                double[] p=abilityPoint(a);button(g,(p[0]-cx())/scale(),(p[1]-cy())/scale(),null,a,a==(keyboard!=null?keyboard:hovered),AbilityInput.selectable(a),stone,48);
                if(stone==Stone.MIND) {
                    float labelScale=Math.min(1f,scale()*1.65f);
                    String key=a==Ability.GRAB&&AbilityInput.holding()>0?"throw":a==Ability.GRAB?"grab":a==Ability.TEAR?"tear":"drop";
                    g.pose().pushMatrix();g.pose().scale(labelScale,labelScale);
                    text(g,Component.translatable("ability.infinity_gauntlet.short."+key),(int)(p[0]/labelScale),(int)((cy()+180*scale())/labelScale),AbilityInput.selectable(a)?color(stone):0xFF85818A);
                    g.pose().popMatrix();
                }
            }
            button(g,-140,135,null,Ability.BEAM,(keyboard!=null?keyboard:hovered)==Ability.BEAM,AbilityInput.selectable(Ability.BEAM),Stone.MIND,48);
            button(g,140,135,null,Ability.SNAP,(keyboard!=null?keyboard:hovered)==Ability.SNAP,AbilityInput.selectable(Ability.SNAP),Stone.MIND,48);
            button(g,0,0,GauntletState.activeGauntlet(Minecraft.getInstance().player),null,false,true,stone,68);
        } else {
            int count=submenu==1?3:CosmosRoutes.ALL.size();
            for(int i=0;i<count;i++) {
                double a=Math.toRadians(-90+360.0*i/count);double x=Math.cos(a)*115,y=Math.sin(a)*115;
                button(g,x,y,null,null,i==target,destinationAllowed(i),Stone.SPACE,54);
                blit(g,"icons_64/"+(submenu==1?"portal_"+new String[]{"nether","end","overworld"}[i]:destinationIcon(CosmosRoutes.ALL.get(i).id())),(float)(cx()+(x-16)*scale()),(float)(cy()+(y-16)*scale()),32*scale(),32*scale(),64,64);
                Component label=submenu==1?Component.translatable("ability.infinity_gauntlet.dimension."+new String[]{"nether","end","overworld"}[i]):Component.translatable("cosmos.infinity_gauntlet."+CosmosRoutes.ALL.get(i).id());
                text(g,label,(int)(cx()+x*scale()),(int)(cy()+(y+34)*scale()),destinationAllowed(i)?0xFFDEE8F9:0xFF85818A);
            }
            button(g,0,0,null,null,false,true,Stone.SPACE,62);
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
            tooltipLine(g, Component.translatable("ability.infinity_gauntlet.pick_destination"), firstLine, 0xFFDED7EA);
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
        float fit = Math.min(1f, (float) availableWidth / Math.max(1, textWidth));
        graphics.pose().pushMatrix();
        graphics.pose().translate(width / 2f, y);
        graphics.pose().scale(fit, fit);
        graphics.text(font, label, -textWidth / 2, 0, color, true);
        graphics.pose().popMatrix();
    }

    private static void text(GuiGraphicsExtractor g,Component text,int center,int y,int color) {
        var font=Minecraft.getInstance().font;g.text(font,text,center-font.width(text)/2,y,color,true);
    }
    private void button(GuiGraphicsExtractor g,double x,double y,ItemStack stack,Ability ability,boolean selected,boolean enabled,Stone stone,int size) {
        int px=(int)(cx()+x*scale()-size*scale()/2),py=(int)(cy()+y*scale()-size*scale()/2);float s=size*scale();
        blit(g,"button_disc",px,py,s,s,64,64);
        if(selected) blit(g,"selection_halo",px-2,py-2,s+4,s+4,64,64);
        if(stack!=null) item(g,stack,px+s*.2f,py+s*.2f,s*.6f);
        else if(ability!=null) icon(g,ability,px+s*.2f,py+s*.2f,s*.6f);
        if(ability!=null && AbilityInput.cooldown(ability)>0) {text(g,Component.literal(""+((AbilityInput.cooldown(ability)+19)/20)),(int)(px+s/2),(int)(py+s/2-4),0xFFFFDE82);}
    }
    private static String destinationIcon(String id) {
        return switch (id) {
            case "earth", "moon" -> "route_" + id;
            case "sun", "mercury", "venus", "mars" -> "route_ember";
            case "uranus", "neptune" -> "route_ice";
            default -> "destinations";
        };
    }
    public static void icon(GuiGraphicsExtractor g,Ability a,float x,float y,float size) {
        String id=a==Ability.WARP?"route_earth":a==Ability.PORTAL?"portal_"+new String[]{"nether","end","overworld"}[Math.floorMod(AbilityInput.portal(),3)]:a==Ability.FLIGHT&&CosmosClient.flying()?"flight_disable":a==Ability.GRAB&&AbilityInput.holding()>0?"mind_throw":a.id;
        blit(g,"icons_64/"+id,x,y,size,size,64,64);
    }
    private static void item(GuiGraphicsExtractor g,ItemStack stack,float x,float y,float size) {
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(size/16,size/16);g.item(stack,0,0);g.pose().popMatrix();
    }
    private static void blit(GuiGraphicsExtractor g,String name,float x,float y,float width,float height,int sourceW,int sourceH) {
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(width/sourceW,height/sourceH);
        g.blit(RenderPipelines.GUI_TEXTURED,InfinityGauntlet.id("textures/gui/ability_wheel/"+name+".png"),0,0,0f,0f,sourceW,sourceH,sourceW,sourceH);
        g.pose().popMatrix();
    }
}
