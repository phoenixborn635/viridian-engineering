package dev.viridianengineering.mixin;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import dev.viridianengineering.content.pump.VEPumps;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Applies a Manasteel Pump's speed boost to every pipe it pressurizes (copper pipes included). */
@Mixin(value = FluidTransportBehaviour.class, remap = false)
public abstract class FluidTransportBehaviourMixin {

    @ModifyVariable(method = "addPressure", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float ve$pumpBoost(float pressure, Direction side, boolean inbound) {
        return pressure * VEPumps.PumpBoost.current();
    }
}
