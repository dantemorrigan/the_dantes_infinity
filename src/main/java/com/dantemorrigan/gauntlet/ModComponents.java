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

	/** Set on the gauntlet and its stones by the Infinity Snap: burnt, dead stones, the gauntlet no longer works. */
	public static final DataComponentType<Boolean> SNAPPED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			InfinityGauntlet.id("snapped"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

	/** Infinity Extractor: ordinal of the stone it pulls, absent = automatic (first free stone of the target). */
	public static final DataComponentType<Integer> EXTRACTOR_STONE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			InfinityGauntlet.id("extractor_stone"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	/** The world record invalidates old copies when the original returns or is reclaimed. */
	public static final DataComponentType<String> STORMBREAKER_SEAL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			InfinityGauntlet.id("stormbreaker_seal"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

	public static void init() {}

	private ModComponents() {}
}
