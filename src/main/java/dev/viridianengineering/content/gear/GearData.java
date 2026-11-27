package dev.viridianengineering.content.gear;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Everything the gaia gear keeps on its item stacks lives in {@code DataComponents.CUSTOM_DATA}
 * (spec 3.8): Sylvan module slots ({@code Modules}), the Flugel Tiara upgrade ({@code hasFlugelTiara},
 * {@code tiaraVariant}, and the tiara's own flight state under {@code Tiara}), stored mana
 * ({@code Mana}: the lance buffer and the Livingrock Mana Pack).
 */
public final class GearData {

    public static final String MODULES = "Modules";
    public static final String HAS_TIARA = "hasFlugelTiara";
    public static final String TIARA_VARIANT = "tiaraVariant";
    public static final String TIARA = "Tiara";
    public static final String MANA = "Mana";

    /** The four Sylvan augmentation modules and the armor piece each one fits. */
    public enum Module {
        BRACER_TANKS(EquipmentSlot.CHEST),
        MANA_PACK(EquipmentSlot.CHEST),
        STRIDERS(EquipmentSlot.LEGS),
        BOOT_THRUSTERS(EquipmentSlot.FEET);

        public final EquipmentSlot slot;

        Module(EquipmentSlot slot) { this.slot = slot; }

        public String id() { return name().toLowerCase(Locale.ROOT); }

        public static Module byId(String id) {
            for (Module m : values()) if (m.id().equals(id)) return m;
            return null;
        }
    }

    private GearData() {}

    public static CompoundTag read(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        return d == null ? new CompoundTag() : d.copyTag();
    }

    public static void write(ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int getInt(ItemStack stack, String key) {
        return read(stack).getInt(key);
    }

    public static void setInt(ItemStack stack, String key, int value) {
        CompoundTag t = read(stack);
        if (t.contains(key) && t.getInt(key) == value) return;
        t.putInt(key, value);
        write(stack, t);
    }

    // ------------------------------------------------------------------ modules

    public static List<Module> modules(ItemStack stack) {
        List<Module> out = new ArrayList<>();
        ListTag list = read(stack).getList(MODULES, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            Module m = Module.byId(list.getString(i));
            if (m != null) out.add(m);
        }
        return out;
    }

    public static int count(ItemStack stack, Module m) {
        int n = 0;
        for (Module x : modules(stack)) if (x == m) n++;
        return n;
    }

    public static boolean has(ItemStack stack, Module m) {
        return count(stack, m) > 0;
    }

    public static void setModules(ItemStack stack, List<Module> modules) {
        CompoundTag t = read(stack);
        ListTag list = new ListTag();
        for (Module m : modules) list.add(StringTag.valueOf(m.id()));
        if (list.isEmpty()) t.remove(MODULES);
        else t.put(MODULES, list);
        write(stack, t);
    }

    // ------------------------------------------------------------------ flugel tiara

    public static boolean hasTiara(ItemStack stack) {
        return read(stack).getBoolean(HAS_TIARA);
    }

    public static int tiaraVariant(ItemStack stack) {
        return read(stack).getInt(TIARA_VARIANT);
    }

    /** The tiara's own data (variant, flight time left, dash cooldown ...) kept on the helmet. */
    public static CompoundTag tiaraTag(ItemStack helmet) {
        return read(helmet).getCompound(TIARA);
    }

    public static void setTiara(ItemStack helmet, CompoundTag tiaraData) {
        CompoundTag t = read(helmet);
        t.putBoolean(HAS_TIARA, true);
        t.putInt(TIARA_VARIANT, tiaraData.getInt("variant"));
        t.put(TIARA, tiaraData.copy());
        write(helmet, t);
    }

    /** Stores the tiara state back, only when it changed (every change re-syncs the armor slot). */
    public static void updateTiara(ItemStack helmet, CompoundTag tiaraData) {
        CompoundTag t = read(helmet);
        if (tiaraData.equals(t.getCompound(TIARA))) return;
        t.put(TIARA, tiaraData.copy());
        t.putInt(TIARA_VARIANT, tiaraData.getInt("variant"));
        write(helmet, t);
    }
}
