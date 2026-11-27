package dev.viridianengineering.content.gear;

import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.AllDataComponents;
import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.registry.VEGear;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import velrondevs.botania.api.BotaniaCapabilities;
import velrondevs.botania.api.mana.ManaItemHandler;
import velrondevs.botania.api.mana.ManaItemsEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server and common hooks for the gaia gear (registered in {@code ViridianEngineering}). */
public final class SylvanEvents {

    static final ResourceLocation STEP_ID = ViridianEngineering.asResource("pneumatic_striders_step");
    static final ResourceLocation SPRINT_ID = ViridianEngineering.asResource("pneumatic_striders_sprint");

    private static final Map<UUID, Long> DASH_READY = new HashMap<>();
    private static final Map<UUID, Integer> AIR_BURSTS = new HashMap<>();

    private SylvanEvents() {}

    // ------------------------------------------------------------------ registration hooks

    public static void commonSetup(FMLCommonSetupEvent e) {
        // the chestplate's tanks are an air source for everything that uses Create's BacktankUtil
        // (Create's pneumatic tools, the lance); their capacity comes from BacktankUtilMixin
        e.enqueueWork(() -> BacktankUtil.addBacktankSupplier(entity -> {
            ItemStack chest = SylvanArmorItem.worn(entity, EquipmentSlot.CHEST, GearData.Module.BRACER_TANKS);
            return chest.isEmpty() ? List.of() : List.of(chest);
        }));
    }

    public static void capabilities(RegisterCapabilitiesEvent e) {
        e.registerItem(BotaniaCapabilities.MANA_ITEM,
                (stack, ctx) -> new GearPower.StackMana(stack, VEConfig.LANCE_MANA_CAPACITY::get, false),
                VEGear.GAIA_KINETIC_LANCE.get());
        e.registerItem(BotaniaCapabilities.MANA_ITEM,
                (stack, ctx) -> GearData.has(stack, GearData.Module.MANA_PACK)
                        ? new GearPower.StackMana(stack, () -> packCapacity(stack), true) : null,
                VEGear.SYLVAN_CHESTPLATE.get());
    }

    /** Botania only looks for mana items in the inventory; a worn mana pack counts too. */
    public static void manaItems(ManaItemsEvent e) {
        ItemStack chest = SylvanArmorItem.worn(e.getPlayer(), EquipmentSlot.CHEST, GearData.Module.MANA_PACK);
        if (!chest.isEmpty() && e.getItems().stream().noneMatch(s -> s == chest)) e.getItems().add(chest);
    }

    public static int packCapacity(ItemStack chest) {
        return GearData.count(chest, GearData.Module.MANA_PACK) * VEConfig.MANA_PACK_CAPACITY.get();
    }

    // ------------------------------------------------------------------ ticking

    public static void onPlayerTick(PlayerTickEvent.Post e) {
        Player p = e.getEntity();
        SylvanTiara.tick(p);
        if (p.level().isClientSide) return;
        GearPower.trackMotion(p);
        GaiaKineticLanceItem.checkLanded(p);
        serverTick(p, p.level().getGameTime());
    }

    /** Everything a worn set does on the server in one tick (GameTests call this directly). */
    public static void serverTick(Player p, long gameTime) {
        if (p.onGround()) AIR_BURSTS.remove(p.getUUID());
        setBonus(p, gameTime);
        tanks(p);
        striders(p);
    }

    /** Full set: 2 mana/t (6 with a mana pack) into carried mana items every 10 ticks, and Terrasteel's healing. */
    static void setBonus(Player p, long gameTime) {
        if (!SylvanArmorItem.hasFullSet(p)) return;
        if (gameTime % 10 == 0) {
            ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
            boolean pack = GearData.has(chest, GearData.Module.MANA_PACK);
            int amount = 10 * (pack ? VEConfig.MANA_PACK_REGEN_PER_TICK.get() : VEConfig.SYLVAN_MANA_REGEN_PER_TICK.get());
            if (pack && GearPower.ownMana(chest) + amount <= packCapacity(chest))
                GearPower.setOwnMana(chest, GearPower.ownMana(chest) + amount);
            else if (amount > 0)
                ManaItemHandler.instance().dispatchManaExact(p.getItemBySlot(EquipmentSlot.HEAD), p, amount, true);
        }
        int food = p.getFoodData().getFoodLevel();
        if (food > 0 && food < 18 && p.isHurt() && gameTime % 80 == 0) p.heal(1f);
    }

    /** Bracer tanks: the trickle compressor turns 20 mana into 1 air while the tanks aren't full (pack mana first). */
    static void tanks(Player p) {
        ItemStack chest = SylvanArmorItem.worn(p, EquipmentSlot.CHEST, GearData.Module.BRACER_TANKS);
        int rate = VEConfig.BRACER_TANKS_TRICKLE_AIR_PER_TICK.get();
        if (chest.isEmpty() || rate <= 0) return;
        int air = BacktankUtil.getAir(chest), max = BacktankUtil.maxAir(chest);
        int n = Math.min(rate, max - air);
        if (n <= 0) return;
        if (GearPower.pay(p, chest, n * VEConfig.BRACER_TANKS_MANA_PER_AIR_TRICKLE.get()) || GearPower.free(p))
            chest.set(AllDataComponents.BACKTANK_AIR, air + n);
    }

