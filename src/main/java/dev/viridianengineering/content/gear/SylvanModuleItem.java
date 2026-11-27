package dev.viridianengineering.content.gear;

import dev.viridianengineering.registry.VEGear;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A Sylvan augmentation module; right-click it onto the matching Sylvan piece in an inventory to fit it. */
public class SylvanModuleItem extends Item {

    public final GearData.Module module;

    public SylvanModuleItem(GearData.Module module, Properties props) {
        super(props);
        this.module = module;
    }

    public static Item itemFor(GearData.Module m) {
        return switch (m) {
            case BRACER_TANKS -> VEGear.PNEUMATIC_BRACER_TANKS.get();
            case MANA_PACK -> VEGear.LIVINGROCK_MANA_PACK.get();
            case STRIDERS -> VEGear.PNEUMATIC_STRIDERS.get();
            case BOOT_THRUSTERS -> VEGear.PNEUMATIC_BOOT_THRUSTERS.get();
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.viridian_engineering." + module.id()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.viridian_engineering.module.fits." + module.slot.getName())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
