package dev.viridianengineering.content.gear;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import velrondevs.botania.common.handler.EquipmentHandler;
import velrondevs.botania.common.item.equipment.bauble.FlugelTiaraItem;
import velrondevs.botania.registry.BotaniaItems;

/**
 * Flugel Tiara helmet upgrade (spec 3.8, LOCKED). A Sylvan helmet with a tiara fitted stands in for a
 * worn tiara without taking a Curios slot: Botania's own tiara code runs against a stand-in stack built
 * from the tiara data kept on the helmet, so flight, sprint-glide, dash, the flight gauge and its
 * recharge from inventory/armor mana all behave exactly like the real accessory.
 * <ul>
 *   <li>{@code EquipmentHandlerMixin}: Botania's {@code EquipmentHandler.findOrEmpty(flightTiara, ...)}
 *       returns the stand-in when no real tiara is worn (flight status, mana cost, HUD gauge).</li>
 *   <li>{@link #tick}: runs the tiara's {@code onWornTick} on the stand-in (both sides, as Curios
 *       does) and writes its state back to the helmet.</li>
 *   <li>The wings are drawn in their normal back position by {@code client.SylvanArmorLayer}.</li>
 * </ul>
 * A real tiara worn in a Curios slot always wins; the helmet then does nothing.
 */
public final class SylvanTiara {

    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

    private SylvanTiara() {}

    /** True while we look up the player's real tiara (the mixin must not answer with the stand-in). */
    public static boolean bypassing() {
        return BYPASS.get();
    }

    /** The tiara the worn Sylvan helmet stands in for, or empty. */
    public static ItemStack standIn(LivingEntity e) {
        ItemStack helm = e.getItemBySlot(EquipmentSlot.HEAD);
        if (!SylvanArmorItem.isSylvan(helm) || !GearData.hasTiara(helm)) return ItemStack.EMPTY;
        ItemStack t = new ItemStack(BotaniaItems.flightTiara);
        t.set(DataComponents.CUSTOM_DATA, CustomData.of(GearData.tiaraTag(helm)));
        return t;
    }

    public static boolean realTiaraWorn(LivingEntity e) {
        BYPASS.set(true);
        try {
            return !EquipmentHandler.findOrEmpty(BotaniaItems.flightTiara, e).isEmpty();
        } finally {
            BYPASS.set(false);
        }
    }

    /** True if the helmet's tiara is the one in use (fitted, and no real tiara worn). */
    public static boolean active(LivingEntity e) {
        ItemStack helm = e.getItemBySlot(EquipmentSlot.HEAD);
        return SylvanArmorItem.isSylvan(helm) && GearData.hasTiara(helm) && !realTiaraWorn(e);
    }

    public static void tick(Player p) {
        if (!active(p)) return;
        ItemStack helm = p.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack t = standIn(p);
        ((FlugelTiaraItem) BotaniaItems.flightTiara).onWornTick(t, p);
        GearData.updateTiara(helm, GearData.read(t));
    }
}
