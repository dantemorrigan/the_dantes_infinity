package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.ModAdvancements;
import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the Latverian Resistance Questline (Old Mechanic Radovan & the Under-Castle Generator Disruptor),
 * the Tavern Trader Mirko (Wheat for the Latverian Citizen Cloak), cloak stealth & exposure rules,
 * and post-victory Royal recognition from townsfolk.
 */
public final class LatveriaQuests {
	public static final int FLAG_CONQUERED = 1 << 0;
	public static final int FLAG_HAS_DISRUPTOR = 1 << 1;
	public static final int FLAG_GENERATOR_OFF = 1 << 2;
	public static final int FLAG_HAS_CLOAK = 1 << 3;
	public static final int FLAG_HAS_WHEAT = 1 << 4;

	public static final int ACTION_CLAIM_DISRUPTOR = 1;
	public static final int ACTION_TRADE_WHEAT_FOR_CLOAK = 2;
	public static final int ACTION_CITIZEN_BLESSING = 3;
	public static final int ACTION_TALK_SOUND = 4;

	public static final int WHEAT_COST = 8;

	/** Fixed positions in Latveria for key NPCs. */
	public static final BlockPos MECHANIC_POS = new BlockPos(-29, LatveriaPlan.VALLEY_Y + 1, 16);
	public static final BlockPos TAVERN_KEEPER_POS = new BlockPos(28, LatveriaPlan.VALLEY_Y + 1, 13);

	/** Center of the huge generator chamber deep beneath Castle Doom. */
	public static final BlockPos UNDER_CASTLE_GEN = new BlockPos(0, LatveriaPlan.CASTLE_Y - 11, -242);
	public static final AABB UNDER_CASTLE_GEN_BOX = new AABB(-8, LatveriaPlan.CASTLE_Y - 14, -250, 9, LatveriaPlan.CASTLE_Y - 4, -234);

	private static final Map<UUID, Long> CLOAK_EXPOSED_UNTIL = new HashMap<>();
	private static boolean npcsChecked;

