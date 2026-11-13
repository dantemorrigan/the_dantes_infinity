package com.dantemorrigan.gauntlet;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Passive effects of the gauntlet. An EMPTY gauntlet gives nothing.
 *
 * <ul>
 *   <li>In inventory (any slot) with at least one stone: small max health, slow regen, poison immunity.</li>
 *   <li>Worn (off hand): each inserted stone adds its own passive.</li>
 *   <li>Worn with all six: awakening ritual (1 heart, killable for a moment), then near-invulnerability.</li>
 * </ul>
 */
public final class GauntletEffects {
	// ---- Tuning ----
	static final double INV_HEALTH = 4;           // +2 hearts
	static final int INV_REGEN_INTERVAL = 100;    // 1 HP every 5 s
	static final double POWER_DAMAGE = 12;        // attack damage
	static final double SPACE_SPEED = 0.6;        // +60% movement speed
	static final double SOUL_HEALTH = 20;         // +10 hearts
	static final double REALITY_ARMOR = 16;
	static final double REALITY_TOUGHNESS = 10;
	static final double FULL_HEALTH = 180;        // total extra max health when awakened (~100 hearts)
	static final float FULL_REGEN_PER_TICK = 2;
	static final int RITUAL_TICKS = 60;           // 3 s of vulnerability

	private static final Identifier HEALTH_ID = InfinityGauntlet.id("gauntlet_health");
	private static final Identifier DAMAGE_ID = InfinityGauntlet.id("gauntlet_damage");
	private static final Identifier SPEED_ID = InfinityGauntlet.id("gauntlet_speed");
	private static final Identifier ARMOR_ID = InfinityGauntlet.id("gauntlet_armor");
	private static final Identifier TOUGHNESS_ID = InfinityGauntlet.id("gauntlet_toughness");

	public enum Phase { NONE, RITUAL, AWAKENED }

	/** Per-player state, recomputed every server tick. */
	public static final class State {
		public Set<Stone> worn = EnumSet.noneOf(Stone.class);
		public boolean inventoryBonus;
		public Phase phase = Phase.NONE;
		int ritualEnd;
	}

	private static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Boolean> AWAKENED =
        net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.<Boolean>builder().persistent(com.mojang.serialization.Codec.BOOL)
            .buildAndRegister(InfinityGauntlet.id("gauntlet_awakened"));

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final State NO_STATE = new State();

