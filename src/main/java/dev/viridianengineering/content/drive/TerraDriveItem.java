package dev.viridianengineering.content.drive;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.UUID;

/**
 * Spec 3.6: a Terra Drive item carries only its DriveUUID (plus a cached "used" count for the
 * tooltip) in {@code CUSTOM_DATA}; the contents live in {@link TerraDriveSavedData}. A new drive
 * gets its UUID the first time it is mounted.
 */
public class TerraDriveItem extends Item {

    private static final String ID_KEY = "DriveUUID";
    private static final String USED_KEY = "Used";

    private final long capacity;

    public TerraDriveItem(Properties properties, long capacity) {
        super(properties);
        this.capacity = capacity;
    }

    public long capacity() {
        return capacity;
    }

    public static UUID getId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.getUnsafe();
        return tag.hasUUID(ID_KEY) ? tag.getUUID(ID_KEY) : null;
    }

    /** Server side only: returns the drive's UUID, assigning a fresh one to a blank drive. */
    public static UUID getOrAssignId(ItemStack stack) {
        UUID id = getId(stack);
        if (id != null) return id;
        UUID fresh = UUID.randomUUID();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(ID_KEY, fresh));
        return fresh;
    }

    /** Caches the used count on the item so the tooltip can show it without server data. */
    public static void cacheUsed(ItemStack stack, long used) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(USED_KEY, used));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        long used = data == null ? 0 : data.getUnsafe().getLong(USED_KEY);
        tooltip.add(Component.translatable("tooltip.viridian_engineering.terra_drive.capacity",
                String.format("%,d", used), String.format("%,d", capacity)).withStyle(ChatFormatting.GRAY));
        UUID id = getId(stack);
        if (id == null)
            tooltip.add(Component.translatable("tooltip.viridian_engineering.terra_drive.blank").withStyle(ChatFormatting.DARK_GRAY));
        else if (flag.isAdvanced())
            tooltip.add(Component.literal(id.toString()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