	public static void init() {
		// Attacking anything in Latveria while wearing the cloak breaks order and exposes the player.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!world.isClientSide() && world instanceof ServerLevel level && level.dimension() == Latveria.LEVEL
					&& player instanceof ServerPlayer sp && !sp.isCreative() && !sp.isSpectator()) {
				if (entity instanceof Doombot || entity instanceof Villager || entity instanceof LatveriaNpc) {
					if (wearingCloak(sp)) {
						exposeCloak(level, sp, entity.position());
					}
				}
			}
			return InteractionResult.PASS;
		});

		// Right-clicking vanilla Latverian villagers opens the Latverian citizen dialog with voice murmurs.
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (hand == InteractionHand.MAIN_HAND && world instanceof ServerLevel level && level.dimension() == Latveria.LEVEL
					&& entity instanceof Villager villager && player instanceof ServerPlayer sp) {
				boolean conquered = Latveria.conquered(level.getServer());
				float pitch = 0.92f + (level.getRandom().nextFloat() - 0.5f) * 0.16f;
				level.playSound(null, villager.getX(), villager.getY(), villager.getZ(),
						conquered ? SoundEvents.VILLAGER_CELEBRATE : SoundEvents.VILLAGER_AMBIENT,
						SoundSource.NEUTRAL, 0.85f, pitch);
				level.playSound(null, villager.getX(), villager.getY(), villager.getZ(),
						SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 0.45f, pitch + 0.08f);
				int role = LatveriaNpc.ROLE_CITIZEN_1 + (Math.abs(villager.getUUID().hashCode()) % 4);
				ServerPlayNetworking.send(sp, new ModNetwork.LatveriaDialogOpen(villager.getId(), role, dialogFlagsFor(sp, null)));
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.PASS;
		});
	}

	public static void resetRuntime() {
		CLOAK_EXPOSED_UNTIL.clear();
		npcsChecked = false;
	}

	/** Ensures the Old Mechanic, Tavern Keeper, and player-skinned Latverian citizens are present in the city. */
	public static void ensureNpcs(ServerLevel level) {
		if (level.dimension() != Latveria.LEVEL) return;
		if (npcsChecked && level.getGameTime() % 100 != 0) return;
		if (level.players().isEmpty()) return;
		npcsChecked = true;

		AABB cityBox = new AABB(-120, 40, -60, 120, 130, 160);
		List<LatveriaNpc> existing = level.getEntitiesOfClass(LatveriaNpc.class, cityBox);
		boolean hasMechanic = false;
		boolean hasTavern = false;
		int citizenCount = 0;
		for (LatveriaNpc npc : existing) {
			if (npc.role() == LatveriaNpc.ROLE_MECHANIC) hasMechanic = true;
			else if (npc.role() == LatveriaNpc.ROLE_TAVERN_KEEPER) hasTavern = true;
			else citizenCount++;
		}

		if (!hasMechanic) {
			level.getChunk(MECHANIC_POS);
			spawnNpc(level, LatveriaNpc.ROLE_MECHANIC, MECHANIC_POS, 90f);
		}
		if (!hasTavern) {
			level.getChunk(TAVERN_KEEPER_POS);
			spawnNpc(level, LatveriaNpc.ROLE_TAVERN_KEEPER, TAVERN_KEEPER_POS, -90f);
		}
		if (citizenCount < 10) {
			BlockPos[] spots = {
					new BlockPos(-8, LatveriaPlan.VALLEY_Y + 1, 24),
					new BlockPos(10, LatveriaPlan.VALLEY_Y + 1, 34),
					new BlockPos(-14, LatveriaPlan.VALLEY_Y + 1, 46),
					new BlockPos(14, LatveriaPlan.VALLEY_Y + 1, 52),
					new BlockPos(0, LatveriaPlan.VALLEY_Y + 1, 68),
					new BlockPos(-36, LatveriaPlan.VALLEY_Y + 1, 28),
					new BlockPos(36, LatveriaPlan.VALLEY_Y + 1, 32),
					new BlockPos(-36, LatveriaPlan.VALLEY_Y + 1, 70),
					new BlockPos(36, LatveriaPlan.VALLEY_Y + 1, 74),
					new BlockPos(18, LatveriaPlan.VALLEY_Y + 1, 25),
					new BlockPos(-20, LatveriaPlan.VALLEY_Y + 1, 14),
					new BlockPos(4, LatveriaPlan.VALLEY_Y + 1, 14)
			};
			for (int i = citizenCount; i < spots.length; i++) {
				BlockPos pos = spots[i];
				level.getChunk(pos);
				int role = LatveriaNpc.ROLE_CITIZEN_1 + (i % 4);
				spawnNpc(level, role, pos, (i * 73) % 360);
			}
		}
	}

	private static void spawnNpc(ServerLevel level, int role, BlockPos pos, float yaw) {
		LatveriaNpc npc = ModEntities.LATVERIA_NPC.create(level, EntitySpawnReason.STRUCTURE);
		if (npc == null) return;
		npc.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
		npc.setup(role, pos);
		level.addFreshEntity(npc);
	}

	public static int dialogFlagsFor(ServerPlayer player, LatveriaNpc npc) {
		int flags = 0;
		var server = player.level().getServer();
		if (Latveria.conquered(server)) flags |= FLAG_CONQUERED;
		if (Latveria.generatorOff(server)) flags |= FLAG_GENERATOR_OFF;
		if (hasItem(player, ModItems.GENERATOR_DISRUPTOR) || Latveria.generatorOff(server)) flags |= FLAG_HAS_DISRUPTOR;
		if (hasItem(player, ModItems.LATVERIA_CLOAK) || wearingCloak(player)) flags |= FLAG_HAS_CLOAK;
		if (player.isCreative() || player.getInventory().countItem(Items.WHEAT) >= WHEAT_COST) flags |= FLAG_HAS_WHEAT;
		return flags;
	}

	private static boolean hasItem(ServerPlayer player, net.minecraft.world.item.Item item) {
		if (player.getOffhandItem().is(item) || player.getMainHandItem().is(item)) return true;
		return player.getInventory().countItem(item) > 0;
	}

	public static void handleDialogAction(ServerPlayer player, int entityId, int actionId) {
		if (!(player.level() instanceof ServerLevel level)) return;
		Entity entity = level.getEntity(entityId);
		if (entity != null && entity.distanceToSqr(player) > 144.0) return;

		if (entity instanceof LatveriaNpc npc) {
			npc.triggerTalk(30);
			npc.playVoiceMurmur(level, actionId != ACTION_TALK_SOUND);
		} else if (entity instanceof Villager v) {
			level.playSound(null, v.getX(), v.getY(), v.getZ(),
					actionId == ACTION_CITIZEN_BLESSING ? SoundEvents.VILLAGER_CELEBRATE : SoundEvents.VILLAGER_YES,
					SoundSource.NEUTRAL, 0.85f, 0.95f + (level.getRandom().nextFloat() - 0.5f) * 0.1f);
		}

		switch (actionId) {
			case ACTION_CLAIM_DISRUPTOR -> {
				if (!hasItem(player, ModItems.GENERATOR_DISRUPTOR)) {
					ItemStack disruptor = new ItemStack(ModItems.GENERATOR_DISRUPTOR);
					if (!player.getInventory().add(disruptor)) player.drop(disruptor, false);
				}
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.65f, 1.35f);
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.9f, 0.78f);
				player.sendSystemMessage(Component.translatable("msg.infinity_gauntlet.latveria.quest.disruptor_received"));
				ServerPlayNetworking.send(player, new ModNetwork.LatveriaDialogOpen(entityId, LatveriaNpc.ROLE_MECHANIC, dialogFlagsFor(player, null)));
			}
			case ACTION_TRADE_WHEAT_FOR_CLOAK -> {
				int count = player.getInventory().countItem(Items.WHEAT);
				if (!player.isCreative() && count < WHEAT_COST) {
					player.sendSystemMessage(Component.translatable("msg.infinity_gauntlet.latveria.quest.need_wheat"), true);
					return;
				}
				if (!player.isCreative()) {
					removeItems(player, Items.WHEAT, WHEAT_COST);
				}
				ItemStack cloak = new ItemStack(ModItems.LATVERIA_CLOAK);
				if (player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
					player.setItemSlot(EquipmentSlot.CHEST, cloak);
				} else if (!player.getInventory().add(cloak)) {
					player.drop(cloak, false);
				}
				ItemStack ale = new ItemStack(Items.HONEY_BOTTLE, 2);
				ale.set(DataComponents.CUSTOM_NAME, Component.translatable("item.infinity_gauntlet.latveria_ale"));
				if (!player.getInventory().add(ale)) player.drop(ale, false);

				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.9f, 1.0f);
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.9f, 0.88f);
				player.sendSystemMessage(Component.translatable("msg.infinity_gauntlet.latveria.quest.cloak_received"));
				ServerPlayNetworking.send(player, new ModNetwork.LatveriaDialogOpen(entityId, LatveriaNpc.ROLE_TAVERN_KEEPER, dialogFlagsFor(player, null)));
			}
			case ACTION_CITIZEN_BLESSING -> {
				if (Latveria.conquered(level.getServer())) {
					player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 30, 0, true, true));
					player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 20 * 120, 0, true, true));
					if (entity != null) {
						level.sendParticles(ParticleTypes.HAPPY_VILLAGER, entity.getX(), entity.getY() + 1.8, entity.getZ(), 6, 0.3, 0.25, 0.3, 0.02);
					}
					player.sendSystemMessage(Component.translatable("msg.infinity_gauntlet.latveria.quest.king_blessing"), true);
				}
			}
			default -> {
			}
		}
	}

	private static void removeItems(ServerPlayer player, net.minecraft.world.item.Item item, int amount) {
		int left = amount;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item)) {
				int take = Math.min(left, s.getCount());
				s.shrink(take);
				left -= take;
			}
		}
	}

	// ------------------------------------------------------------------ Latverian Citizen Cloak stealth & exposure

	public static boolean wearingCloak(Player player) {
		return player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.LATVERIA_CLOAK);
	}

	/** True when the player is wearing the Latverian Citizen Cloak and has not blown their cover. */
	public static boolean isCloakActive(ServerLevel level, ServerPlayer player) {
		if (!wearingCloak(player)) return false;
		long until = CLOAK_EXPOSED_UNTIL.getOrDefault(player.getUUID(), 0L);
		return level.getGameTime() >= until;
	}

	public static void exposeCloak(ServerLevel level, ServerPlayer player, net.minecraft.world.phys.Vec3 spot) {
		long now = level.getGameTime();
		long prev = CLOAK_EXPOSED_UNTIL.getOrDefault(player.getUUID(), 0L);
		CLOAK_EXPOSED_UNTIL.put(player.getUUID(), now + 20 * 25);
		if (now >= prev) {
			player.sendSystemMessage(Component.translatable("msg.infinity_gauntlet.latveria.cloak.exposed_close"), true);
			player.sendSystemMessage(Component.translatable("msg.infinity_gauntlet.latveria.cloak.exposed_close"));
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.0f, 1.25f);
		}
		if (!Latveria.conquered(level.getServer()) && !Latveria.generatorOff(level.getServer())) {
			LatveriaHunt.launch(level, player);
			LatveriaHunt.attacked(level, player);
		}
	}

	/**
	 * Checks if a player wearing the Latverian Cloak gets too close to any active Doombot (< 3.6 blocks)
	 * or sprints noisily right in front of one (< 6.5 blocks). If so, the Doombot sees through the disguise!
	 */
	public static boolean checkCloakProximity(ServerLevel level, ServerPlayer player) {
		if (!isCloakActive(level, player)) return false;
		if (Latveria.conquered(level.getServer()) || Latveria.generatorOff(level.getServer())) return false;
		List<Doombot> nearby = level.getEntitiesOfClass(Doombot.class, player.getBoundingBox().inflate(7.0),
				b -> b.isAlive() && !b.allied() && !b.dormant());
		for (Doombot bot : nearby) {
			double dist = bot.distanceTo(player);
			boolean tooClose = dist < 3.6 && bot.hasLineOfSight(player);
			boolean recklessNear = dist < 6.5 && player.isSprinting() && !player.isShiftKeyDown() && bot.hasLineOfSight(player);
			if (tooClose || recklessNear) {
				exposeCloak(level, player, bot.position());
				bot.setTarget(player);
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ Under-Castle Generator Sabotage

	public static boolean isGeneratorBlock(BlockPos pos) {
		return pos.distManhattan(LatveriaPlan.GENERATOR) <= 3 || UNDER_CASTLE_GEN_BOX.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
	}

	/**
	 * Attaches the Old Mechanic's Generator Disruptor to the under-castle generator (or lab generator),
	 * shutting down all castle defenses, searchlights, gates, and powering down all castle guard Doombots!
	 */
	public static boolean attachDisruptor(ServerPlayer player, BlockPos pos) {
		if (!(player.level() instanceof ServerLevel level) || level.dimension() != Latveria.LEVEL) return false;
		if (!isGeneratorBlock(pos)) return false;
		if (Latveria.generatorOff(level.getServer())) {
			player.sendSystemMessage(Component.translatable("msg.infinity_gauntlet.latveria.generator.already"), true);
			return true;
		}
		ItemStack main = player.getMainHandItem();
		ItemStack off = player.getOffhandItem();
		boolean usedDisruptor = main.is(ModItems.GENERATOR_DISRUPTOR) || off.is(ModItems.GENERATOR_DISRUPTOR);
		if (usedDisruptor && !player.isCreative()) {
			if (main.is(ModItems.GENERATOR_DISRUPTOR)) main.shrink(1);
			else off.shrink(1);
		}

		// Shut down the generator & open all castle gates/doors so the path to Doom's throne room is wide open.
		Latveria.setGenerator(level, true);
		Latveria.openGates(level);
		LatveriaHunt.clear();

		// Power down all guarding Doombots across the castle and road into dormant grey statues!
		AABB castleAndRoad = new AABB(-200, 0, -400, 200, 260, 200);
		for (Doombot bot : level.getEntitiesOfClass(Doombot.class, castleAndRoad, b -> b.isAlive() && !b.allied())) {
			bot.becomeStatue(null);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, bot.getX(), bot.getY() + 1.2, bot.getZ(), 18, 0.35, 0.5, 0.35, 0.08);
		}

		// Visual & audio overload cascade in the generator chamber
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 65, 1.8, 1.5, 1.8, 0.15);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 30, 1.2, 1.0, 1.2, 0.04);
		level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.6f, 0.65f);
		level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 1.2f, 0.75f);

		ModAdvancements.award(player, "sabotage_generator");
		Component msg = Component.translatable(usedDisruptor
				? "msg.infinity_gauntlet.latveria.generator.sabotaged_full"
				: "msg.infinity_gauntlet.latveria.generator.off");
		for (ServerPlayer p : level.players()) {
			p.sendSystemMessage(msg, true);
			p.sendSystemMessage(msg);
		}
		return true;
	}

	private LatveriaQuests() {}
}
