package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** The usable items of the add-on. What an item does is decided on the server. */
public final class DoomItems {
	/** Latveria Key: travel to Latveria (the castle for its owners, the valley while sneaking) and back. */
	public static final class Key extends Item {
		public Key(Properties properties) { super(properties); }

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
			if (!Latveria.hasAccess(p)) DoomFx.tell(p, "key.denied");
			else if (p.level().dimension() == Latveria.LEVEL) Latveria.leave(p);
			else if (!Latveria.enter(p, !p.isShiftKeyDown())) DoomFx.tell(p, "key.denied");
			p.getCooldowns().addCooldown(p.getItemInHand(hand), 60);
			return InteractionResult.SUCCESS;
		}
	}

	/** Castle Seal: a guest pass. Whoever uses it may travel to Latveria from then on. */
	public static final class Seal extends Item {
		public Seal(Properties properties) { super(properties); }

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
			if (Latveria.hasAccess(p)) {
				DoomFx.tell(p, "seal.have");
				return InteractionResult.SUCCESS;
			}
			Latveria.grantAccess(p);
			p.getItemInHand(hand).shrink(1);
			DoomFx.tell(p, "seal.accepted");
			p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.6f);
			return InteractionResult.SUCCESS;
		}
	}

	/** Doombot Controller: all of the owner's Doombots nearby switch between following and standing guard. */
	public static final class Controller extends Item {
		public Controller(Properties properties) { super(properties); }

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
			if (!Latveria.isOwner(p)) { DoomFx.tell(p, "bot.denied"); return InteractionResult.SUCCESS; }
			List<Doombot> bots = p.level().getEntitiesOfClass(Doombot.class, p.getBoundingBox().inflate(48), b -> b.isAlive() && b.allied());
			if (bots.isEmpty()) { DoomFx.tell(p, "bot.none"); return InteractionResult.SUCCESS; }
			boolean guard = !bots.getFirst().guarding();
			for (Doombot b : bots) b.setGuarding(guard);
			DoomFx.tell(p, guard ? "bot.all_guard" : "bot.all_follow", bots.size());
			p.getCooldowns().addCooldown(p.getItemInHand(hand), 10);
			return InteractionResult.SUCCESS;
		}
	}

	/**
	 * A Mechanical Heart builds a Doombot for the castle's lord. What is held in the other hand decides its role and is
	 * used up: an arc reactor makes a Bastion, a feather or rocket an Interceptor, a jade shard an Arcanist, a repair
	 * tool an Engineer, an axe a Royal Guard; nothing makes a Soldier. At an anvil the robot comes out of a proper forge.
	 */
	public static final class Heart extends Item {
		public Heart(Properties properties) { super(properties); }

		public static int role(ItemStack catalyst) {
			if (catalyst.is(ModItems.ARC_REACTOR)) return Doombot.BASTION;
			if (catalyst.is(net.minecraft.world.item.Items.FEATHER) || catalyst.is(net.minecraft.world.item.Items.FIREWORK_ROCKET)) return Doombot.INTERCEPTOR;
			if (catalyst.is(ModItems.JADE_SHARD)) return Doombot.ARCANIST;
			if (catalyst.is(ModItems.REPAIR_TOOL)) return Doombot.ENGINEER;
			if (catalyst.is(net.minecraft.tags.ItemTags.AXES)) return Doombot.ROYAL_GUARD;
			return Doombot.SOLDIER;
		}

		/** Registered once: a heart used on an anvil builds a robot instead of opening the anvil. */
		public static void init() {
			net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
				if (!player.getItemInHand(hand).is(ModItems.MECHANICAL_HEART) || !level.getBlockState(hit.getBlockPos()).is(net.minecraft.tags.BlockTags.ANVIL))
					return InteractionResult.PASS;
				if (player instanceof ServerPlayer p && level instanceof ServerLevel sl) build(p, sl, hit.getBlockPos(), hit.getDirection(), hand);
				return InteractionResult.SUCCESS;
			});
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			if (!(context.getPlayer() instanceof ServerPlayer p) || !(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
			build(p, level, context.getClickedPos(), context.getClickedFace(), context.getHand());
			return InteractionResult.SUCCESS;
		}

		static void build(ServerPlayer p, ServerLevel level, BlockPos clicked, net.minecraft.core.Direction face, InteractionHand hand) {
			if (!Latveria.isOwner(p)) { DoomFx.tell(p, "bot.denied"); return; }
			Doombot bot = ModEntities.DOOMBOT.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
			if (bot == null) return;
			InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			ItemStack catalyst = p.getItemInHand(other);
			int role = role(catalyst);
			boolean forge = level.getBlockState(clicked).is(net.minecraft.tags.BlockTags.ANVIL);
			BlockPos at = forge ? clicked.above() : clicked.relative(face);
			bot.setup(role, -1);
			bot.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot() + 180, 0);
			level.addFreshEntity(bot);
			bot.obey(p);
			DoomFx.bill(level, ModParticles.DOOM_PHASE_SHIFT, bot.position().add(0, 1, 0), 1.6);
			if (forge) {
				DoomVfx.at(level, DoomVfx.IMPACT, Vec3.atCenterOf(clicked).add(0, 0.6, 0), 1.2f, 12, DoomVfx.GOLD);
				level.playSound(null, clicked, SoundEvents.ANVIL_USE, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 0.8f);
				level.playSound(null, clicked, SoundEvents.SMITHING_TABLE_USE, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 0.9f);
				bot.forge();
			}
			if (role != Doombot.SOLDIER && !p.isCreative()) catalyst.shrink(1);
			DoomFx.tell(p, forge ? "bot.forged" : "bot.built", bot.getName());
			if (!p.isCreative()) p.getItemInHand(hand).shrink(1);
		}
	}

	/** Doom's Gauntlet and Grimoire: one shot each without the armor, a straight bolt or a homing one. */
	public static final class Caster extends Item {
		private final boolean homing;

		public Caster(Properties properties, boolean homing) {
			super(properties);
			this.homing = homing;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
			ServerLevel sl = p.level();
			Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
			if (homing) {
				LivingEntity target = sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(20), e -> e != p && e.isAlive()
						&& (e instanceof net.minecraft.world.entity.monster.Enemy || e instanceof Doombot b && !b.allied())
						&& e.position().subtract(eye).normalize().dot(look) > 0.6).stream().findFirst().orElse(null);
				DoomCombat.fire(sl, p, eye.add(look), look.scale(0.7), 9, 0.35, 0, 100, target, 0.1, DoomVfx.GREEN, 0.36f, e -> hits(p, e));
				DoomFx.sound(sl, eye, SoundEvents.EVOKER_CAST_SPELL, 0.9f, 1.1f);
			} else {
				DoomCombat.fire(sl, p, eye.add(look.scale(0.8)), look.scale(2.2), 9, 0.3, 0, 40, null, 0, DoomVfx.GOLD, 0.26f, e -> hits(p, e));
				DoomFx.sound(sl, eye, SoundEvents.BLAZE_SHOOT, 0.8f, 1.3f);
			}
			p.getCooldowns().addCooldown(p.getItemInHand(hand), homing ? 50 : 36);
			return InteractionResult.SUCCESS;
		}

		private static boolean hits(ServerPlayer p, LivingEntity e) {
			return e != p && !(e instanceof Doombot b && b.allied()) && !(e instanceof net.minecraft.world.entity.npc.villager.AbstractVillager)
					&& (!(e instanceof ServerPlayer) || com.dantemorrigan.gauntlet.CoopRules.canAffect(p, e));
		}
	}

	/** Time Platform: a floor of stilled air under the user's feet for ten seconds. */
	public static final class Platform extends Item {
		public Platform(Properties properties) { super(properties); }

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
			ServerLevel sl = p.level();
			BlockPos c = p.blockPosition().below();
			List<BlockPos> placed = new java.util.ArrayList<>();
			for (BlockPos at : BlockPos.betweenClosed(c.offset(-1, 0, -1), c.offset(1, 0, 1))) {
				if (!sl.getBlockState(at).isAir()) continue;
				sl.setBlockAndUpdate(at, Blocks.PURPLE_STAINED_GLASS.defaultBlockState());
				placed.add(at.immutable());
			}
			if (placed.isEmpty()) return InteractionResult.SUCCESS;
			TimePlatforms.add(sl, placed, 200);
			DoomFx.flat(sl, ModParticles.DOOM_TIME_PLATFORM, Vec3.atBottomCenterOf(c.above()), 0.5);
			DoomFx.sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.6f);
			p.fallDistance = 0;
			p.getCooldowns().addCooldown(p.getItemInHand(hand), 300);
			return InteractionResult.SUCCESS;
		}
	}

	/** Blocks of Time Platforms still standing; they are taken away when their time runs out or the server stops. */
	public static final class TimePlatforms {
		private record Entry(ServerLevel level, List<BlockPos> blocks, long until) {}

		private static final List<Entry> LIVE = new java.util.ArrayList<>();

		static void add(ServerLevel level, List<BlockPos> blocks, int ticks) {
			LIVE.add(new Entry(level, blocks, level.getGameTime() + ticks));
		}

		public static void tick(boolean all) {
			LIVE.removeIf(e -> {
				if (!all && e.level().getGameTime() < e.until()) return false;
				Latveria.edit(() -> { for (BlockPos p : e.blocks()) if (e.level().getBlockState(p).is(Blocks.PURPLE_STAINED_GLASS)) e.level().setBlockAndUpdate(p, Blocks.AIR.defaultBlockState()); });
				return true;
			});
		}
	}

	private DoomItems() {}
}
