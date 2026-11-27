package dev.viridianengineering.content.gear;

import com.simibubi.create.content.equipment.armor.BacktankUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import velrondevs.botania.api.mana.ManaItem;
import velrondevs.botania.api.mana.ManaItemHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * Power for the gaia gear: compressed air (spec 3.8: Create's {@code BacktankUtil.getAllWithAir} /
 * {@code consumeAir}, so a worn Backtank and a tank-fitted Sylvan Chestplate both count), mana (an
 * item's own buffer first, then the player's Botania mana items) and the wearer's world speed.
 * <p>
 * Every take is all-or-nothing: a cost is either paid in full or nothing is drawn. Creative players
 * pay nothing.
 */
public final class GearPower {

    private GearPower() {}

    static boolean free(LivingEntity e) {
        return e instanceof Player p && p.getAbilities().instabuild;
    }

    // ------------------------------------------------------------------ air

    public static int air(LivingEntity e) {
        int total = 0;
        for (ItemStack s : BacktankUtil.getAllWithAir(e)) total += BacktankUtil.getAir(s);
        return total;
    }

    /** Takes exactly {@code amount} air from the worn tanks (emptiest first), or nothing. */
    public static boolean takeAir(LivingEntity e, int amount) {
        if (amount <= 0 || free(e)) return true;
        List<ItemStack> tanks = BacktankUtil.getAllWithAir(e);
        int total = 0;
        for (ItemStack s : tanks) total += BacktankUtil.getAir(s);
        if (total < amount) return false;
        int left = amount;
        for (ItemStack s : tanks) {
            int take = Math.min(left, BacktankUtil.getAir(s));
            if (take > 0) BacktankUtil.consumeAir(e, s, take);
            left -= take;
            if (left <= 0) break;
        }
        return true;
    }

    // ------------------------------------------------------------------ mana

    /** The cost after the wearer's Botania tool discount (Sylvan / Terrasteel set, Unbreaking). */
    public static int discounted(Player p, ItemStack tool, int cost) {
        float d = ManaItemHandler.instance().getFullDiscountForTools(p, tool);
        return Math.max(0, (int) (cost * Math.max(0f, 1f - d)));
    }

    /** True if {@code cost} mana could be paid right now from {@code own}'s buffer plus the player's mana items. */
    public static boolean canPay(Player p, ItemStack own, int cost) {
        if (cost <= 0 || free(p)) return true;
        int inOwn = ownMana(own);
        if (inOwn >= cost) return true;
        return ManaItemHandler.instance().requestManaExact(own, p, cost - inOwn, false);
    }

    /** Pays {@code cost} mana from {@code own}'s buffer first, the rest from the player's mana items; all or nothing. */
    public static boolean pay(Player p, ItemStack own, int cost) {
        if (cost <= 0 || free(p)) return true;
        int inOwn = ownMana(own);
        if (inOwn >= cost) {
            setOwnMana(own, inOwn - cost);
            return true;
        }
        if (!ManaItemHandler.instance().requestManaExact(own, p, cost - inOwn, true)) return false;
        if (inOwn > 0) setOwnMana(own, 0);
        return true;
    }

    static int ownMana(ItemStack s) {
        return s.isEmpty() ? 0 : GearData.getInt(s, GearData.MANA);
    }

    static void setOwnMana(ItemStack s, int mana) {
        GearData.setInt(s, GearData.MANA, Math.max(0, mana));
    }

    /** Botania {@code ManaItem} backed by an item's {@code Mana} value (the lance, the mana pack). */
    public record StackMana(ItemStack stack, IntSupplier capacity, boolean exports) implements ManaItem {
        @Override public int getMana() { return Math.min(ownMana(stack), getMaxMana()); }
        @Override public int getMaxMana() { return capacity.getAsInt(); }
        @Override public void addMana(int mana) { setOwnMana(stack, Math.min(getMaxMana(), Math.max(0, getMana() + mana))); }
        @Override public boolean canReceiveManaFromPool(BlockEntity pool) { return true; }
        @Override public boolean canReceiveManaFromItem(ItemStack other) { return true; }
        @Override public boolean canExportManaToPool(BlockEntity pool) { return exports; }
        @Override public boolean canExportManaToItem(ItemStack other) { return exports; }
        @Override public boolean isNoExport() { return !exports; }
    }

    // ------------------------------------------------------------------ world speed

    private static final int WINDOW = 4;
    private static final Map<UUID, Motion> MOTION = new HashMap<>();
    private static final Map<UUID, Double> SPEED_OVERRIDE = new HashMap<>();

    private static final class Motion {
        Vec3 last;
        final double[] steps = new double[WINDOW];
        int i;
    }

    /**
     * Called every server tick per player. The server sees a player's real movement only as position
     * changes (sprinting, falling, and standing on a moving Sable ship all move it), so the speed is
     * the distance covered, averaged over a few ticks to smooth bunched movement packets.
     */
    public static void trackMotion(Player p) {
        Motion m = MOTION.computeIfAbsent(p.getUUID(), u -> new Motion());
        Vec3 now = p.position();
        double step = m.last == null ? 0 : now.distanceTo(m.last);
        if (step > 10) step = 0; // a teleport, not motion
        m.steps[m.i++ % WINDOW] = step;
        m.last = now;
    }

    /** World speed in blocks per tick. */
    public static double speed(Player p) {
        Double o = SPEED_OVERRIDE.get(p.getUUID());
        if (o != null) return o;
        Motion m = MOTION.get(p.getUUID());
        if (m == null) return 0;
        double sum = 0;
        for (double d : m.steps) sum += d;
        return sum / WINDOW;
    }

    /** GameTests only: FakePlayers never move, so tests set the speed they want to measure (null clears). */
    public static void overrideSpeed(Player p, Double blocksPerTick) {
        if (blocksPerTick == null) SPEED_OVERRIDE.remove(p.getUUID());
        else SPEED_OVERRIDE.put(p.getUUID(), blocksPerTick);
    }

    public static void forget(UUID player) {
        MOTION.remove(player);
    }

    public static void clear() {
        MOTION.clear();
        SPEED_OVERRIDE.clear();
    }
}
