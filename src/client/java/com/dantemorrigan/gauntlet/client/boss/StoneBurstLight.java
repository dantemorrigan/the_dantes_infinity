package com.dantemorrigan.gauntlet.client.boss;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.Stone;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/** One cached colour field and a white flash, independent of screen size. No per-frame pixel loops. */
public final class StoneBurstLight {
    private static final int SIZE=256;
    private static final Identifier TEXTURE=InfinityGauntlet.id("dynamic/six_stone_burst");
    private static boolean ready;
    public static void prepare() {
        if(ready)return;
        NativeImage pixels=new NativeImage(SIZE,SIZE,false);
        var stones=Stone.values();
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++) {
            double dx=(x+.5)/SIZE-.5,dy=(y+.5)/SIZE-.5;
            double angle=(Math.atan2(dy,dx)+Math.PI)/(Math.PI*2)*6+.18;
            int sector=(int)angle;float blend=(float)(angle-sector);
            int a=stones[sector%6].color,b=stones[(sector+1)%6].color;
            float core=(float)Math.exp(-(dx*dx+dy*dy)/.072),white=core*.8f;
            int r=(int)(((a>>16&255)*(1-blend)+(b>>16&255)*blend)*(1-white)+255*white);
            int g=(int)(((a>>8&255)*(1-blend)+(b>>8&255)*blend)*(1-white)+255*white);
            int blue=(int)(((a&255)*(1-blend)+(b&255)*blend)*(1-white)+255*white);
            pixels.setPixel(x,y,ARGB.color((int)(255*(.3f+.5f*core)),r,g,blue));
        }
        var texture=new DynamicTexture(TEXTURE::toString,pixels) {
            {sampler=com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);}
        };
        Minecraft.getInstance().getTextureManager().register(TEXTURE,texture);ready=true;
    }
    public static void draw(GuiGraphicsExtractor g,float age) {
        prepare();float power=Mth.clamp(age/8,0,1),white=Mth.clamp((age-14)/36,0,1);
        g.blit(RenderPipelines.GUI_TEXTURED,TEXTURE,0,0,0,0,g.guiWidth(),g.guiHeight(),SIZE,SIZE,SIZE,SIZE,ARGB.color((int)(255*power),255,255,255));
        if(white>0)g.fill(0,0,g.guiWidth(),g.guiHeight(),ARGB.color((int)(255*white),255,255,255));
    }
    private StoneBurstLight() {}
}
