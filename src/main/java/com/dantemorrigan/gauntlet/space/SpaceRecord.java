package com.dantemorrigan.gauntlet.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Persistent per-player state after the planet was destroyed (stored as a player attachment).
 * Fragments keep the original BlockStates and the source biome so grass etc. keep their real tint.
 */
public record SpaceRecord(ResourceKey<Level> returnDimension, Vec3 returnPos, long eventStartTime, int seed, List<Fragment> fragments) {
	public static final Codec<SpaceRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceKey.codec(Registries.DIMENSION).fieldOf("return_dimension").forGetter(SpaceRecord::returnDimension),
			Vec3.CODEC.fieldOf("return_pos").forGetter(SpaceRecord::returnPos),
			Codec.LONG.fieldOf("event_start").forGetter(SpaceRecord::eventStartTime),
			Codec.INT.fieldOf("seed").forGetter(SpaceRecord::seed),
			Fragment.CODEC.listOf().fieldOf("fragments").forGetter(SpaceRecord::fragments)
	).apply(i, SpaceRecord::new));

	public record BlockEntry(BlockPos pos, BlockState state) {
		public static final Codec<BlockEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(BlockEntry::pos),
				BlockState.CODEC.fieldOf("state").forGetter(BlockEntry::state)
		).apply(i, BlockEntry::new));
	}

	/**
	 * A small piece of the destroyed world. {@code blueprint} names a prepared fallback model from the pack
	 * (used when no real blocks could be captured); otherwise {@code blocks} hold the real captured states.
	 */
	public record Fragment(String kind, Optional<String> blueprint, Optional<ResourceKey<Biome>> biome, List<BlockEntry> blocks) {
		public static final Codec<Fragment> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("kind").forGetter(Fragment::kind),
				Codec.STRING.optionalFieldOf("blueprint").forGetter(Fragment::blueprint),
				ResourceKey.codec(Registries.BIOME).optionalFieldOf("biome").forGetter(Fragment::biome),
				BlockEntry.CODEC.listOf().fieldOf("blocks").forGetter(Fragment::blocks)
		).apply(i, Fragment::new));
	}
}
