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

	/** Power stone ability: 0 = strike, 1 = Planet Destruction, 2 = Celestial Apocalypse (break the sun/moon). */
	public static final DataComponentType<Integer> POWER_MODE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			InfinityGauntlet.id("power_mode"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	/** Space stone portal destination index (see SpaceStone.DESTINATIONS). */
	public static final DataComponentType<Integer> PORTAL_DESTINATION = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			InfinityGauntlet.id("portal_destination"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	public static void init() {}

	private ModComponents() {}
}
