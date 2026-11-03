package com.dantemorrigan.gauntlet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Cached block geometry from real BlockStates: the vanilla block model (so resource packs apply) plus the
 * correct tint, computed once. Biome tint comes either from the live world or from a stored biome.
 */
public final class BlockMeshes {
	private static final BlockDisplayContext CONTEXT = BlockDisplayContext.create();

	public record Baked(BlockPos pos, BlockModelRenderState state) {}

	/** A group of blocks, positions relative to a pivot (in blocks). */
	public record Mesh(List<Baked> blocks, float cx, float cy, float cz) {
		public void submit(PoseStack pose, SubmitNodeCollector collector, int light) {
			for (Baked b : blocks) {
				pose.pushPose();
				pose.translate(b.pos.getX() - cx, b.pos.getY() - cy, b.pos.getZ() - cz);
				b.state.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
				pose.popPose();
			}
		}
	}

	public static Baked bake(BlockState state, BlockPos rel, BlockAndTintGetter tintLevel, BlockPos tintPos) {
		Minecraft mc = Minecraft.getInstance();
		BlockModelRenderState rs = new BlockModelRenderState();
		mc.blockModelResolver.update(rs, state, CONTEXT);
		List<BlockTintSource> sources = mc.getBlockColors().getTintSources(state);
		if (!sources.isEmpty()) {
			rs.tintLayers().clear();
			for (BlockTintSource src : sources) rs.tintLayers().add(src.colorInWorld(state, tintLevel, tintPos));
		}
		return new Baked(rel.immutable(), rs);
	}

	public static Mesh mesh(List<BlockState> states, List<BlockPos> positions, BlockAndTintGetter tintLevel, BlockPos tintPos, float cx, float cy, float cz) {
		List<Baked> list = new ArrayList<>(states.size());
		for (int i = 0; i < states.size(); i++) {
			if (states.get(i).isAir()) continue;
			list.add(bake(states.get(i), positions.get(i), tintLevel, tintPos));
		}
		return new Mesh(list, cx, cy, cz);
	}

	/** Tint getter that answers biome colors for a stored biome (grass, foliage, water...) — no world needed. */
	public static BlockAndTintGetter biomeTint(Holder<Biome> biome) {
		return new BlockAndTintGetter() {
			@Override
			public int getBlockTint(BlockPos pos, ColorResolver color) {
				return color.getColor(biome.value(), pos.getX(), pos.getZ());
			}

			@Override
			public net.minecraft.world.level.CardinalLighting cardinalLighting() {
				return EMPTY.cardinalLighting();
			}

			@Override
			public net.minecraft.world.level.lighting.LevelLightEngine getLightEngine() {
				return EMPTY.getLightEngine();
			}

			@Override
			public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
				return null;
			}

			@Override
			public BlockState getBlockState(BlockPos pos) {
				return EMPTY.getBlockState(pos);
			}

			@Override
			public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) {
				return EMPTY.getFluidState(pos);
			}

			@Override
			public int getHeight() {
				return 0;
			}

			@Override
			public int getMinY() {
				return 0;
			}
		};
	}

	private BlockMeshes() {}
}
