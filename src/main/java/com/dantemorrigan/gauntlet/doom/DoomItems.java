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

	/** Mechanical Heart: the castle's owner builds a new Soldier where the heart is set down. */
	public static final class Heart extends Item {
		public Heart(Properties properties) { super(properties); }

		@Override
		public InteractionResult useOn(UseOnContext context) {
			if (!(context.getPlayer() instanceof ServerPlayer p) || !(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
			if (!Latveria.isOwner(p)) { DoomFx.tell(p, "bot.denied"); return InteractionResult.SUCCESS; }
			Doombot bot = ModEntities.DOOMBOT.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
			if (bot == null) return InteractionResult.FAIL;
			BlockPos at = context.getClickedPos().relative(context.getClickedFace());
			bot.setup(Doombot.SOLDIER, -1);
			bot.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot() + 180, 0);
			level.addFreshEntity(bot);
			bot.obey(p);
			DoomFx.bill(level, ModParticles.DOOM_PHASE_SHIFT, bot.position().add(0, 1, 0), 1.6);
			context.getItemInHand().shrink(1);
			return InteractionResult.SUCCESS;
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
