package com.dantemorrigan.gauntlet.stormbreaker;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class StormbreakerEntity extends Entity {
    private static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(StormbreakerEntity.class, EntityDataSerializers.INT);
    private int phaseStarted;
    public static final int OUTBOUND = 0, RETURNING = 1, EMBEDDED = 2, PLANTING = 3, SKYFALL = 4, PULLING = 5,
            EMBEDDED_BLADE = 6, PULLING_BLADE = 7;

    public StormbreakerEntity(EntityType<? extends StormbreakerEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    void phase(int phase) {
        entityData.set(PHASE, phase);

    }

    public float phaseAge(float partial) {
        return Math.max(0, tickCount - phaseStarted + partial);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor == PHASE) phaseStarted = tickCount;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PHASE, OUTBOUND);
    }

    public boolean embedded() {
        return phase() == EMBEDDED || phase() == EMBEDDED_BLADE;
    }

    @Override
    public boolean isPickable() {
        return embedded();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, net.minecraft.world.phys.Vec3 hit) {
        if (!embedded()) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) Stormbreaker.reclaim(serverPlayer, this);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    // The world's Stormbreaker record owns persistence. This entity is only its loaded representation.
    @Override protected void readAdditionalSaveData(ValueInput input) {}
    @Override protected void addAdditionalSaveData(ValueOutput output) {}
}
