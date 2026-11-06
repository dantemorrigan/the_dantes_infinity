package com.dantemorrigan.gauntlet.boss;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class LivingTribunal extends Monster {
    private static final net.minecraft.server.level.TicketType TICKETING=net.minecraft.core.Registry.register(
        net.minecraft.core.registries.BuiltInRegistries.TICKET_TYPE,com.dantemorrigan.gauntlet.InfinityGauntlet.id("tribunal_scene"),
        new net.minecraft.server.level.TicketType(120,net.minecraft.server.level.TicketType.FLAG_LOADING|net.minecraft.server.level.TicketType.FLAG_SIMULATION));
    private net.minecraft.world.level.ChunkPos activeChunk;
    /** Keep only the giant's local chunk neighbourhood active while he moves beyond the player's ticking edge. */
    public void keepActiveAt(Vec3 position) {
        if(!(level() instanceof ServerLevel level)) return;
        var block=net.minecraft.core.BlockPos.containing(position);
        var next=new net.minecraft.world.level.ChunkPos(block.getX()>>4,block.getZ()>>4);
        if(activeChunk!=null && !activeChunk.equals(next)) level.getChunkSource().removeTicketWithRadius(TICKETING,activeChunk,2);
        level.getChunkSource().addTicketWithRadius(TICKETING,next,2);activeChunk=next;
    }
	public static final int ARRIVAL = 0, BATTLE = 1, VERDICT = 2, DEPARTURE = 3;
	public static final int EDICT=1, GRAVITY=2, NOVA=3, VOLLEY=4, WAVE=5, LANCE=6, PRISON=7;
	public static final int INTRO_TICKS = 180, VERDICT_TICKS = 160, SHATTER_TICK = 105, WINDUP = 34;
	private static final EntityDataAccessor<Integer> CHALLENGER = SynchedEntityData.defineId(LivingTribunal.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(LivingTribunal.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(LivingTribunal.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(LivingTribunal.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Vector3fc> MARK = SynchedEntityData.defineId(LivingTribunal.class, EntityDataSerializers.VECTOR3);
	private final net.minecraft.server.level.ServerBossEvent bar = new net.minecraft.server.level.ServerBossEvent(
			java.util.UUID.randomUUID(), net.minecraft.network.chat.Component.translatable("entity.infinity_gauntlet.living_tribunal"),
			net.minecraft.world.BossEvent.BossBarColor.PURPLE, net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_12);
	int attackCycle;
	int nextAttack = 18;
	long exposedUntil;
    private boolean gauntletHit;
    private long damageWindow=-20;
    private float windowDamage;
    public static final int DEATH_TICKS=100, EXPLOSION_TICK=55;
    public boolean hurtByGauntlet(ServerLevel level,ServerPlayer owner,float amount) {
        gauntletHit=true;try{return hurtServer(level,level.damageSources().playerAttack(owner),amount);}finally{gauntletHit=false;}
    }

	public LivingTribunal(EntityType<? extends LivingTribunal> type, Level level) {
		super(type, level);
		setNoGravity(true);
		noPhysics = true;
		setPersistenceRequired();
		setNoAi(true);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 1024)
				.add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.KNOCKBACK_RESISTANCE, 1);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(CHALLENGER, -1).define(PHASE, ARRIVAL).define(AGE, 0).define(ATTACK, 0).define(MARK, new Vector3f());
	}

	public int challenger() { return entityData.get(CHALLENGER); }
	void challenger(int id) { entityData.set(CHALLENGER, id); }
	public int phase() { return entityData.get(PHASE); }
	public int sceneAge() { return entityData.get(AGE); }
	public int attack() { return entityData.get(ATTACK); }
	public Vec3 mark() { return new Vec3(entityData.get(MARK)); }
	void phase(int value) { entityData.set(PHASE, value); age(0); attack(0, Vec3.ZERO); }
	void age(int value) { entityData.set(AGE, value); }
	void attack(int value, Vec3 mark) {
		entityData.set(ATTACK, value);
		entityData.set(MARK, mark.toVector3f());
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		bar.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bar.removePlayer(player);
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel) bar.setProgress(getHealth() / getMaxHealth());
		setDeltaMovement(Vec3.ZERO);
		if(!isRemoved()) keepActiveAt(position());
	}

	@Override public void remove(RemovalReason reason) {
        if(activeChunk!=null && level() instanceof ServerLevel level) level.getChunkSource().removeTicketWithRadius(TICKETING,activeChunk,2);
        activeChunk=null;bar.removeAllPlayers();super.remove(reason);
    }
	@Override public boolean removeWhenFarAway(double distance) { return false; }
	@Override public boolean isPushable() { return false; }
	@Override public boolean canUsePortal(boolean passengers) { return false; }

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (phase() != BATTLE || !(source.getEntity() instanceof ServerPlayer player)
				|| !TribunalEncounter.isChallenger(this, player) || !(amount > 0) || !Float.isFinite(amount)) return false;
		boolean exposed=level.getGameTime()<exposedUntil;
        if(level.getGameTime()-damageWindow>=20){damageWindow=level.getGameTime();windowDamage=0;}
        // Shared across every spell and beam slice: stacking casts cannot erase the boss in two volleys.
        float hit=Math.min(Math.min(amount,100)*(gauntletHit?(exposed?.32f:.18f):.10f),Math.max(0,(exposed?14:8)-windowDamage));
        if(hit<=0)return false;
        float before=getHealth();
        if(gauntletHit) invulnerableTime=0;
        boolean result=super.hurtServer(level,source,hit);
        windowDamage+=Math.max(0,before-getHealth());return result;
	}

	@Override
	public void die(DamageSource source) {
		if (dead) return;
		super.die(source);
		if (level() instanceof ServerLevel && dead) TribunalEncounter.defeated(this);
	}

	@Override
	protected void tickDeath() {
		++deathTime;
        if(level() instanceof ServerLevel level){
            if(deathTime==EXPLOSION_TICK){
                Vec3 at=position().add(0,53,0);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,at.x,at.y,at.z,180,25,40,25,.8);
                level.playSound(null,net.minecraft.core.BlockPos.containing(at),net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),net.minecraft.sounds.SoundSource.HOSTILE,6,.5f);
            }
            if(deathTime>=DEATH_TICKS)discard();
        }
	}
}
