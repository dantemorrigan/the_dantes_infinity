package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModBlocks;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.ModSounds;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.net.ModStatus;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Time Stone quest: insert three Ancient Gears (Overworld, Nether, End) into the broken clock mechanism
 * inside the Temple of Time to start the clock and receive the Time Stone.
 */
public final class TimeQuest {
	public static final String GEAR_OVERWORLD = "overworld";
	public static final String GEAR_NETHER = "nether";
	public static final String GEAR_END = "end";

	/** Which dimensional gears have been locked into the Temple of Time clock on the overworld. */
	private static final AttachmentType<List<String>> INSERTED_GEARS = AttachmentRegistry.<List<String>>builder()
			.persistent(Codec.STRING.listOf()).buildAndRegister(InfinityGauntlet.id("time_clock_gears"));

	/** Registers the persistent attachment at mod start: a world saved with it must find the type known on load. */
	public static void init() {}

	public static boolean isGear(ItemStack stack) {
		return gearId(stack) != null;
	}

	public static String gearId(ItemStack stack) {
		if (stack.is(ModItems.QUEST_TIME_GEAR_OVERWORLD)) return GEAR_OVERWORLD;
		if (stack.is(ModItems.QUEST_TIME_GEAR_NETHER)) return GEAR_NETHER;
		if (stack.is(ModItems.QUEST_TIME_GEAR_END)) return GEAR_END;
		return null;
	}

	public static List<String> insertedGears(MinecraftServer server) {
		List<String> list = server.overworld().getAttached(INSERTED_GEARS);
		return list == null ? List.of() : list;
	}

	public static int insertedCount(MinecraftServer server) {
		if (StoneRegistry.released(server, Stone.TIME) || StoneKeeper.anyPlayerHas(server, Stone.TIME)) return 3;
		return Math.min(3, insertedGears(server).size());
	}

	/** Ensures the world record and clock block reflect all 3 gears inserted. */
	public static void markClockCompleted(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		overworld.setAttached(INSERTED_GEARS, List.of(GEAR_OVERWORLD, GEAR_NETHER, GEAR_END));
		if (!TimeTemple.built(server)) return;
		BlockPos clockPos = TimeTemple.clockPos(TimeTemple.site(server));
		if (overworld.hasChunkAt(clockPos)) {
			BlockState state = overworld.getBlockState(clockPos);
			if (state.is(ModBlocks.QUEST_TIME_CLOCK) && state.getValue(TimeClockBlock.GEARS) != 3) {
				overworld.setBlock(clockPos, state.setValue(TimeClockBlock.GEARS, 3), Block.UPDATE_ALL);
			}
		}
	}

	private static boolean isCompleted(MinecraftServer server, ServerLevel level, BlockPos pos, BlockState state) {
		boolean hasStone = StoneKeeper.anyPlayerHas(server, Stone.TIME);
		if (hasStone || StoneRegistry.released(server, Stone.TIME) || insertedGears(server).size() >= 3) {
			if (hasStone) StoneRegistry.markReleased(server, Stone.TIME);
			markClockCompleted(server);
			if (state.is(ModBlocks.QUEST_TIME_CLOCK) && state.getValue(TimeClockBlock.GEARS) != 3) {
				level.setBlock(pos, state.setValue(TimeClockBlock.GEARS, 3), Block.UPDATE_ALL);
			}
			return true;
		}
		return false;
	}

