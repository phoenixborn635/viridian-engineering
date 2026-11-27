package dev.viridianengineering.content.gear;

import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.registry.VEDamageTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gaia Kinetic Lance (spec 3.8, LOCKED). Powered by mana (its own 50,000 buffer, then the player's
 * mana items) and compressed air (worn Backtank or tank-fitted Sylvan Chestplate) together.
 * <ul>
 *   <li><b>Momentum thrust</b> (left click): 9 damage, +1.5 reach. A full-strength strike while moving
 *       adds up to +11 kinetic damage, linear in the wielder's world speed (sprinting, diving, riding a
 *       moving ship), for 250 mana + 15 air. Without both, the strike lands at 9 and costs nothing.</li>
 *   <li><b>Wind-lunge</b> (hold, release): 30 ticks of wind-up at 100 mana + 2 air per tick, then the
 *       wielder is flung along their look and an 8-block piercing gale deals 18 with heavy knockback to
 *       everything in its path; 40-tick cooldown. Releasing early spends what was channelled.</li>
 * </ul>
 */
public class GaiaKineticLanceItem extends Item {

    public static final net.minecraft.resources.ResourceLocation REACH_ID = ViridianEngineering.asResource("lance_reach");

    /** Server: ticks of wind-up paid so far, per wielder. */
    private static final Map<UUID, Integer> CHARGE = new HashMap<>();
    /** Server: players mid-lunge (no fall damage until they land). */
    private static final Map<UUID, Long> LUNGING = new HashMap<>();

    public GaiaKineticLanceItem(Properties props) {
        super(props);
    }

    // ------------------------------------------------------------------ attributes

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID,
                        VEConfig.LANCE_BASE_DAMAGE.get() - 1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.8,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(REACH_ID,
                        VEConfig.LANCE_REACH_BONUS.get(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    // ------------------------------------------------------------------ momentum thrust

    /** Spec: the kinetic bonus for a strike at {@code speed} blocks/tick (0 .. +11, linear). */
    public static double velocityBonus(double speedBlocksPerTick) {
        double bps = speedBlocksPerTick * 20.0;
        return VEConfig.LANCE_MAX_VELOCITY_BONUS.get() * Math.min(1.0, bps / VEConfig.LANCE_FULL_BONUS_SPEED.get());
    }

    @Override
    public float getAttackDamageBonus(Entity target, float damage, DamageSource source) {
        if (!(source.getEntity() instanceof Player p) || p.level().isClientSide) return 0f;
        ItemStack lance = p.getMainHandItem();
        if (!lance.is(this)) return 0f;
        float bonus = (float) strike(p, lance, damage);
        if (bonus > 0 && p instanceof net.minecraft.server.level.ServerPlayer sp) {
            var c = target.getBoundingBox().getCenter();
            var look = p.getLookAngle();
            dev.viridianengineering.network.VENetwork.lanceFx(sp, new dev.viridianengineering.network.VENetwork.LanceFx(1,
                    c.x, c.y, c.z, (float) look.x, (float) look.y, (float) look.z,
                    (float) (bonus / VEConfig.LANCE_MAX_VELOCITY_BONUS.get())));
        }
        return bonus;
    }

    /**
     * Server: decides and pays a strike's kinetic bonus. {@code damage} is the strike's damage after the
     * attack-cooldown scaling: only a (nearly) full-strength strike is empowered, so spam-clicking
     * neither gets the bonus nor drains anything.
     */
    public static double strike(Player p, ItemStack lance, float damage) {
        double bonus = velocityBonus(GearPower.speed(p));
        if (bonus < 0.5 || damage < 0.8 * VEConfig.LANCE_BASE_DAMAGE.get()) return 0;
        int mana = GearPower.discounted(p, lance, VEConfig.LANCE_STRIKE_MANA_COST.get());
        int air = VEConfig.LANCE_AIR_PER_STRIKE.get();
        if (!GearPower.free(p) && (GearPower.air(p) < air || !GearPower.canPay(p, lance, mana))) return 0;
        GearPower.pay(p, lance, mana);
        GearPower.takeAir(p, air);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), dev.viridianengineering.registry.VESounds.LANCE_THRUST.get(), SoundSource.PLAYERS, 1f, 0.7f);
        return bonus;
    }

