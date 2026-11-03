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
    private float scale() {return Math.max(.35f,Math.min(1.05f,Math.min((width-16)/360f,(height-64)/360f)));}
    private double cx() {return width/2.0;}
    private double cy() {return (height-55)/2.0;}
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
    }
    @Override public void tick() {
        if(!AbilityInput.equipped() || Minecraft.getInstance().player==null || !Minecraft.getInstance().player.isAlive()) {onClose();return;}
        if(pending!=null && pending!=stone && System.nanoTime()-hoverSince>=140_000_000L) {stone=pending;hovered=null;}
    }
    private void finish() {
        if(finished) return;finished=true;
        Ability a=keyboard!=null?keyboard:hovered;
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
        if(key==GLFW.GLFW_KEY_PAGE_DOWN || key==GLFW.GLFW_KEY_PAGE_UP) {
            stone=ORDER[Math.floorMod(index(stone)+(key==GLFW.GLFW_KEY_PAGE_DOWN?1:-1),6)];keyboard=null;hovered=null;pending=null;return true;
        }
        if(key==GLFW.GLFW_KEY_TAB || key==GLFW.GLFW_KEY_RIGHT || key==GLFW.GLFW_KEY_LEFT || key==GLFW.GLFW_KEY_DOWN || key==GLFW.GLFW_KEY_UP) {
            int direction=key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_UP?-1:1;
            if(submenu!=0) target=Math.floorMod(target+direction,submenu==1?3:CosmosRoutes.ALL.size());
            else {var list=new ArrayList<>(Ability.of(stone,CosmosClient.inCosmos()));list.add(Ability.BEAM);Ability a=keyboard==null?(hovered==null?AbilityInput.last(stone):hovered):keyboard;keyboard=list.get(Math.floorMod(list.indexOf(a)+direction,list.size()));}
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
        if(hit(event.x(),event.y(),0,0,34)) {
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
        text(g,Component.translatable("ability.infinity_gauntlet.wheel"),width/2,8,0xFFF4D68D);
        blit(g,"wheel_frame",(int)(cx()-128*scale()),(int)(cy()-128*scale()),256*scale(),256*scale(),256,256);
        if(submenu==0) {
            for(int i=0;i<6;i++) {
                Stone s=ORDER[i];double a=Math.toRadians(-90+i*60);
                button(g,Math.cos(a)*88,Math.sin(a)*88,new ItemStack(s.item()),null,s==stone,GauntletState.inserted(GauntletState.activeGauntlet(Minecraft.getInstance().player)).contains(s),s,54);
            }
            for(Ability a:Ability.of(stone,CosmosClient.inCosmos())) {
                double[] p=abilityPoint(a);button(g,(p[0]-cx())/scale(),(p[1]-cy())/scale(),null,a,a==(keyboard!=null?keyboard:hovered),AbilityInput.selectable(a),stone,48);
            }
            button(g,-140,135,null,Ability.BEAM,(keyboard!=null?keyboard:hovered)==Ability.BEAM,AbilityInput.selectable(Ability.BEAM),Stone.MIND,48);
            button(g,0,0,GauntletState.activeGauntlet(Minecraft.getInstance().player),null,false,true,stone,68);
        } else {
            int count=submenu==1?3:CosmosRoutes.ALL.size();
            for(int i=0;i<count;i++) {
                double a=Math.toRadians(-90+360.0*i/count);double x=Math.cos(a)*115,y=Math.sin(a)*115;
                button(g,x,y,null,submenu==1?Ability.PORTAL:Ability.WARP,i==target,destinationAllowed(i),Stone.SPACE,54);
                Component label=submenu==1?Component.translatable("ability.infinity_gauntlet.dimension."+new String[]{"nether","end","overworld"}[i]):Component.translatable("cosmos.infinity_gauntlet."+CosmosRoutes.ALL.get(i).id());
                text(g,label,(int)(cx()+x*scale()),(int)(cy()+(y+34)*scale()),destinationAllowed(i)?0xFFDEE8F9:0xFF85818A);
            }
            button(g,0,0,null,null,false,true,Stone.SPACE,62);
            blit(g,"icons_64/back",(int)(cx()-16*scale()),(int)(cy()-16*scale()),32*scale(),32*scale(),64,64);
        }
        Ability focus=keyboard!=null?keyboard:hovered;
        if(focus==null && submenu==0) focus=AbilityInput.last(stone);
        int y=height-48;
        blit(g,"tooltip_panel",8,y-10,width-16,55,320,80);
        if(focus!=null && submenu==0) {
            text(g,AbilityInput.title(focus),width/2,y,color(focus.stone==null?Stone.MIND:focus.stone));
            text(g,focus==Ability.DESTINATIONS?Component.translatable("ability.infinity_gauntlet.open_submenu"):AbilityInput.hint(focus),width/2,y+12,0xFFE1D9E8);
        } else text(g,Component.translatable("ability.infinity_gauntlet.pick_destination"),width/2,y,0xFFDED7EA);
        text(g,Component.translatable("ability.infinity_gauntlet.wheel_help",AbilityInput.WHEEL.getTranslatedKeyMessage()),width/2,y+26,0xFF9BA7BB);
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
        if(!enabled) {g.fill(px,py,(int)(px+s),(int)(py+s),0xA513101B);blit(g,"icons_64/locked",px+s*.62f,py+s*.62f,s*.32f,s*.32f,64,64);}
        else if(ability!=null && AbilityInput.cooldown(ability)>0) {g.fill(px,py,(int)(px+s),(int)(py+s),0x9013101B);text(g,Component.literal(""+((AbilityInput.cooldown(ability)+19)/20)),(int)(px+s/2),(int)(py+s/2-4),0xFFFFDE82);}
    }
    public static void icon(GuiGraphicsExtractor g,Ability a,float x,float y,float size) {
        if(a==Ability.STRIKE || a==Ability.REALITY_STRIKE) {item(g,GauntletState.activeGauntlet(Minecraft.getInstance().player),x,y,size);return;}
        String id=a==Ability.DROP?"back":a==Ability.FLIGHT&&CosmosClient.flying()?"flight_disable":a==Ability.GRAB&&AbilityInput.holding()>0?"mind_throw":a.id;
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
