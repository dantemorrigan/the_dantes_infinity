package com.dantemorrigan.gauntlet.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the Doom armor's jets keep their wearer in the air without the server's "flying is not enabled" kick. */
@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerAccessor {
	@Accessor("aboveGroundTickCount")
	void infinityGauntlet$setAboveGroundTickCount(int ticks);
}
