package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.doom.LatveriaNpc;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Red Skull, keeper of the Soul Stone: a levitating figure on the summit of the Temple of Soul who will talk to
 * whoever comes. He is scenery with a voice: untouchable, motionless, and gone (he departs, fading) once the stone has
 * been taken.
 */
public final class RedSkull extends Mob {
	private static final EntityDataAccessor<Integer> DEPART_AT = SynchedEntityData.defineId(RedSkull.class, EntityDataSerializers.INT);
	/** Client tick of the last word he spoke: the "guide" gesture plays for a few seconds after it. */
	private static final EntityDataAccessor<Integer> TALKED_AT = SynchedEntityData.defineId(RedSkull.class, EntityDataSerializers.INT);
	public static final int DEPART_TICKS = 46, LINES = 9;
	private static final Map<UUID, Integer> SPOKEN = new HashMap<>();

	public RedSkull(EntityType<? extends RedSkull> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.setNoGravity(true);
		this.setInvulnerable(true);
		this.setNoAi(true);
		this.setCustomName(LatveriaNpc.fancy("entity.infinity_gauntlet.red_skull", ChatFormatting.DARK_RED));
		this.setCustomNameVisible(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DEPART_AT, -1);
		builder.define(TALKED_AT, -10000);
	}

	public boolean departing() { return this.entityData.get(DEPART_AT) >= 0; }

	/** Ticks since he began to leave (0 when he is not leaving). */
	public int departAge() { return departing() ? Math.max(0, this.tickCount - this.entityData.get(DEPART_AT)) : 0; }

	/** Ticks since he last spoke. */
	public int sinceTalk() { return this.tickCount - this.entityData.get(TALKED_AT); }

	public void depart() {
		if (departing()) return;
		this.entityData.set(DEPART_AT, this.tickCount);
		if (this.level() instanceof ServerLevel level) {
			level.playSound(null, this.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.NEUTRAL, 2.0f, 0.6f);
			SPOKEN.values().removeIf(v -> true);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide() && departing() && departAge() >= DEPART_TICKS) this.discard();
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
		if (this.level().isClientSide()) return InteractionResult.SUCCESS;
		if (departing() || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
		int line = SPOKEN.merge(sp.getUUID(), 1, Integer::sum);
		int index = (line - 1) % LINES + 1;
		// He turns to whoever speaks to him.
		double dx = sp.getX() - this.getX(), dz = sp.getZ() - this.getZ();
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		this.setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
		this.entityData.set(TALKED_AT, this.tickCount);
		Component name = Component.translatable("entity.infinity_gauntlet.red_skull").withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_RED);
		sp.sendSystemMessage(Component.literal("").append(name).append(Component.literal(": ").withStyle(ChatFormatting.DARK_RED))
				.append(Component.translatable("dialog.infinity_gauntlet.red_skull." + index).withStyle(ChatFormatting.GRAY)));
		((ServerLevel) this.level()).playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 0.8f, 0.5f);
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean isPushable() { return false; }

	@Override
	public boolean removeWhenFarAway(double distance) { return false; }

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// Only commands and the void remove him; nothing else touches him.
		return super.hurtServer(level, source, amount);
	}

	public static void clearTalk() { SPOKEN.clear(); }
}
