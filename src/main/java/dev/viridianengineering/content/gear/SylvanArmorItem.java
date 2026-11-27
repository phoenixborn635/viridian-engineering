package dev.viridianengineering.content.gear;

import com.simibubi.create.content.equipment.armor.BacktankUtil;
import dev.viridianengineering.config.VEConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import velrondevs.botania.api.mana.ManaDiscountArmor;
import velrondevs.botania.api.mana.ManaItemHandler;
import velrondevs.botania.common.item.equipment.tool.ToolCommons;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Sylvan armor (spec 3.8, LOCKED): the Tier 5 upgrade of Botania's Terrasteel set. The full set gives
 * 20 armor, 14 toughness and 60% knockback resistance (from the material), a 20% Botania mana
 * discount and 2 mana/t of passive regeneration (both on the helmet, like Terrasteel's), heals like
 * Terrasteel, and repairs itself from mana.
 * <p>
 * Module slots (stored in CUSTOM_DATA): the chestplate has 2 (Pneumatic Bracer Tanks, Livingrock Mana
 * Pack), the leggings 1 (Pneumatic Striders), the boots 1 (Pneumatic Boot Thrusters). In an inventory,
 * right-click a module onto the piece to fit it; right-click the piece with an empty cursor to take
 * the last module back out.
 */
public class SylvanArmorItem extends ArmorItem implements ManaDiscountArmor {

    /** Botania's Terrasteel repair price per durability point (Manasteel 70, x2 when repairing). */
    private static final int MANA_PER_DAMAGE = 70;

    public SylvanArmorItem(Holder<ArmorMaterial> material, Type type, Properties props) {
        super(material, type, props);
    }

    public static int slots(EquipmentSlot slot) {
        return switch (slot) {
            case CHEST -> 2;
            case LEGS, FEET -> 1;
            default -> 0;
        };
    }

    public static boolean isSylvan(ItemStack s) {
        return s.getItem() instanceof SylvanArmorItem;
    }

    public static boolean hasFullSet(Player p) {
        return isSylvan(p.getItemBySlot(EquipmentSlot.HEAD)) && isSylvan(p.getItemBySlot(EquipmentSlot.CHEST))
                && isSylvan(p.getItemBySlot(EquipmentSlot.LEGS)) && isSylvan(p.getItemBySlot(EquipmentSlot.FEET));
    }

    /** The worn Sylvan piece in {@code slot} carrying {@code module}, or empty. */
    public static ItemStack worn(LivingEntity e, EquipmentSlot slot, GearData.Module module) {
        ItemStack s = e.getItemBySlot(slot);
        return isSylvan(s) && GearData.has(s, module) ? s : ItemStack.EMPTY;
    }

    @Override
    public float getDiscount(ItemStack stack, int slot, Player player, @Nullable ItemStack tool) {
        return getType() == Type.HELMET && hasFullSet(player) ? VEConfig.SYLVAN_MANA_DISCOUNT.get().floatValue() : 0f;
    }

    // ------------------------------------------------------------------ mana durability (Botania style)

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<Item> onBroken) {
        return ToolCommons.damageItemIfPossible(stack, amount, entity, MANA_PER_DAMAGE);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof Player p && stack.getDamageValue() > 0
                && ManaItemHandler.instance().requestManaExact(stack, p, MANA_PER_DAMAGE * 2, true))
            stack.setDamageValue(stack.getDamageValue() - 1);
    }

    /** The 3D worn model (client.SylvanArmorModel) uses one texture for every piece. */
    @Override
    public net.minecraft.resources.ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot,
                                                                    ArmorMaterial.Layer layer, boolean innerModel) {
        return dev.viridianengineering.ViridianEngineering.asResource("textures/models/armor/sylvan_3d.png");
    }

    // ------------------------------------------------------------------ modules

    /** Fits {@code module} if this piece takes it and has a free slot. */
    public static boolean fit(ItemStack armor, GearData.Module module) {
        if (!(armor.getItem() instanceof SylvanArmorItem a) || a.getEquipmentSlot() != module.slot) return false;
        List<GearData.Module> list = GearData.modules(armor);
        if (list.size() >= slots(module.slot)) return false;
        list.add(module);
        GearData.setModules(armor, list);
        return true;
    }

    /** Takes the last fitted module out (tank air and pack mana stay with the armor, capped by what is left). */
    public static GearData.Module unfit(ItemStack armor) {
        List<GearData.Module> list = GearData.modules(armor);
        if (list.isEmpty()) return null;
        GearData.Module m = list.remove(list.size() - 1);
        GearData.setModules(armor, list);
        return m;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack armor, ItemStack carried, Slot slot, ClickAction action,
                                            Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) return false;
        if (carried.getItem() instanceof SylvanModuleItem module) {
            if (!fit(armor, module.module)) return false;
            carried.shrink(1);
            player.playSound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.8f, 1.2f);
            return true;
        }
        if (carried.isEmpty() && !GearData.modules(armor).isEmpty()) {
            GearData.Module m = unfit(armor);
            access.set(new ItemStack(SylvanModuleItem.itemFor(m)));
            player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.8f, 0.8f);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ tooltip

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.viridian_engineering.sylvan.set").withStyle(ChatFormatting.GRAY));
        int n = slots(getEquipmentSlot());
        if (n > 0) {
            List<GearData.Module> mods = new ArrayList<>(GearData.modules(stack));
            tooltip.add(Component.translatable("tooltip.viridian_engineering.sylvan.slots", mods.size(), n).withStyle(ChatFormatting.GRAY));
            for (GearData.Module m : mods)
                tooltip.add(Component.literal(" - ").append(SylvanModuleItem.itemFor(m).getDescription())
                        .withStyle(ChatFormatting.GREEN));
            if (GearData.has(stack, GearData.Module.BRACER_TANKS))
                tooltip.add(Component.translatable("tooltip.viridian_engineering.sylvan.air",
                        BacktankUtil.getAir(stack), BacktankUtil.maxAir(stack)).withStyle(ChatFormatting.GOLD));
            if (GearData.has(stack, GearData.Module.MANA_PACK))
                tooltip.add(Component.translatable("tooltip.viridian_engineering.gear.mana",
                        GearPower.ownMana(stack), SylvanEvents.packCapacity(stack)).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.viridian_engineering.sylvan.fit_help").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (getEquipmentSlot() == EquipmentSlot.HEAD) {
            if (GearData.hasTiara(stack))
                tooltip.add(Component.translatable("tooltip.viridian_engineering.sylvan.tiara")
                        .append(" (").append(Component.translatable("botania.wings" + GearData.tiaraVariant(stack))).append(")")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            else
                tooltip.add(Component.translatable("tooltip.viridian_engineering.sylvan.no_tiara").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