	public static State state(Player player) {
		return STATES.getOrDefault(player.getUUID(), NO_STATE);
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(GauntletEffects::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server->STATES.clear());
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && state(player).phase == Phase.AWAKENED) {
				if (source.is(com.dantemorrigan.gauntlet.stormbreaker.Stormbreaker.DAMAGE)) return true;
				// Doom's science and sorcery are a match for the stones: he and his robots can still hurt the bearer.
				if (source.getEntity() instanceof com.dantemorrigan.gauntlet.doom.DoctorDoom
						|| source.getEntity() instanceof com.dantemorrigan.gauntlet.doom.Doombot bot && !bot.allied()) return true;
				return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY); // only /kill, void
			}
			return true;
		});
	}

	// ---- Queries used by mixins ----

	/** Should this effect be blocked for the player? */
	public static boolean blocksEffect(Player player, MobEffectInstance effect) {
		State s = state(player);
		Holder<MobEffect> e = effect.getEffect();
		if (s.phase == Phase.AWAKENED && e.value().getCategory() == MobEffectCategory.HARMFUL) return true;
		if (s.inventoryBonus && s.phase != Phase.RITUAL && e.is(MobEffects.POISON)) return true;
		if (hasPassive(player, Stone.MIND)
				&& (e.is(MobEffects.BLINDNESS) || e.is(MobEffects.DARKNESS) || e.is(MobEffects.NAUSEA))) return true;
		return false;
	}

	/** Worn stone passive is active (not during the vulnerable ritual window). */
	public static boolean hasPassive(Player player, Stone stone) {
		State s = state(player);
		return s.phase != Phase.RITUAL && s.worn.contains(stone);
	}

	// ---- Tick ----

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		Set<UUID> online = new java.util.HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			online.add(player.getUUID());
			State s = STATES.computeIfAbsent(player.getUUID(), id -> new State());
			update(player, s, now);
		}
		STATES.keySet().retainAll(online);
	}

	private static void update(ServerPlayer player, State s, int now) {
		ItemStack worn = GauntletState.activeGauntlet(player);
		s.worn = worn.isEmpty() ? EnumSet.noneOf(Stone.class) : GauntletState.inserted(worn);
		s.inventoryBonus = player.getInventory().contains(st -> st.is(ModItems.GAUNTLET) && !GauntletState.inserted(st).isEmpty());
		if (!s.worn.isEmpty() && now % 20 == 0) {
			ModAdvancements.award(player, "craft_gauntlet");
			for (Stone st : s.worn) ModAdvancements.award(player, st.name().toLowerCase(java.util.Locale.ROOT) + "_stone");
		}

		boolean full = s.worn.size() == Stone.values().length;
		if (!full) {
            if(!player.hasAttached(AWAKENED) || Boolean.TRUE.equals(player.getAttached(AWAKENED)))player.setAttached(AWAKENED,false);
			if (s.phase != Phase.NONE && !worn.isEmpty()) GauntletState.setInUse(worn, EnumSet.noneOf(Stone.class));
			s.phase = Phase.NONE;
		} else if (s.phase == Phase.NONE) {
            // Older saves migrate from the completed awakening advancement once; unequipping writes false.
            boolean saved=Boolean.TRUE.equals(player.getAttached(AWAKENED)) || !player.hasAttached(AWAKENED) && ModAdvancements.completed(player,"awaken_gauntlet");
            if(saved)restoreAwakened(player,player.getHealth());else startRitual(player,s,worn,now);
		} else if (s.phase == Phase.RITUAL) {
			ritualTick(player, now);
			if (now >= s.ritualEnd) awaken(player, s, worn);
		}

		applyAttributes(player, s);

		boolean active = s.phase != Phase.RITUAL;
		// Regeneration
		if (s.phase == Phase.AWAKENED && !com.dantemorrigan.gauntlet.boss.TribunalEncounter.inTrial(player)) {
			player.heal(FULL_REGEN_PER_TICK);
		} else if (active && s.inventoryBonus && !com.dantemorrigan.gauntlet.boss.TribunalEncounter.inTrial(player) && now % INV_REGEN_INTERVAL == 0) {
			player.heal(1);
		}
		// Cleansing
		if (active) {
			List<Holder<MobEffect>> remove = new ArrayList<>();
			for (MobEffectInstance e : player.getActiveEffects()) {
				if (blocksEffect(player, e)) remove.add(e.getEffect());
			}
			remove.forEach(player::removeEffect);
		}
		// Time stone: no hunger
		if (hasPassive(player, Stone.TIME)) {
			player.getFoodData().setFoodLevel(20);
			player.getFoodData().setSaturation(5);
		}
	}

	private static void applyAttributes(ServerPlayer player, State s) {
		boolean active = s.phase != Phase.RITUAL;
		double health = 0, damage = 0, speed = 0, armor = 0, toughness = 0;
		if (active) {
			if (s.inventoryBonus) health += INV_HEALTH;
			if (s.worn.contains(Stone.SOUL)) health += SOUL_HEALTH;
			if (s.worn.contains(Stone.POWER)) damage += POWER_DAMAGE;
			if (s.worn.contains(Stone.SPACE)) speed += SPACE_SPEED;
			if (s.worn.contains(Stone.REALITY)) { armor += REALITY_ARMOR; toughness += REALITY_TOUGHNESS; }
			if (s.phase == Phase.AWAKENED) health = FULL_HEALTH;
		}
		setModifier(player, Attributes.MAX_HEALTH, HEALTH_ID, health, AttributeModifier.Operation.ADD_VALUE);
		setModifier(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE);
		setModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, speed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		setModifier(player, Attributes.ARMOR, ARMOR_ID, armor, AttributeModifier.Operation.ADD_VALUE);
		setModifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, toughness, AttributeModifier.Operation.ADD_VALUE);
		if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
	}

	private static void setModifier(ServerPlayer player, Holder<Attribute> attr, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance inst = player.getAttribute(attr);
		if (inst == null) return;
		AttributeModifier current = inst.getModifier(id);
		if (amount == 0) {
			if (current != null) inst.removeModifier(id);
		} else if (current == null || current.amount() != amount) {
			inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	// ---- Awakening ritual ----

	private static void startRitual(ServerPlayer player, State s, ItemStack worn, int now) {
		s.phase = Phase.RITUAL;
		s.ritualEnd = now + RITUAL_TICKS;
		GauntletState.setInUse(worn, EnumSet.allOf(Stone.class));
		ServerLevel level = player.level();
		if (!player.isCreative() && !player.isSpectator()) {
			player.setHealth(Math.min(2, player.getHealth())); // 1 heart
		}
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(player.getX(), player.getY(), player.getZ());
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.0F, 1.4F);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);
	}

	private static void ritualTick(ServerPlayer player, int now) {
		ServerLevel level = player.level();
		double t = now * 0.3;
		Stone[] all = Stone.values();
		for (int i = 0; i < all.length; i++) {
			double a = t + i * (Math.PI * 2 / all.length);
			level.sendParticles(new DustParticleOptions(all[i].color, 1.5F),
					player.getX() + Math.cos(a) * 1.2, player.getY() + 1.0, player.getZ() + Math.sin(a) * 1.2,
					2, 0.05, 0.3, 0.05, 0);
		}
	}

	/** Restore persisted awakening or a Tribunal checkpoint without replaying the one-heart ritual. */
	public static void restoreAwakened(ServerPlayer player, float health) {
        player.setAttached(AWAKENED,true);
		State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
		state.worn = GauntletState.inserted(GauntletState.activeGauntlet(player));
		state.inventoryBonus = true;
		state.phase = Phase.AWAKENED;
		applyAttributes(player, state);
		player.setHealth(Math.min(health, player.getMaxHealth()));
	}

	private static void awaken(ServerPlayer player, State s, ItemStack worn) {
        player.setAttached(AWAKENED,true);
		s.phase = Phase.AWAKENED;
		ModAdvancements.award(player, "awaken_gauntlet");
		GauntletState.setInUse(worn, EnumSet.noneOf(Stone.class));
		applyAttributes(player, s);
		player.setHealth(player.getMaxHealth());
		player.removeAllEffects();
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 120, 0.6, 1.0, 0.6, 0.6);
	}

	private GauntletEffects() {}
}