	/** Right-click on the clock without a gear in main hand: tries offhand/backpack gear, or reports status. */
	public static void inspectOrInsertFromInventory(ServerPlayer player, BlockPos pos, BlockState state) {
		MinecraftServer server = player.level().getServer();
		if (isCompleted(server, (ServerLevel) player.level(), pos, state)) {
			ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.time_quest.already_running"));
			return;
		}
		List<String> current = insertedGears(server);
		// Check if the player has a needed gear anywhere in their inventory (hands or backpack).
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			String id = gearId(stack);
			if (id != null && !current.contains(id)) {
				insertGear(player, pos, state, stack);
				return;
			}
		}
		ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.time_quest.status", current.size()));
	}

	/** Inserts a specific gear stack held or carried by the player into the clock mechanism. */
	public static boolean insertGear(ServerPlayer player, BlockPos pos, BlockState state, ItemStack stack) {
		String id = gearId(stack);
		if (id == null) return false;
		MinecraftServer server = player.level().getServer();
		ServerLevel level = (ServerLevel) player.level();
		if (isCompleted(server, level, pos, state)) {
			ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.time_quest.already_running"));
			return false;
		}
		ServerLevel overworld = server.overworld();
		List<String> current = new ArrayList<>(insertedGears(server));
		if (current.contains(id)) {
			ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.time_quest.duplicate_gear", current.size()));
			player.level().playSound(null, pos, SoundEvents.COPPER_BULB_TURN_OFF, SoundSource.BLOCKS, 1.0F, 0.7F);
			return false;
		}

		if (!player.isCreative()) stack.shrink(1);
		current.add(id);
		overworld.setAttached(INSERTED_GEARS, List.copyOf(current));
		StoneRegistry.markFound(server, Stone.TIME);

		int count = Math.min(3, current.size());
		Direction facing = state.hasProperty(TimeClockBlock.FACING) ? state.getValue(TimeClockBlock.FACING) : Direction.SOUTH;
		if (state.is(ModBlocks.QUEST_TIME_CLOCK)) {
			level.setBlock(pos, state.setValue(TimeClockBlock.GEARS, count), Block.UPDATE_ALL);
		}

		double fx = pos.getX() + 0.5 + facing.getStepX() * 0.6;
		double fy = pos.getY() + 0.5;
		double fz = pos.getZ() + 0.5 + facing.getStepZ() * 0.6;
		level.sendParticles(ModParticles.QUEST_TIME_GEAR_LOCK, fx, fy, fz, 1, 0, 0, 0, 0);
		level.playSound(null, pos, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.BLOCKS, 1.4F, 0.75F + count * 0.15F);
		level.playSound(null, pos, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, 1.2F, 0.8F + count * 0.2F);

		if (count < 3) {
			ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.time_quest.gear_inserted", count));
			return true;
		}

		// All 3 gears (Overworld, Nether, End) are locked in: start the clock and grant the Time Stone!
		level.sendParticles(ModParticles.QUEST_TIME_CLOCK_START, fx, fy, fz, 1, 0, 0, 0, 0);
		level.sendParticles(ModParticles.QUEST_TIME_REWARD_SPIRAL, fx, fy, fz, 1, 0, 0, 0, 0);
		level.sendParticles(ModParticles.QUEST_TIME_REWARD_SPIRAL, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
		level.playSound(null, pos, ModSounds.STONE_INSERT, SoundSource.BLOCKS, 2.0F, 1.0F);
		level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2.5F, 1.2F);
		level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0F, 0.9F);

		if (StoneRegistry.markReleased(server, Stone.TIME)) {
			ItemStack timeStone = new ItemStack(ModItems.TIME_STONE);
			if (player.getMainHandItem().isEmpty()) {
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, timeStone);
			} else if (!player.getInventory().add(timeStone)) {
				OrbItem.giveIntoBackpack(player, timeStone);
			}
		}
		ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.time_quest.completed"));
		return true;
	}

	/** Resets the clock mechanism when the Time Stone is destroyed in creative or stones are reset. */
	public static void resetClock(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		overworld.removeAttached(INSERTED_GEARS);
		if (!TimeTemple.built(server)) return;
		BlockPos clockPos = TimeTemple.clockPos(TimeTemple.site(server));
		if (overworld.hasChunkAt(clockPos)) {
			BlockState state = overworld.getBlockState(clockPos);
			if (state.is(ModBlocks.QUEST_TIME_CLOCK)) {
				overworld.setBlock(clockPos, state.setValue(TimeClockBlock.GEARS, 0), Block.UPDATE_ALL);
			}
		}
	}

	private TimeQuest() {}
}
