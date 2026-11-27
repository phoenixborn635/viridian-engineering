package dev.viridianengineering.registry;

import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.content.cannon.ArcaneBoltEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VEEntities {

    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ViridianEngineering.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<ArcaneBoltEntity>> ARCANE_BOLT = ENTITIES.register("arcane_bolt",
            () -> EntityType.Builder.<ArcaneBoltEntity>of(ArcaneBoltEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(8).updateInterval(1).build("arcane_bolt"));

    private VEEntities() {}

    public static void register(IEventBus bus) { ENTITIES.register(bus); }
}
