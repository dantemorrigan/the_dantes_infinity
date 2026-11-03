package com.dantemorrigan.gauntlet;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

public final class ModComponents {
	/** Power stone mode stored on the gauntlet: false = power strike, true = Planet Destruction ultimate. */
	public static final DataComponentType<Boolean> ULTIMATE_MODE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			InfinityGauntlet.id("ultimate_mode"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

	public static void init() {}

	private ModComponents() {}
}
