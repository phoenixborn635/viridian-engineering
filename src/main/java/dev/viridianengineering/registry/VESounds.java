package dev.viridianengineering.registry;

import dev.viridianengineering.ViridianEngineering;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VESounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ViridianEngineering.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> LANCE_THRUST = sound("lance_thrust");
    public static final DeferredHolder<SoundEvent, SoundEvent> LANCE_CHARGE = sound("lance_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> LANCE_LUNGE = sound("lance_lunge");
    public static final DeferredHolder<SoundEvent, SoundEvent> CANNON_CHARGE = sound("cannon_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> CANNON_FIRE = sound("cannon_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> CANNON_VENT = sound("cannon_vent");
    public static final DeferredHolder<SoundEvent, SoundEvent> CANNON_COOLDOWN = sound("cannon_cooldown");
    public static final DeferredHolder<SoundEvent, SoundEvent> BATTERY_UNSTABLE = sound("battery_unstable");
    public static final DeferredHolder<SoundEvent, SoundEvent> BATTERY_DETONATE = sound("battery_detonate");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPARK_LINK = sound("spark_link");
    public static final DeferredHolder<SoundEvent, SoundEvent> ALFHEIM_INFUSE = sound("alfheim_infuse");
    public static final DeferredHolder<SoundEvent, SoundEvent> ALFHEIM_DETACH = sound("alfheim_detach");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOOT_DASH = sound("boot_dash");

    private VESounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(ViridianEngineering.asResource(id)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
