package dev.viridianengineering.content.cannon;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.viridianengineering.api.mana.ManaBuffer;
import dev.viridianengineering.compat.SableCompat;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.registry.VEDamageTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Gaia Arcane Cannon (spec 3.8). 50,000 mana buffer (Terrasteel pipes or spreaders); fires while
 * powered by redstone on any face. Two modes, switched by right-clicking with an empty hand:
 * <ul>
 *   <li><b>Beam:</b> 64-block raycast, 200 mana/t, 7.0 damage every 10 ticks to everything on the ray,
 *       at most 200 ticks of continuous fire, then an 80-tick cooldown.</li>
 *   <li><b>Projectile:</b> charges up to 60 ticks at 250 mana/t (15,000 total) while powered, and fires on
 *       the falling edge: 100% charge = 42.0 damage; 25-99% = 10 + 20 * ((c - 0.25) / 0.75);
 *       below 25% the charge vents (no shot, no refund).</li>
 * </ul>
 */
public class GaiaCannonBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    public enum Mode { BEAM, PROJECTILE }

    public final ManaBuffer mana = new ManaBuffer(this, () -> VEConfig.CANNON_MANA_BUFFER.get(), true, false);

    private Mode mode = Mode.PROJECTILE;
    private boolean wasPowered;
    private long lastSyncedMana = -1;
    private int beamTicks, cooldown, chargeTicks;
    /** Synced for the renderer: length of the beam this tick (0 = not firing). */
    private float beamLength;
    private int lastFiredTick;

    public GaiaCannonBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public Direction facing() { return getBlockState().getValue(DirectionalBlock.FACING); }

    public Mode mode() { return mode; }

    public float beamLength() { return beamLength; }

    public float charge() { return chargeTicks / (float) VEConfig.PROJECTILE_CHARGE_TICKS.get(); }

    public int cooldown() { return cooldown; }

    public void toggleMode() {
        mode = mode == Mode.BEAM ? Mode.PROJECTILE : Mode.BEAM;
        beamTicks = 0;
        chargeTicks = 0;
        beamLength = 0;
        setChanged();
        sendData();
    }

    public void setMode(Mode m) { if (mode != m) toggleMode(); }

    /** The block in front of the redstone port's face: the only place a signal is read from. */
    public BlockPos redstoneInput() {
        return GaiaCannonBlock.portCell(worldPosition, facing(), false).relative(GaiaCannonBlock.portFace(facing(), false));
    }

    /** Muzzle point in block space, just past the barrel's tip at the front face of the 3x3x3. */
    public Vec3 muzzle() {
        Direction f = facing();
        return Vec3.atCenterOf(worldPosition).add(f.getStepX() * 1.6, f.getStepY() * 1.6, f.getStepZ() * 1.6);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        boolean powered = level.getSignal(redstoneInput(), GaiaCannonBlock.portFace(facing(), false)) > 0;
        float oldBeam = beamLength;
        int oldCharge = chargeTicks;

        int oldCooldown = cooldown;
        if (cooldown > 0) cooldown--;
        if (mode == Mode.BEAM) tickBeam(powered); else tickProjectile(powered);

        wasPowered = powered;
        boolean manaChanged = level.getGameTime() % 10 == 0 && mana.getStoredMana() != lastSyncedMana;
        if (manaChanged || oldCooldown / 10 != cooldown / 10 || oldCooldown == 1 || oldBeam != beamLength || oldCharge / 5 != chargeTicks / 5 || (chargeTicks == 0) != (oldCharge == 0)) {
            lastSyncedMana = mana.getStoredMana();
            sendData();
        }
    }

    // ------------------------------------------------------------------ beam

    private void tickBeam(boolean powered) {
        if (!powered || cooldown > 0) {
            beamTicks = 0;
            beamLength = 0;
            return;
        }
        if (!mana.consume(VEConfig.BEAM_MANA_PER_TICK.get())) {
            beamTicks = 0;
            beamLength = 0;
            return;
        }
        beamTicks++;
        // world space, so a cannon on a Sable ship fires where the ship actually points
        Vec3 from = SableCompat.toWorld(level, muzzle());
        Vec3 dir = SableCompat.toWorldNormal(level, worldPosition, Vec3.atLowerCornerOf(facing().getNormal())).normalize();
        int range = VEConfig.BEAM_RANGE.get();
        BlockHitResult blockHit = level.clip(new ClipContext(from, from.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity) null));
        double len = blockHit.getType() == HitResult.Type.MISS ? range : blockHit.getLocation().distanceTo(from);
        beamLength = (float) len;
        if (beamTicks % 10 == 1) damageAlong(from, dir, len); // every 10 ticks, starting at once
        if (beamTicks >= VEConfig.BEAM_MAX_DURATION_TICKS.get()) {
            beamTicks = 0;
            beamLength = 0;
            cooldown = VEConfig.BEAM_COOLDOWN_TICKS.get();
            level.playSound(null, worldPosition, dev.viridianengineering.registry.VESounds.CANNON_COOLDOWN.get(), SoundSource.BLOCKS, 1f, 0.8f);
        }
    }

    private void damageAlong(Vec3 from, Vec3 dir, double len) {
        Vec3 to = from.add(dir.scale(len));
        DamageSource source = VEDamageTypes.source(level, VEDamageTypes.ARCANE_BEAM, null, null);
        float dmg = VEConfig.BEAM_DAMAGE_PER_HALF_SECOND.get().floatValue();
        AABB box = new AABB(from, to).inflate(0.5);
        for (Entity e : level.getEntities((Entity) null, box, x -> x instanceof LivingEntity && x.isAlive())) {
            Optional<Vec3> clip = e.getBoundingBox().inflate(0.3).clip(from, to);
            if (clip.isPresent() || e.getBoundingBox().contains(from)) {
                // ignores invulnerability frames (damage type tag bypasses_cooldown) and grants none
                // either, so other damage still lands; no knockback (tag no_knockback)
                int before = e.invulnerableTime;
                e.hurt(source, dmg);
                e.invulnerableTime = before;
            }
        }
    }

    // ------------------------------------------------------------------ projectile

    private void tickProjectile(boolean powered) {
        int max = VEConfig.PROJECTILE_CHARGE_TICKS.get();
        if (powered) {
            if (chargeTicks < max) {
                long perTick = Math.max(0, (long) Math.ceil(VEConfig.PROJECTILE_MANA_COST.get() / (double) max));
                if (mana.consume(perTick)) {
                    if (chargeTicks == 0)
                        level.playSound(null, worldPosition, dev.viridianengineering.registry.VESounds.CANNON_CHARGE.get(), SoundSource.BLOCKS, 1f, .8f);
                    chargeTicks++;
                }
            }
            return;
        }
        if (wasPowered && chargeTicks > 0) fire(chargeTicks / (float) max);
        chargeTicks = 0;
    }

    /** Damage of a shot at charge {@code c}, or -1 when it vents. */
    public static float damageFor(float c) {
        float max = VEConfig.PROJECTILE_MAX_DAMAGE.get().floatValue();
        if (c >= 1f) return max;
        if (c < 0.25f) return -1;
        return 10f + 20f * ((c - 0.25f) / 0.75f);
    }

    private void fire(float c) {
        float damage = damageFor(c);
        ServerLevel sl = (ServerLevel) level;
        Vec3 m = muzzle();
        if (damage < 0) {
            sl.sendParticles(ParticleTypes.CLOUD, m.x, m.y, m.z, 12, .15, .15, .15, .02);
            sl.playSound(null, worldPosition, dev.viridianengineering.registry.VESounds.CANNON_VENT.get(), SoundSource.BLOCKS, 0.8f, 1.4f);
            return;
        }
        Vec3 dir = Vec3.atLowerCornerOf(facing().getNormal());
        Vec3 wm = SableCompat.toWorld(level, m), wd = SableCompat.toWorldNormal(level, worldPosition, dir);
        ArcaneBoltEntity bolt = ArcaneBoltEntity.create(level, wm, wd, damage, c);
        level.addFreshEntity(bolt);
        lastFiredTick = (int) level.getGameTime();
        level.playSound(null, worldPosition, dev.viridianengineering.registry.VESounds.CANNON_FIRE.get(), SoundSource.BLOCKS, 1.5f, 0.6f + 0.8f * c);
    }

    // ------------------------------------------------------------------ info / data

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable("block.viridian_engineering.gaia_arcane_cannon")));
        tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.cannon.mode."
                + mode.name().toLowerCase())).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.mana",
                mana.getStoredMana(), mana.getManaCapacity())).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("     ").append(Component.translatable(wasPowered
                ? "viridian_engineering.goggles.cannon.powered" : "viridian_engineering.goggles.cannon.unpowered"))
                .withStyle(wasPowered ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        if (mana.getStoredMana() < 250)
            tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.cannon.no_mana"))
                    .withStyle(ChatFormatting.RED));
        if (mode == Mode.PROJECTILE && chargeTicks > 0)
            tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.cannon.charge",
                    Math.round(charge() * 100))).withStyle(ChatFormatting.GOLD));
        if (cooldown > 0)
            tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.cannon.cooldown",
                    String.format("%.1f", cooldown / 20f))).withStyle(ChatFormatting.RED));
        return true;
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(worldPosition).inflate(VEConfig.BEAM_RANGE.get() + 1);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        mana.write(tag, "Mana");
        tag.putString("Mode", mode.name());
        tag.putInt("Beam", beamTicks);
        tag.putInt("Cooldown", cooldown);
        tag.putInt("Charge", chargeTicks);
        tag.putBoolean("WasPowered", wasPowered);
        if (clientPacket) tag.putFloat("BeamLength", beamLength);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        mana.read(tag, "Mana");
        try { mode = Mode.valueOf(tag.getString("Mode")); } catch (IllegalArgumentException e) { mode = Mode.PROJECTILE; }
        beamTicks = tag.getInt("Beam");
        cooldown = tag.getInt("Cooldown");
        chargeTicks = tag.getInt("Charge");
        wasPowered = tag.getBoolean("WasPowered");
        if (clientPacket) beamLength = tag.getFloat("BeamLength");
    }
}