    // ------------------------------------------------------------------ wind-lunge

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72_000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (!level.isClientSide) CHARGE.remove(player.getUUID());
        if (player instanceof net.minecraft.server.level.ServerPlayer server && isNetworkPlayer(server))
            dev.viridianengineering.network.VENetwork.lanceCharge(server, 0, false);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide || !(entity instanceof Player p)) return;
        int before = charge(p);
        boolean paid = channel(p, stack);
        if (p instanceof net.minecraft.server.level.ServerPlayer server && isNetworkPlayer(server)
                && (charge(p) != before || !paid && level.getGameTime() % 5 == 0)) {
            dev.viridianengineering.network.VENetwork.lanceCharge(server, charge(p), !paid);
        }
    }

    private static boolean isNetworkPlayer(net.minecraft.server.level.ServerPlayer player) {
        return !(player instanceof net.neoforged.neoforge.common.util.FakePlayer) && player.connection != null;
    }

    /** One tick of wind-up: pays this tick's share of the 3,000 mana and 60 air (the remainder lands on the last tick). */
    public static boolean channel(Player p, ItemStack lance) {
        int ticks = VEConfig.LANCE_LUNGE_CHARGE_TICKS.get();
        int done = CHARGE.getOrDefault(p.getUUID(), 0);
        if (done >= ticks) return true; // fully wound: holding costs nothing
        int mana = share(GearPower.discounted(p, lance, VEConfig.LANCE_LUNGE_MANA_COST.get()), ticks, done);
        int air = share(VEConfig.LANCE_LUNGE_AIR_COST.get(), ticks, done);
        if (!GearPower.free(p) && (GearPower.air(p) < air || !GearPower.canPay(p, lance, mana))) return false;
        GearPower.pay(p, lance, mana);
        GearPower.takeAir(p, air);
        CHARGE.put(p.getUUID(), done + 1);
        if (done + 1 == ticks)
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), dev.viridianengineering.registry.VESounds.LANCE_CHARGE.get(), SoundSource.PLAYERS, 0.8f, 1.4f);
        return true;
    }

    private static int share(int total, int ticks, int done) {
        return (int) ((long) total * (done + 1) / ticks - (long) total * done / ticks);
    }

    public static int charge(Player p) {
        return CHARGE.getOrDefault(p.getUUID(), 0);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide || !(entity instanceof Player p)) return;
        release(p, stack);
    }

    /** Server: release. A full wind-up lunges and fires the gale; returns the entities the gale hit. */
    public static List<LivingEntity> release(Player p, ItemStack lance) {
        int done = CHARGE.getOrDefault(p.getUUID(), 0);
        CHARGE.remove(p.getUUID());
        if (done < VEConfig.LANCE_LUNGE_CHARGE_TICKS.get()) return List.of();
        Vec3 look = p.getLookAngle();
        p.setDeltaMovement(look.scale(VEConfig.LANCE_LUNGE_SPEED.get()).add(0, VEConfig.LANCE_LUNGE_LIFT.get(), 0));
        p.hurtMarked = true; // sends the new motion to the client, which owns the player's movement
        p.fallDistance = 0;
        LUNGING.put(p.getUUID(), p.level().getGameTime());
        p.getCooldowns().addCooldown(lance.getItem(), VEConfig.LANCE_COOLDOWN_TICKS.get());
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), dev.viridianengineering.registry.VESounds.LANCE_LUNGE.get(), SoundSource.PLAYERS, 1.2f, 1.4f);
        return gale(p, look);
    }

    /** The 8-block piercing gale: every living thing along the look ray (stopped by blocks) takes 18 and is thrown back. */
    public static List<LivingEntity> gale(Player p, Vec3 look) {
        Level level = p.level();
        Vec3 from = p.getEyePosition();
        double range = VEConfig.LANCE_GALE_RANGE.get();
        Vec3 to = from.add(look.scale(range));
        HitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (wall.getType() != HitResult.Type.MISS) to = wall.getLocation();
        double len = from.distanceTo(to);
        List<LivingEntity> hit = new ArrayList<>();
        AABB box = new AABB(from, to).inflate(1.5);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive() && !e.isSpectator())) {
            Vec3 c = e.getBoundingBox().getCenter();
            double t = Mth.clamp(c.subtract(from).dot(look), 0, len);
            double reach = 1.0 + e.getBbWidth() / 2;
            if (from.add(look.scale(t)).distanceToSqr(c) > reach * reach) continue;
            DamageSource src = VEDamageTypes.source(level, VEDamageTypes.GAIA_GALE, p, p);
            e.hurt(src, VEConfig.LANCE_GALE_DAMAGE.get().floatValue());
            double k = VEConfig.LANCE_GALE_KNOCKBACK.get();
            e.knockback(k, -look.x, -look.z);
            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.25 * k, 0));
            e.hurtMarked = true;
            hit.add(e);
        }
        // the gale's look (cone, rings, wisps) is drawn by every watching client: client.LanceEffects
        if (p instanceof net.minecraft.server.level.ServerPlayer sp)
            dev.viridianengineering.network.VENetwork.lanceFx(sp, new dev.viridianengineering.network.VENetwork.LanceFx(0,
                    from.x, from.y, from.z, (float) look.x, (float) look.y, (float) look.z, (float) len));
        return hit;
    }

    /** Fall damage is waived for a lunge until the wielder lands (boarding a ship below the launch point). */
    public static boolean consumeLungeLanding(Player p) {
        return LUNGING.remove(p.getUUID()) != null;
    }

    /** Server tick: a lunge ends once the wielder is back on the ground (or swimming / flying) a few ticks after launch. */
    public static void checkLanded(Player p) {
        Long t = LUNGING.get(p.getUUID());
        if (t == null) return;
        boolean down = p.onGround() || p.isInWater() || p.getAbilities().flying;
        if ((down && p.level().getGameTime() - t > 5) || p.level().getGameTime() - t > 400) LUNGING.remove(p.getUUID());
    }

    public static boolean isLunging(Player p) {
        return LUNGING.containsKey(p.getUUID());
    }

    public static void clear() {
        CHARGE.clear();
        LUNGING.clear();
    }

    // ------------------------------------------------------------------ item behaviour

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int cap = VEConfig.LANCE_MANA_CAPACITY.get();
        return cap <= 0 ? 0 : Math.round(13f * Math.min(1f, GearPower.ownMana(stack) / (float) cap));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x6fd3ff;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.viridian_engineering.gear.mana",
                GearPower.ownMana(stack), VEConfig.LANCE_MANA_CAPACITY.get()).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.viridian_engineering.lance.thrust").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.viridian_engineering.lance.lunge").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.viridian_engineering.lance.power").withStyle(ChatFormatting.DARK_GRAY));
    }
}
