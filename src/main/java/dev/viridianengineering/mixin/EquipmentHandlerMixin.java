package dev.viridianengineering.mixin;

import dev.viridianengineering.content.gear.SylvanTiara;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import velrondevs.botania.common.handler.EquipmentHandler;
import velrondevs.botania.registry.BotaniaItems;

/**
 * Flugel Tiara helmet upgrade: when Botania looks for a worn Flugel Tiara and finds none, a Sylvan helmet
 * with a tiara fitted answers with its stand-in (see {@link SylvanTiara}). No Curios slot is used.
 */
@Mixin(value = EquipmentHandler.class, remap = false)
public abstract class EquipmentHandlerMixin {

    @Inject(method = "findOrEmpty(Lnet/minecraft/world/item/Item;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"), cancellable = true)
    private static void ve$sylvanTiara(Item item, LivingEntity living, CallbackInfoReturnable<ItemStack> cir) {
        if (item != BotaniaItems.flightTiara || !cir.getReturnValue().isEmpty() || SylvanTiara.bypassing()) return;
        ItemStack standIn = SylvanTiara.standIn(living);
        if (!standIn.isEmpty()) cir.setReturnValue(standIn);
    }
}
