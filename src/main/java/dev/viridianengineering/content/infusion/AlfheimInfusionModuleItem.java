package dev.viridianengineering.content.infusion;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Attaching and detaching are handled in {@link AlfheimPressHandler} (right-click events). */
public class AlfheimInfusionModuleItem extends Item {

    public AlfheimInfusionModuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.viridian_engineering.alfheim_infusion_module.tooltip.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.viridian_engineering.alfheim_infusion_module.tooltip.2")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
