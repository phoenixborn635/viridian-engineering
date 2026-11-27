package dev.viridianengineering.mixin;

import com.simibubi.create.AllEnchantments;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.content.gear.GearData;
import dev.viridianengineering.content.gear.SylvanArmorItem;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pneumatic Bracer Tanks: a Sylvan Chestplate's air capacity is 1,800 per fitted tank module (spec),
 * each module also growing with Create's Capacity enchantment like a backtank. Everything that reads
 * Create's {@code BacktankUtil.maxAir} (air left, consuming, refill caps, bars) sees that number.
 */
@Mixin(value = BacktankUtil.class, remap = false)
public abstract class BacktankUtilMixin {

    @Inject(method = "maxAir(Lnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"), cancellable = true)
    private static void ve$sylvanTanks(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (!(stack.getItem() instanceof SylvanArmorItem)) return;
        int modules = GearData.count(stack, GearData.Module.BRACER_TANKS);
        int level = 0;
        for (var e : stack.getTagEnchantments().entrySet()) {
            Holder<Enchantment> h = e.getKey();
            if (h.is(AllEnchantments.CAPACITY)) { level = e.getIntValue(); break; }
        }
        int perLevel = level == 0 ? 0 : AllConfigs.server().equipment.enchantedBacktankCapacity.get();
        cir.setReturnValue(modules * (VEConfig.BRACER_TANKS_BASE_CAPACITY.get() + perLevel * level));
    }
}
