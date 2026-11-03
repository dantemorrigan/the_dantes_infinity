package com.dantemorrigan.gauntlet.uru;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Armor of Uru: a very strong set (more than netherite), and the only thing that holds against a FULL Infinity
 * Gauntlet. Attacks of a six-stone gauntlet go through {@link GauntletDamage} and ignore ordinary armor; a complete,
 * worn, unbroken Uru set takes only a quarter of such a hit and pays for it with durability. It never makes anyone
 * immortal: every hit still costs at least 1 HP, the runes wear out, and world events (snap, planet destruction...) are
 * not attacks and are not stopped by it.
 */
public final class UruArmor {
	/** Durability multiplier 60: helmet 660, chestplate 960, leggings 900, boots 780. */
	public static final ArmorMaterial MATERIAL = new ArmorMaterial(60,
			Map.of(ArmorType.HELMET, 4, ArmorType.CHESTPLATE, 9, ArmorType.LEGGINGS, 7, ArmorType.BOOTS, 4, ArmorType.BODY, 11),
			15, SoundEvents.ARMOR_EQUIP_NETHERITE, 4.0f, 0.1f,
			TagKey.create(Registries.ITEM, InfinityGauntlet.id("repairs_uru_armor")),
			ResourceKey.create(EquipmentAssets.ROOT_ID, InfinityGauntlet.id("uru")));

	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** Last guard flash per entity (game time), so a continuous beam does not flood the clients. */
	private static final Map<UUID, Long> LAST_FLASH = new WeakHashMap<>();

	private static Item piece(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> ModItems.URU_HELMET;
			case CHEST -> ModItems.URU_CHESTPLATE;
			case LEGS -> ModItems.URU_LEGGINGS;
			default -> ModItems.URU_BOOTS;
		};
	}

	/** All four Uru pieces worn in their own slots, none of them broken. Inventory or partial sets give nothing. */
	public static boolean fullSet(LivingEntity entity) {
		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = entity.getItemBySlot(slot);
			if (!stack.is(piece(slot)) || stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage()) return false;
		}
		return true;
	}

	/** After an accepted hit: each piece loses clamp(ceil(absorbed / 4), 1, 12) durability, and the runes flash. */
	public static void absorbed(ServerLevel level, LivingEntity wearer, float absorbed) {
		int wear = Mth.clamp(Mth.ceil(absorbed / 4f), 1, Math.max(1, GauntletConfig.get().uruMaxWearPerHit));
		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = wearer.getItemBySlot(slot);
			if (stack.is(piece(slot))) stack.hurtAndBreak(wear, wearer, slot);
		}
		long now = level.getGameTime();
		Long last = LAST_FLASH.get(wearer.getUUID());
		if (last != null && now - last < 8) return;
		LAST_FLASH.put(wearer.getUUID(), now);
		level.playSound(null, wearer.getX(), wearer.getY() + 1, wearer.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0f, 0.6f);
		level.playSound(null, wearer.getX(), wearer.getY() + 1, wearer.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 1.4f);
		ModNetwork.UruGuardFx fx = new ModNetwork.UruGuardFx(wearer.getId());
		for (ServerPlayer viewer : PlayerLookup.tracking(wearer)) ServerPlayNetworking.send(viewer, fx);
		if (wearer instanceof ServerPlayer self) ServerPlayNetworking.send(self, fx);
	}

	private UruArmor() {}
}
