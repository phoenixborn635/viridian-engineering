package dev.viridianengineering.mixin;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import dev.viridianengineering.content.autocraft.CraftingScheduler;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = BeltInventory.class, remap = false)
abstract class BeltInventoryMixin {
    @Shadow @Final BeltBlockEntity belt;

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/kinetics/belt/transport/BeltInventory;eject(Lcom/simibubi/create/content/kinetics/belt/transport/TransportedItemStack;)V"))
    private void viridianEngineering$collectJobOutput(BeltInventory inventory, TransportedItemStack item) {
        if (belt.getLevel() instanceof ServerLevel level && CraftingScheduler.collectBeltEjection(level, belt, item.stack))
            return;
        inventory.eject(item);
    }
}
