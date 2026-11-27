package dev.viridianengineering.content.drive;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Spec 3.6: drive contents are keyed by item + component patch. Two stacks share a key exactly when
 * {@link ItemStack#isSameItemSameComponents} would say so, so differently named or enchanted
 * items never merge.
 */
public record ItemStackKey(Item item, DataComponentPatch patch) {

    public static ItemStackKey of(ItemStack stack) {
        return new ItemStackKey(stack.getItem(), stack.getComponentsPatch());
    }

    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == item && stack.getComponentsPatch().equals(patch);
    }

    /** A fresh stack of this key. Counts above a vanilla stack size are allowed (browse views). */
    public ItemStack toStack(int count) {
        return new ItemStack(BuiltInRegistries.ITEM.wrapAsHolder(item), count, patch);
    }

    /**
     * Serialized size of the component patch in bytes (NBT). Unserializable patches report
     * {@link Integer#MAX_VALUE} so they are always rejected.
     */
    public int payloadBytes(HolderLookup.Provider registries) {
        if (patch.isEmpty()) return 0;
        var result = DataComponentPatch.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), patch);
        Tag tag = result.result().orElse(null);
        if (tag == null) return Integer.MAX_VALUE;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            tag.write(out);
        } catch (IOException e) {
            return Integer.MAX_VALUE;
        }
        return bytes.size();
    }
}
