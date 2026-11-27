package dev.viridianengineering.registry;

import dev.viridianengineering.ViridianEngineering;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Data-driven damage types (data/viridian_engineering/damage_type). None bypass armor or enchantments. */
public final class VEDamageTypes {

    public static final ResourceKey<DamageType> ARCANE_BOLT = key("arcane_bolt");
    public static final ResourceKey<DamageType> ARCANE_BEAM = key("arcane_beam");
    public static final ResourceKey<DamageType> BATTERY_DETONATION = key("battery_detonation");
    /** Gaia Kinetic Lance wind-lunge shockwave. */
    public static final ResourceKey<DamageType> GAIA_GALE = key("gaia_gale");

    private VEDamageTypes() {}

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ViridianEngineering.asResource(name));
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> type, @Nullable Entity direct, @Nullable Entity cause) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), direct, cause);
    }
}
