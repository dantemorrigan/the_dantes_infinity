package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.client.ability.AbilityInput;
import com.dantemorrigan.gauntlet.client.fx.*;
import com.dantemorrigan.gauntlet.client.cosmos.CosmosClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;

/** Legacy suites deliberately opt back into their historical controls; production defaults stay V/R. */
public final class LegacyBindings {
    public static void enable(ClientGameTestContext ctx) { configure(ctx,true); }
    public static void disable(ClientGameTestContext ctx) { configure(ctx,false); }
    private static void configure(ClientGameTestContext ctx,boolean legacy) {
        ctx.runOnClient(mc->{
            GauntletConfig.get().legacyRightClickPower=legacy;
            KeyMapping[] keys={ClientPower.TOGGLE_MODE,SpaceStoneClient.PORTAL,SpaceStoneClient.BLINK,TimeRewindClient.REWIND,TimeShiftClient.SHIFT,RealityClient.KEY,MindClient.GRAB,SoulClient.EXTRACT,InfinityBeamClient.BEAM,CosmosClient.FLIGHT,CosmosClient.NEXT,CosmosClient.WARP};
            int[] values={86,66,78,82,89,71,90,79,72,74,75,85};
            for(int i=0;i<keys.length;i++) keys[i].setKey(legacy?InputConstants.Type.KEYSYM.getOrCreate(values[i]):InputConstants.UNKNOWN);
            AbilityInput.WHEEL.setKey(legacy?InputConstants.UNKNOWN:InputConstants.Type.KEYSYM.getOrCreate(86));
            com.dantemorrigan.gauntlet.client.render.GauntletGestureClient.KEY.setKey(legacy?InputConstants.UNKNOWN:InputConstants.Type.KEYSYM.getOrCreate(73));
            AbilityInput.FAVORITES.setKey(legacy?InputConstants.UNKNOWN:InputConstants.Type.KEYSYM.getOrCreate(66));
            AbilityInput.CAST.setKey(legacy?InputConstants.UNKNOWN:InputConstants.Type.KEYSYM.getOrCreate(82));
            KeyMapping.resetMapping();
        });
    }
}
