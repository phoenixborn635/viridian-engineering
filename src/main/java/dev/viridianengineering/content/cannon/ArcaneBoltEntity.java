package dev.viridianengineering.content.cannon;

import dev.viridianengineering.registry.VEDamageTypes;
import dev.viridianengineering.registry.VEEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Gaia Arcane Cannon's charged orb. Flies straight (no gravity) and deals its damage once, as
 * {@code viridian_engineering:arcane_bolt} (a projectile type that armor and Protection reduce).
 */
public class ArcaneBoltEntity extends Projectile {

    private static final EntityDataAccessor<Float> CHARGE = SynchedEntityData.defineId(ArcaneBoltEntity.class, EntityDataSerializers.FLOAT);
    public static final double SPEED = 1.6;
    private float damage;
    private int life;

    public ArcaneBoltEntity(EntityType<? extends ArcaneBoltEntity> type, Level level) {
        super(type, level);
        noPhysics = false;
    }

    public static ArcaneBoltEntity create(Level level, Vec3 pos, Vec3 dir, float damage, float charge) {
        ArcaneBoltEntity bolt = new ArcaneBoltEntity(VEEntities.ARCANE_BOLT.get(), level);
        bolt.setPos(pos);
        bolt.setDeltaMovement(dir.normalize().scale(SPEED));
        bolt.damage = damage;
        bolt.entityData.set(CHARGE, charge);
        return bolt;
    }

    public float damage() { return damage; }

    public float charge() { return entityData.get(CHARGE); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CHARGE, 1f);
    }

    @Override
    public boolean isNoGravity() { return true; }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement();
        if (!level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                if (isRemoved()) return;
            }
            if (++life > 100) { discard(); return; }
        } else {
            // a trail of Botania wisps (pink and white-gold) left hanging where the bolt has been
            for (int i = 0; i < 3; i++) {
                boolean gold = random.nextInt(3) == 0;
                level().addParticle(velrondevs.botania.client.fx.WispParticleData.wisp(0.25f + 0.3f * charge() + random.nextFloat() * 0.2f,
                                1f, gold ? 0.95f : 0.44f, gold ? 0.77f : 0.81f, 0.5f, true),
                        getX() - motion.x * i * .35 + (random.nextFloat() - .5f) * 0.2, getY() + 0.25 - motion.y * i * .35,
                        getZ() - motion.z * i * .35 + (random.nextFloat() - .5f) * 0.2,
                        (random.nextFloat() - .5f) * 0.02, (random.nextFloat() - .5f) * 0.02, (random.nextFloat() - .5f) * 0.02);
            }
        }
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        result.getEntity().hurt(VEDamageTypes.source(level(), VEDamageTypes.ARCANE_BOLT, this, getOwner()), damage);
        burst();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        burst();
    }

    /** Breaks terrain only: entities take the bolt's own (spec) damage, never blast damage or knockback. */
    private static final net.minecraft.world.level.ExplosionDamageCalculator TERRAIN_ONLY = new net.minecraft.world.level.ExplosionDamageCalculator() {
        @Override public boolean shouldDamageEntity(net.minecraft.world.level.Explosion explosion, Entity entity) { return false; }
        @Override public float getKnockbackMultiplier(Entity entity) { return 0f; }
    };

    private void burst() {
        if (level() instanceof ServerLevel sl && dev.viridianengineering.config.VEConfig.BOLT_BREAKS_TERRAIN.get()) {
            float radius = (float) (dev.viridianengineering.config.VEConfig.BOLT_BLAST_RADIUS.get() * charge());
            if (radius >= 0.5f)
                sl.explode(this, null, TERRAIN_ONLY, getX(), getY(), getZ(), radius, false, Level.ExplosionInteraction.TNT);
        }
        if (level() instanceof ServerLevel sl) {
            sl.broadcastEntityEvent(this, IMPACT_EVENT); // clients draw the 3D impact burst
            sl.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 1.5f, 0.6f);
        }
        discard();
    }

    private static final byte IMPACT_EVENT = 71;

    /** Client side only (entity events are sent to clients): the impact burst. */
    @Override
    public void handleEntityEvent(byte id) {
        if (id == IMPACT_EVENT && level().isClientSide)
            dev.viridianengineering.client.ManaExplosions.impact(position().add(0, 0.25, 0), 0.5f + charge());
        else super.handleEntityEvent(id);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
        tag.putInt("Life", life);
        tag.putFloat("Charge", charge());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("Damage");
        life = tag.getInt("Life");
        entityData.set(CHARGE, tag.getFloat("Charge"));
    }
}
