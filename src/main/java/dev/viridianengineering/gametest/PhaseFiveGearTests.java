package dev.viridianengineering.gametest;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.content.gear.ArmorUpgradeRecipe;
import dev.viridianengineering.content.gear.GaiaKineticLanceItem;
import dev.viridianengineering.content.gear.GearData;
import dev.viridianengineering.content.gear.GearPower;
import dev.viridianengineering.content.gear.SylvanArmorItem;
import dev.viridianengineering.content.gear.SylvanEvents;
import dev.viridianengineering.content.gear.SylvanTiara;
import dev.viridianengineering.mixin.LivingEntityAccessor;
import dev.viridianengineering.registry.VEGear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import velrondevs.botania.api.BotaniaCapabilities;
import velrondevs.botania.api.mana.ManaItem;
import velrondevs.botania.api.mana.ManaItemHandler;
import velrondevs.botania.common.handler.EquipmentHandler;
import velrondevs.botania.common.item.equipment.bauble.FlugelTiaraItem;
import velrondevs.botania.registry.BotaniaItems;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Viridian spec Phase 5a: the Gaia Kinetic Lance and the Sylvan armor set, measured against the spec
 * numbers. FakePlayers never tick or move, so tests drive the per-tick hooks themselves
 * ({@link SylvanEvents#serverTick}, {@link GaiaKineticLanceItem#channel}) and set the world speed the
 * strike reads ({@link GearPower#overrideSpeed}).
 */
@GameTestHolder(ViridianEngineering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhaseFiveGearTests {

    private static final String EMPTY = "empty_12x6x12";

    // ====================================================================== helpers

    static FakePlayer player(GameTestHelper h, BlockPos rel) {
        FakePlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "[VE gear]"));
        BlockPos a = h.absolutePos(rel);
        p.moveTo(a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 0, 0);
        p.getInventory().clearContent();
        return p;
    }

    static ItemStack backtank(int air) {
        ItemStack s = AllItems.COPPER_BACKTANK.asStack();
        s.set(AllDataComponents.BACKTANK_AIR, air);
        return s;
    }

    static ItemStack lance(int mana) {
        ItemStack s = VEGear.GAIA_KINETIC_LANCE.asStack();
        GearData.setInt(s, GearData.MANA, mana);
        return s;
    }

    static ItemStack tablet(int mana) {
        ItemStack s = new ItemStack(BotaniaItems.manaTablet);
        ManaItem m = s.getCapability(BotaniaCapabilities.MANA_ITEM);
        if (m != null && mana > 0) m.addMana(mana);
        return s;
    }

    /** The mana tablet in the player's inventory ({@code Inventory.add} moves the stack, so look it up). */
    static ItemStack tab(FakePlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            if (p.getInventory().getItem(i).is(BotaniaItems.manaTablet)) return p.getInventory().getItem(i);
        return ItemStack.EMPTY;
    }

    static int mana(ItemStack s) {
        ManaItem m = s.getCapability(BotaniaCapabilities.MANA_ITEM);
        return m == null ? -1 : m.getMana();
    }

    /** FakePlayers don't apply held-item attributes by themselves. */
    static void hold(FakePlayer p, ItemStack stack) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> {
            var inst = p.getAttribute(attr);
            if (inst != null) { inst.removeModifier(mod.id()); inst.addTransientModifier(mod); }
        });
    }

    static Pig pig(GameTestHelper h, Vec3 at) {
        Pig pig = EntityType.PIG.create(h.getLevel());
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        pig.setHealth(1000);
        pig.setNoAi(true);
        pig.setNoGravity(true);
        pig.moveTo(at.x, at.y, at.z, 0, 0);
        h.getLevel().addFreshEntity(pig);
        return pig;
    }

    static float strike(GameTestHelper h, FakePlayer p, double speedBlocksPerTick) {
        GearPower.overrideSpeed(p, speedBlocksPerTick);
        Pig target = pig(h, p.position().add(0, 0, 2));
        ((LivingEntityAccessor) p).ve$setAttackStrengthTicker(100);
        p.attack(target);
        float dmg = 1000 - target.getHealth();
        target.discard();
        return dmg;
    }

    static void sylvanSet(LivingEntity e) {
        e.setItemSlot(EquipmentSlot.HEAD, VEGear.SYLVAN_HELMET.asStack());
        e.setItemSlot(EquipmentSlot.CHEST, VEGear.SYLVAN_CHESTPLATE.asStack());
        e.setItemSlot(EquipmentSlot.LEGS, VEGear.SYLVAN_LEGGINGS.asStack());
        e.setItemSlot(EquipmentSlot.FEET, VEGear.SYLVAN_BOOTS.asStack());
    }

    static void done(GameTestHelper h, FakePlayer p) {
        GearPower.overrideSpeed(p, null);
        h.succeed();
    }

    // ====================================================================== lance: momentum thrust

    /** Spec 3.8: 9 at rest; +11 x (speed / full-bonus speed) for 250 mana + 15 air; 20 max; nothing without air. */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void lanceStrikeScalesWithSpeed(GameTestHelper h) {
        FakePlayer p = player(h, new BlockPos(2, 1, 2));
        p.setItemSlot(EquipmentSlot.CHEST, backtank(900));
        ItemStack lance = lance(50_000);
        hold(p, lance);
        double full = VEConfig.LANCE_FULL_BONUS_SPEED.get() / 20.0;

        float rest = strike(h, p, 0);
        h.assertTrue(Math.abs(rest - 9f) < 1e-3, "Strike at rest did " + rest + ", spec 9");
        h.assertTrue(GearData.getInt(lance, GearData.MANA) == 50_000, "A strike at rest drained mana");

        float half = strike(h, p, full / 2);
        h.assertTrue(Math.abs(half - 14.5f) < 1e-3, "Strike at half the full-bonus speed did " + half + ", expected 9 + 5.5");
        h.assertTrue(GearData.getInt(lance, GearData.MANA) == 49_750, "Empowered strike used " + (50_000 - GearData.getInt(lance, GearData.MANA)) + " mana, spec 250");
        h.assertTrue(BacktankUtil.getAir(p.getItemBySlot(EquipmentSlot.CHEST)) == 885, "Empowered strike used " + (900 - BacktankUtil.getAir(p.getItemBySlot(EquipmentSlot.CHEST))) + " air, spec 15");

        float max = strike(h, p, full * 3);
        h.assertTrue(Math.abs(max - 20f) < 1e-3, "Strike far above full speed did " + max + ", spec max 20");

        // the lance's own buffer runs dry: the rest comes from a mana tablet in the inventory
        GearData.setInt(lance, GearData.MANA, 100);
        ItemStack tab = tablet(10_000);
        p.getInventory().add(tab);
        strike(h, p, full);
        h.assertTrue(GearData.getInt(lance, GearData.MANA) == 0 && mana(tab(p)) == 9_850,
                "Mana not drawn buffer-first then from the tablet: lance " + GearData.getInt(lance, GearData.MANA) + ", tablet " + mana(tab(p)));

        // no compressed air: the strike lands unempowered and takes no mana
        p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        GearData.setInt(lance, GearData.MANA, 50_000);
        float noAir = strike(h, p, full);
        h.assertTrue(Math.abs(noAir - 9f) < 1e-3, "Strike without air did " + noAir + ", expected 9");
        h.assertTrue(GearData.getInt(lance, GearData.MANA) == 50_000, "Strike without air still drained mana");

        AttributeModifier reach = p.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).getModifier(GaiaKineticLanceItem.REACH_ID);
        h.assertTrue(reach != null && Math.abs(reach.amount() - 1.5) < 1e-9, "Lance doesn't add +1.5 reach");
        done(h, p);
    }

    // ====================================================================== lance: wind-lunge

    /** Spec 3.8: 30 ticks at 100 mana + 2 air, then a lunge along the look and an 8-block gale of 18; 40-tick cooldown. */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void lanceWindLungeAndGale(GameTestHelper h) {
        FakePlayer p = player(h, new BlockPos(1, 1, 6));
        p.setYRot(-90); // east
        p.setXRot(0);
        p.setYHeadRot(-90);
        p.setItemSlot(EquipmentSlot.CHEST, backtank(900));
        ItemStack lance = lance(50_000);
        hold(p, lance);
        Vec3 eye = p.getEyePosition();
        Pig inRange = pig(h, eye.add(6, -0.45, 0));
        Pig outOfRange = pig(h, eye.add(9.6, -0.45, 0));
        Pig offAxis = pig(h, eye.add(4, -0.45, 3));

        // releasing early spends what was channelled and does nothing
        for (int i = 0; i < 10; i++) h.assertTrue(GaiaKineticLanceItem.channel(p, lance), "Wind-up refused with full mana and air");
        h.assertTrue(GaiaKineticLanceItem.release(p, lance).isEmpty(), "A 10-tick wind-up fired the gale");
        h.assertTrue(GearData.getInt(lance, GearData.MANA) == 49_000, "10 ticks of wind-up used " + (50_000 - GearData.getInt(lance, GearData.MANA)) + " mana, spec 1,000");
        h.assertTrue(!p.getCooldowns().isOnCooldown(lance.getItem()), "An early release started the cooldown");

        GearData.setInt(lance, GearData.MANA, 50_000);
        p.setItemSlot(EquipmentSlot.CHEST, backtank(900));
        for (int i = 0; i < 40; i++) GaiaKineticLanceItem.channel(p, lance); // holding past full costs nothing
        h.assertTrue(GaiaKineticLanceItem.charge(p) == 30, "Wind-up stopped at " + GaiaKineticLanceItem.charge(p) + " ticks");
        h.assertTrue(GearData.getInt(lance, GearData.MANA) == 47_000, "Full wind-up used " + (50_000 - GearData.getInt(lance, GearData.MANA)) + " mana, spec 3,000");
        int air = BacktankUtil.getAir(p.getItemBySlot(EquipmentSlot.CHEST));
        h.assertTrue(air == 840, "Full wind-up used " + (900 - air) + " air, spec 60");

        List<LivingEntity> hit = GaiaKineticLanceItem.release(p, lance);
        h.assertTrue(hit.contains(inRange) && !hit.contains(outOfRange) && !hit.contains(offAxis),
                "Gale hit the wrong targets: " + hit.size() + " hit");
        h.assertTrue(Math.abs(1000 - inRange.getHealth() - 18f) < 1e-3, "Gale did " + (1000 - inRange.getHealth()) + ", spec 18");
        h.assertTrue(inRange.getDeltaMovement().x > 0.5, "Gale didn't throw the target back: " + inRange.getDeltaMovement());
        Vec3 v = p.getDeltaMovement();
        h.assertTrue(v.x > 1.4 && Math.abs(v.z) < 1e-6 && v.y > 0.4, "Lunge velocity " + v + " not along the look (east) and up");
        h.assertTrue(GaiaKineticLanceItem.isLunging(p), "Lunge doesn't waive fall damage");
        h.assertTrue(p.getCooldowns().isOnCooldown(lance.getItem()), "No 40-tick cooldown after the lunge");
        h.assertTrue(GaiaKineticLanceItem.charge(p) == 0, "Charge not reset");
        for (Pig pg : List.of(inRange, outOfRange, offAxis)) pg.discard();
        done(h, p);
    }

    // ====================================================================== sylvan armor

    /** Spec 1.2 (LOCKED): 20 armor, 14 toughness, 60% knockback resistance, 20% discount, 2 mana/t (6 with a pack). */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void sylvanSetStatsAndRegen(GameTestHelper h) {
        double[] sum = new double[3];
        for (ItemStack s : List.of(VEGear.SYLVAN_HELMET.asStack(), VEGear.SYLVAN_CHESTPLATE.asStack(),
                VEGear.SYLVAN_LEGGINGS.asStack(), VEGear.SYLVAN_BOOTS.asStack())) {
            EquipmentSlot slot = ((SylvanArmorItem) s.getItem()).getEquipmentSlot();
            s.forEachModifier(slot, (attr, mod) -> {
                if (attr.is(Attributes.ARMOR)) sum[0] += mod.amount();
                if (attr.is(Attributes.ARMOR_TOUGHNESS)) sum[1] += mod.amount();
                if (attr.is(Attributes.KNOCKBACK_RESISTANCE)) sum[2] += mod.amount();
            });
        }
        h.assertTrue(Math.abs(sum[0] - 20) < 1e-6 && Math.abs(sum[1] - 14) < 1e-6 && Math.abs(sum[2] - 0.6) < 1e-6,
                "Set gives armor " + sum[0] + ", toughness " + sum[1] + ", knockback resistance " + sum[2]);

        FakePlayer p = player(h, new BlockPos(2, 1, 2));
        sylvanSet(p);
        float d = ManaItemHandler.instance().getFullDiscountForTools(p, new ItemStack(Items.STICK));
        h.assertTrue(Math.abs(d - 0.2f) < 1e-6, "Full-set discount " + d + ", spec 0.2");
        ItemStack tab = tablet(0);
        p.getInventory().add(tab);
        for (long t = 0; t < 100; t++) SylvanEvents.serverTick(p, t);
        h.assertTrue(mana(tab(p)) == 200, "Set regenerated " + mana(tab(p)) + " mana in 100 ticks, spec 200");

        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        h.assertTrue(SylvanArmorItem.fit(chest, GearData.Module.MANA_PACK), "Mana pack didn't fit");
        for (long t = 100; t < 200; t++) SylvanEvents.serverTick(p, t);
        h.assertTrue(GearData.getInt(chest, GearData.MANA) == 600, "With a pack the set made " + GearData.getInt(chest, GearData.MANA) + " mana in 100 ticks, spec 600");
        h.assertTrue(ManaItemHandler.instance().getManaItems(p).stream().anyMatch(s -> s == chest), "The worn mana pack isn't a mana source");
        h.assertTrue(mana(chest) == 600, "Pack's ManaItem capability reads " + mana(chest));

        // one piece off: no discount, no regen
        p.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        h.assertTrue(ManaItemHandler.instance().getFullDiscountForTools(p, new ItemStack(Items.STICK)) == 0f, "Discount without the full set");
        done(h, p);
    }

    /** Spec: enchantments carry over the upgrade; the Flugel Tiara keeps its wing variant and flies without a Curios slot. */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void armorUpgradeKeepsEnchantsAndTiaraFlies(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var enchants = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> prot = enchants.getOrThrow(Enchantments.PROTECTION);
        Holder<Enchantment> resp = enchants.getOrThrow(Enchantments.RESPIRATION);

        ArmorUpgradeRecipe chestUp = new ArmorUpgradeRecipe("", CraftingBookCategory.EQUIPMENT, VEGear.SYLVAN_CHESTPLATE.asStack(),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(BotaniaItems.terrasteelChest), Ingredient.of(VEGear.GAIA_SPIRIT_SHEET.get()),
                        Ingredient.of(VEGear.MANA_MECHANISM.get())));
        ItemStack terra = new ItemStack(BotaniaItems.terrasteelChest);
        terra.enchant(prot, 4);
        CraftingInput in = CraftingInput.of(3, 1, List.of(terra, VEGear.GAIA_SPIRIT_SHEET.asStack(), VEGear.MANA_MECHANISM.asStack()));
        h.assertTrue(chestUp.matches(in, level), "Upgrade recipe doesn't match its ingredients");
        ItemStack sylvan = chestUp.assemble(in, level.registryAccess());
        h.assertTrue(sylvan.is(VEGear.SYLVAN_CHESTPLATE.get()) && sylvan.getEnchantmentLevel(prot) == 4,
                "Upgrade lost Protection IV: " + sylvan.getTagEnchantments());

        // the JSON form round-trips (what the recipe tables will use)
        var ops = RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, level.registryAccess());
        var codec = dev.viridianengineering.registry.VERecipes.ARMOR_UPGRADE_SERIALIZER.get().codec().codec();
        var json = codec.encodeStart(ops, chestUp).getOrThrow();
        ArmorUpgradeRecipe back = codec.parse(ops, json).getOrThrow();
        h.assertTrue(back.getIngredients().size() == 3 && back.getResultItem(level.registryAccess()).is(VEGear.SYLVAN_CHESTPLATE.get()),
                "armor_upgrade JSON doesn't round-trip: " + json);

        ArmorUpgradeRecipe tiaraUp = new ArmorUpgradeRecipe("", CraftingBookCategory.EQUIPMENT, VEGear.SYLVAN_HELMET.asStack(),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(VEGear.SYLVAN_HELMET.get()), Ingredient.of(BotaniaItems.flightTiara),
                        Ingredient.of(VEGear.MANA_MECHANISM.get())));
        ItemStack helm = VEGear.SYLVAN_HELMET.asStack();
        helm.enchant(resp, 3);
        ItemStack tiara = new ItemStack(BotaniaItems.flightTiara);
        CompoundTag tv = new CompoundTag();
        tv.putInt("variant", 3);
        GearData.write(tiara, tv);
        CraftingInput tin = CraftingInput.of(3, 1, List.of(helm, tiara, VEGear.MANA_MECHANISM.asStack()));
        h.assertTrue(tiaraUp.matches(tin, level), "Tiara upgrade doesn't match");
        ItemStack up = tiaraUp.assemble(tin, level.registryAccess());
        h.assertTrue(GearData.hasTiara(up) && GearData.tiaraVariant(up) == 3 && up.getEnchantmentLevel(resp) == 3,
                "Tiara upgrade: hasFlugelTiara " + GearData.hasTiara(up) + ", variant " + GearData.tiaraVariant(up) + ", Respiration " + up.getEnchantmentLevel(resp));
        CraftingInput again = CraftingInput.of(3, 1, List.of(up, new ItemStack(BotaniaItems.flightTiara), VEGear.MANA_MECHANISM.asStack()));
        h.assertTrue(!tiaraUp.matches(again, level), "A helmet that already has a tiara took a second one");

        // the upgraded helmet answers Botania's tiara lookup and grants flight (no Curios slot used)
        FakePlayer p = player(h, new BlockPos(2, 1, 2));
        p.setItemSlot(EquipmentSlot.HEAD, up);
        p.getInventory().add(tablet(100_000));
        ItemStack found = EquipmentHandler.findOrEmpty(BotaniaItems.flightTiara, p);
        h.assertTrue(found.is(BotaniaItems.flightTiara) && FlugelTiaraItem.getVariant(found) == 3, "Botania sees no tiara (or the wrong wings)");
        h.assertTrue(!SylvanTiara.realTiaraWorn(p), "Stand-in counted as a real (Curios) tiara");
        FlugelTiaraItem.updatePlayerFlyStatus(p);
        FlugelTiaraItem.updatePlayerFlyStatus(p);
        h.assertTrue(p.getAbilities().mayfly, "Flugel helmet didn't grant flight");
        p.getAbilities().flying = true;
        SylvanTiara.tick(p);
        int left = GearData.tiaraTag(p.getItemBySlot(EquipmentSlot.HEAD)).getInt("timeLeft");
        h.assertTrue(left > 0 && left < 1200, "Flight gauge not kept on the helmet (timeLeft " + left + ")");
        p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        FlugelTiaraItem.updatePlayerFlyStatus(p);
        h.assertTrue(!p.getAbilities().mayfly, "Flight stayed after the helmet came off");
        done(h, p);
    }

    /** Bracer tanks: 1,800 air per module (two hold 3,600), an air source for BacktankUtil, refilled at 20 mana per air. */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void bracerTanksHoldAndRefillAir(GameTestHelper h) {
        ItemStack chest = VEGear.SYLVAN_CHESTPLATE.asStack();
        h.assertTrue(BacktankUtil.maxAir(chest) == 0, "Chestplate without tanks holds air");
        SylvanArmorItem.fit(chest, GearData.Module.BRACER_TANKS);
        h.assertTrue(BacktankUtil.maxAir(chest) == 1800, "One tank module holds " + BacktankUtil.maxAir(chest) + ", spec 1,800");
        SylvanArmorItem.fit(chest, GearData.Module.BRACER_TANKS);
        h.assertTrue(BacktankUtil.maxAir(chest) == 3600, "Two tank modules hold " + BacktankUtil.maxAir(chest) + ", spec 3,600");
        h.assertTrue(!SylvanArmorItem.fit(chest, GearData.Module.MANA_PACK), "A third module fitted into 2 slots");
        h.assertTrue(!SylvanArmorItem.fit(VEGear.SYLVAN_BOOTS.asStack(), GearData.Module.BRACER_TANKS), "Tanks fitted onto boots");

        FakePlayer p = player(h, new BlockPos(2, 1, 2));
        p.setItemSlot(EquipmentSlot.CHEST, chest);
        p.getInventory().add(tablet(10_000));
        h.assertTrue(BacktankUtil.getAllWithAir(p).isEmpty(), "Empty tanks listed as an air source");
        for (long t = 0; t < 100; t++) SylvanEvents.serverTick(p, t);
        int air = BacktankUtil.getAir(p.getItemBySlot(EquipmentSlot.CHEST));
        h.assertTrue(air == 100 * VEConfig.BRACER_TANKS_TRICKLE_AIR_PER_TICK.get(), "Trickle made " + air + " air in 100 ticks");
        h.assertTrue(mana(tab(p)) == 10_000 - air * 20, "Trickle used " + (10_000 - mana(tab(p))) + " mana for " + air + " air (spec 20 each)");
        h.assertTrue(BacktankUtil.getAllWithAir(p).contains(p.getItemBySlot(EquipmentSlot.CHEST)), "Tanks aren't an air source for Create");
        h.assertTrue(GearPower.takeAir(p, 15) && BacktankUtil.getAir(p.getItemBySlot(EquipmentSlot.CHEST)) == air - 15, "Couldn't draw air from the tanks");
        h.assertTrue(!GearPower.takeAir(p, 10_000), "Took more air than the tanks hold");
        done(h, p);
    }

    /** Striders: 1-block step, +40% sprint for 1 air/t (5 mana/t fallback). Boot thrusters: 25-air deck-dash with a cooldown. */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void stridersAndBootThrusters(GameTestHelper h) {
        FakePlayer p = player(h, new BlockPos(2, 1, 2));
        ItemStack legs = VEGear.SYLVAN_LEGGINGS.asStack();
        SylvanArmorItem.fit(legs, GearData.Module.STRIDERS);
        ItemStack chest = VEGear.SYLVAN_CHESTPLATE.asStack();
        SylvanArmorItem.fit(chest, GearData.Module.BRACER_TANKS);
        chest.set(AllDataComponents.BACKTANK_AIR, 100);
        p.setItemSlot(EquipmentSlot.LEGS, legs);
        p.setItemSlot(EquipmentSlot.CHEST, chest);
        SylvanEvents.serverTick(p, 1);
        h.assertTrue(Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT) - 1.0) < 1e-6, "Step height " + p.getAttributeValue(Attributes.STEP_HEIGHT));

        double walk = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
        p.setSprinting(true);
        double sprint = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
        // the trickle compressor is off for this check, so only the striders use air
        int trickle = VEConfig.BRACER_TANKS_TRICKLE_AIR_PER_TICK.get();
        VEConfig.BRACER_TANKS_TRICKLE_AIR_PER_TICK.set(0);
        try {
            for (long t = 2; t < 12; t++) SylvanEvents.serverTick(p, t);
            double boosted = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
            h.assertTrue(Math.abs(boosted / sprint - 1.4) < 1e-6, "Striders made sprint x" + boosted / sprint + ", spec 1.4");
            int air = BacktankUtil.getAir(p.getItemBySlot(EquipmentSlot.CHEST));
            h.assertTrue(air == 90, "10 sprinting ticks used " + (100 - air) + " air, spec 1/t");

            // no air: mana fallback, 5/t
            p.getItemBySlot(EquipmentSlot.CHEST).set(AllDataComponents.BACKTANK_AIR, 0);
            p.getInventory().add(tablet(1_000));
            for (long t = 12; t < 22; t++) SylvanEvents.serverTick(p, t);
            h.assertTrue(mana(tab(p)) == 950, "Mana fallback used " + (1_000 - mana(tab(p))) + " in 10 ticks, spec 5/t");
            h.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) / sprint > 1.39, "No boost on the mana fallback");

            // neither: no boost
            tab(p).setCount(0); // (clearContent would also strip the armor)
            SylvanEvents.serverTick(p, 22);
            h.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - sprint) < 1e-9, "Boost without air or mana");
            p.setSprinting(false);
            SylvanEvents.serverTick(p, 23);
            h.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - walk) < 1e-9, "Speed not back to walking");
        } finally {
            VEConfig.BRACER_TANKS_TRICKLE_AIR_PER_TICK.set(trickle);
        }

        // boot thrusters
        ItemStack boots = VEGear.SYLVAN_BOOTS.asStack();
        p.setItemSlot(EquipmentSlot.FEET, boots);
        p.getItemBySlot(EquipmentSlot.CHEST).set(AllDataComponents.BACKTANK_AIR, 100);
        h.assertTrue(!SylvanEvents.deckDash(p, 1, 0, false), "Dashed without the thruster module");
        SylvanArmorItem.fit(p.getItemBySlot(EquipmentSlot.FEET), GearData.Module.BOOT_THRUSTERS);
        h.assertTrue(SylvanEvents.deckDash(p, 1, 0, false), "Deck-dash refused");
        h.assertTrue(BacktankUtil.getAir(p.getItemBySlot(EquipmentSlot.CHEST)) == 75, "Burst used " + (100 - BacktankUtil.getAir(p.getItemBySlot(EquipmentSlot.CHEST))) + " air, spec 25");
        h.assertTrue(Math.abs(p.getDeltaMovement().x - VEConfig.BOOT_THRUSTER_BURST_SPEED.get()) < 1e-6, "Burst velocity " + p.getDeltaMovement());
        h.assertTrue(!SylvanEvents.deckDash(p, 1, 0, false), "Second burst ignored the cooldown");
        h.assertTrue(BacktankUtil.getAir(p.getItemBySlot(EquipmentSlot.CHEST)) == 75, "A refused burst took air");

        p.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        SylvanEvents.serverTick(p, 30);
        h.assertTrue(Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT) - 0.6) < 1e-6, "Step assist stayed after taking the leggings off");
        done(h, p);
    }

    // ====================================================================== gate: lunge between moving ships

    /**
     * Phase 5 gate, last clause: a player in Sylvan armor with a Flugel-upgraded helmet and bracer tanks
     * wind-lunges from one moving ship onto another with the dual air-and-mana lance.
     * <p>
     * Two decks fly east side by side (pylon + full-throttle thruster each), 3 blocks of open air
     * between them. The player stands on ship A; the wind-up is paid from the bracer tanks and the lance
     * (with the Sylvan 20% discount), and the release's velocity, plus ship A's, is flown with vanilla's
     * airborne physics (FakePlayers don't move themselves) until it comes down through ship B's moving
     * deck, which must be under it.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400, skyAccess = true, batch = "viridian_p5_lunge")
    public static void gateLungeBetweenMovingShips(GameTestHelper h) {
        PhaseFiveGameTests.Site site = new PhaseFiveGameTests.Site(h);
        ServerLevel level = h.getLevel();
        List<BlockPos> a = PhaseFiveGameTests.deckWithPylon(site, 10_000);
        BlockPos sigA = PhaseFiveGameTests.thruster(site, a, new BlockPos(1, 3, 5), net.minecraft.core.Direction.WEST);
        List<BlockPos> b = new ArrayList<>();
        for (int x = 3; x <= 7; x++) for (int z = 11; z <= 15; z++) { site.set(new BlockPos(x, 2, z), Blocks.STONE); b.add(new BlockPos(x, 2, z)); }
        var pylon = dev.viridianengineering.registry.VEBlocks.MANA_LEVITATION_PYLON.getDefaultState();
        site.set(new BlockPos(5, 3, 13), pylon);
        site.set(new BlockPos(5, 4, 13), pylon.setValue(dev.viridianengineering.content.airship.AirshipBlocks.LevitationPylon.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        b.add(0, new BlockPos(5, 3, 13));
        b.add(new BlockPos(5, 4, 13));
        site.at(new BlockPos(5, 3, 13), dev.viridianengineering.content.airship.LevitationPylonBlockEntity.class).mana.set(10_000);
        BlockPos sigB = PhaseFiveGameTests.thruster(site, b, new BlockPos(1, 3, 13), net.minecraft.core.Direction.WEST);

        PhaseFiveGameTests.Ship shipA = PhaseFiveGameTests.assemble(site, a);
        PhaseFiveGameTests.Ship shipB = PhaseFiveGameTests.assemble(site, b);
        // where the decks are relative to each ship's centre of mass (ships stay level on their pylons)
        Vec3 comA0 = vec(shipA.com()), comB0 = vec(shipB.com());
        Vec3 standA = new Vec3(site.base.getX() + 6.5, site.base.getY() + 3, site.base.getZ() + 5.5).subtract(comA0);
        Vec3 deckB = new Vec3(site.base.getX() + 5.5, site.base.getY() + 3, site.base.getZ() + 13.5).subtract(comB0);

        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "[VE boarder]"));
        p.getInventory().clearContent();
        sylvanSet(p);
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        SylvanArmorItem.fit(chest, GearData.Module.BRACER_TANKS);
        chest.set(AllDataComponents.BACKTANK_AIR, 1800);
        ItemStack helm = p.getItemBySlot(EquipmentSlot.HEAD);
        CompoundTag tv = new CompoundTag();
        tv.putInt("variant", 1);
        GearData.setTiara(helm, tv);
        ItemStack lance = lance(50_000);
        hold(p, lance);

        h.runAfterDelay(10, () -> {
            level.setBlock(shipA.plot(site.abs(sigA)), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            level.setBlock(shipB.plot(site.abs(sigB)), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        });
        Vec3[] lastA = new Vec3[1];
        h.runAfterDelay(69, () -> lastA[0] = vec(shipA.com()));
        h.runAfterDelay(70, () -> {
            Vec3 comA = vec(shipA.com());
            Vec3 shipVel = comA.subtract(lastA[0]);
            h.assertTrue(shipVel.x > 0.05, "Ship A isn't moving (" + shipVel + " per tick)");
            Vec3 feet = comA.add(standA);
            p.moveTo(feet.x, feet.y, feet.z, 0, 8); // facing ship B (south), 8 degrees down
            for (int i = 0; i < 30; i++) h.assertTrue(GaiaKineticLanceItem.channel(p, lance), "Wind-up refused at tick " + i);
            int airUsed = 1800 - BacktankUtil.getAir(chest);
            int manaUsed = 50_000 - GearData.getInt(lance, GearData.MANA);
            h.assertTrue(airUsed == 60, "Wind-up drew " + airUsed + " air from the bracer tanks, spec 60");
            h.assertTrue(manaUsed == 2_400, "Wind-up used " + manaUsed + " mana, spec 3,000 less the Sylvan 20%");
            GaiaKineticLanceItem.release(p, lance);
            h.assertTrue(EquipmentHandler.findOrEmpty(BotaniaItems.flightTiara, p).is(BotaniaItems.flightTiara), "Flugel helmet not worn as a tiara");

            // fly it: vanilla airborne motion (move, then gravity 0.08 and drag 0.98 vertical / 0.91 horizontal)
            Vec3 pos = feet;
            Vec3 vel = p.getDeltaMovement().add(shipVel);
            boolean landed = false;
            for (int t = 0; t < 60 && !landed; t++) {
                Vec3 next = pos.add(vel);
                vel = new Vec3(vel.x * 0.91, (vel.y - 0.08) * 0.98, vel.z * 0.91);
                pos = next;
                // ship B keeps flying meanwhile; the test only has this tick's pose, so project it with its velocity
                Vec3 deck = vec(shipB.com()).add(deckB).add(shipVel.scale(t + 1));
                if (vel.y < 0 && pos.y <= deck.y && pos.y > deck.y - 1.5) {
                    double dx = Math.abs(pos.x - deck.x), dz = Math.abs(pos.z - deck.z);
                    h.assertTrue(dx <= 2.5 && dz <= 2.5, "Came down " + String.format("%.2f, %.2f", dx, dz) + " blocks off ship B's deck centre (deck is 5x5)");
                    landed = true;
                }
            }
            h.assertTrue(landed, "Lunge never came down onto ship B");
            PhaseFiveGameTests.remove(h, shipA);
            PhaseFiveGameTests.remove(h, shipB);
            site.release();
            h.succeed();
        });
    }

    private static Vec3 vec(org.joml.Vector3d v) {
        return new Vec3(v.x, v.y, v.z);
    }
}
