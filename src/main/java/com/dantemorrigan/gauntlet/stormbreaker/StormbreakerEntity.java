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
    public static final int OUTBOUND = 0, RETURNING = 1, EMBEDDED = 2, PLANTING = 3;

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

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PHASE, OUTBOUND);
    }

    @Override
    public boolean isPickable() {
        return phase() == EMBEDDED;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, net.minecraft.world.phys.Vec3 hit) {
        if (phase() != EMBEDDED) return InteractionResult.PASS;
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