    /** Pneumatic striders: step assist while worn; +40% sprint speed for 1 air/t (or 5 mana/t) while sprinting. */
    static void striders(Player p) {
        ItemStack legs = SylvanArmorItem.worn(p, EquipmentSlot.LEGS, GearData.Module.STRIDERS);
        boolean worn = !legs.isEmpty();
        modifier(p.getAttribute(Attributes.STEP_HEIGHT), STEP_ID, worn,
                VEConfig.STRIDER_STEP_HEIGHT.get() - 0.6, AttributeModifier.Operation.ADD_VALUE);
        boolean boost = false;
        if (worn && p.isSprinting()) {
            boost = GearPower.takeAir(p, VEConfig.STRIDER_AIR_PER_TICK.get())
                    || GearPower.pay(p, legs, VEConfig.STRIDER_MANA_FALLBACK_PER_TICK.get());
        }
        modifier(p.getAttribute(Attributes.MOVEMENT_SPEED), SPRINT_ID, boost,
                VEConfig.STRIDER_SPRINT_BOOST_MULTIPLIER.get() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void modifier(AttributeInstance inst, ResourceLocation id, boolean on, double amount,
                                 AttributeModifier.Operation op) {
        if (inst == null) return;
        AttributeModifier cur = inst.getModifier(id);
        if (on && (cur == null || cur.amount() != amount)) {
            inst.removeModifier(id);
            inst.addTransientModifier(new AttributeModifier(id, amount, op));
        } else if (!on && cur != null) {
            inst.removeModifier(id);
        }
    }

    // ------------------------------------------------------------------ boot thrusters

    /**
     * Server, from the client's {@code DeckDash} intent (midair jump or double-tapped sprint): a 25-air
     * burst along the given horizontal direction, upward when it was a jump. The server checks the boots,
     * the cooldown and the air itself; the client only says which way.
     */
    public static boolean deckDash(Player p, double dx, double dz, boolean jump) {
        if (SylvanArmorItem.worn(p, EquipmentSlot.FEET, GearData.Module.BOOT_THRUSTERS).isEmpty()) return false;
        if (p.isSpectator() || p.getAbilities().flying || p.isFallFlying() || p.isInWater() || p.onClimbable()) return false;
        boolean airborne = !p.onGround();
        if (jump && !airborne) return false;
        UUID id = p.getUUID();
        if (!airborne) AIR_BURSTS.remove(id);
        if (airborne && AIR_BURSTS.getOrDefault(id, 0) >= VEConfig.BOOT_THRUSTER_MAX_AIR_JUMPS.get()) return false;
        long now = p.level().getGameTime();
        if (now < DASH_READY.getOrDefault(p.getUUID(), Long.MIN_VALUE)) return false;
        if (!GearPower.takeAir(p, VEConfig.BOOT_THRUSTER_AIR_PER_BURST.get())) return false;
        if (airborne) AIR_BURSTS.merge(id, 1, Integer::sum);
        Vec3 dir = new Vec3(dx, 0, dz);
        if (dir.lengthSqr() < 1.0e-4 || !Double.isFinite(dir.lengthSqr())) {
            Vec3 look = p.getLookAngle();
            dir = new Vec3(look.x, 0, look.z);
        }
        dir = dir.lengthSqr() < 1.0e-4 ? Vec3.ZERO : dir.normalize();
        double v = VEConfig.BOOT_THRUSTER_BURST_SPEED.get();
        Vec3 cur = p.getDeltaMovement();
        p.setDeltaMovement(dir.x * v, jump ? Math.max(cur.y, 0) + 0.55 : Math.max(cur.y, 0.2), dir.z * v);
        p.hurtMarked = true;
        p.fallDistance = 0;
        DASH_READY.put(p.getUUID(), now + VEConfig.BOOT_THRUSTER_COOLDOWN_TICKS.get());
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), dev.viridianengineering.registry.VESounds.BOOT_DASH.get(), SoundSource.PLAYERS, 0.5f, 1.6f);
        return true;
    }

    // ------------------------------------------------------------------ misc

    public static void onFall(LivingFallEvent e) {
        if (e.getEntity() instanceof Player p && GaiaKineticLanceItem.consumeLungeLanding(p)) e.setDamageMultiplier(0f);
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        GearPower.forget(id);
        DASH_READY.remove(id);
        AIR_BURSTS.remove(id);
    }

    public static void clear() {
        DASH_READY.clear();
        AIR_BURSTS.clear();
        GearPower.clear();
        GaiaKineticLanceItem.clear();
    }

    static boolean isWearing(LivingEntity e, GearData.Module m) {
        return !SylvanArmorItem.worn(e, m.slot, m).isEmpty();
    }
}
