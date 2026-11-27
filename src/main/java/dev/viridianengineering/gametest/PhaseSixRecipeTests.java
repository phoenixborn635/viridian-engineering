package dev.viridianengineering.gametest;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.content.infusion.AlfheimMixerHandler;
import dev.viridianengineering.content.infusion.IAlfheimMixer;
import dev.viridianengineering.content.infusion.ManaInfusionRecipe;
import dev.viridianengineering.registry.VEFluids;
import dev.viridianengineering.registry.VEGear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.armortrim.TrimMaterials;
import net.minecraft.world.item.armortrim.TrimPatterns;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import velrondevs.botania.api.BotaniaCapabilities;
import velrondevs.botania.registry.BotaniaItems;

import java.util.List;
import java.util.UUID;

@GameTestHolder(ViridianEngineering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhaseSixRecipeTests {
    private static final String TEMPLATE = "empty_12x6x12";

    @GameTest(template = TEMPLATE)
    public static void survivalRecipesLoadWithLockedYields(GameTestHelper h) {
        var level = h.getLevel();
        var manager = level.getRecipeManager();
        h.assertTrue(!manager.hadErrorsLoading(), "Recipes reported errors on data-pack load");
        h.assertTrue(result(h, "crafting/terrasteel_casing").getCount() == 9, "Terrasteel casing yield");
        h.assertTrue(result(h, "crafting/terrasteel_mana_pipe").getCount() == 16, "Terrasteel mana pipe yield");
        var assembly = (SequencedAssemblyRecipe) manager.byKey(ViridianEngineering.asResource("sequenced_assembly/mana_mechanism"))
                .orElseThrow().value();
        h.assertTrue(assembly.getLoops() == 5 && assembly.getSequence().size() == 4, "Mana Mechanism sequence");
        var liquid = (BasinRecipe) manager.byKey(ViridianEngineering.asResource("mixing/liquid_mana")).orElseThrow().value();
        h.assertTrue(liquid.getFluidResults().getFirst().is(VEFluids.LIQUID_MANA.get())
                && liquid.getFluidResults().getFirst().getAmount() == 250, "Liquid Mana must have a repeatable supply");
        var recipeOutputs = manager.getRecipes().stream()
                .map(holder -> holder.value().getResultItem(level.registryAccess()).getItem()).collect(java.util.stream.Collectors.toSet());
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            if (!entry.getKey().location().getNamespace().equals(ViridianEngineering.MOD_ID)
                    || entry.getValue() == VEGear.INCOMPLETE_MANA_MECHANISM.get()) continue;
            h.assertTrue(recipeOutputs.contains(entry.getValue()), "Missing survival recipe for " + entry.getKey().location());
        }
        h.succeed();
    }

    private static ItemStack result(GameTestHelper h, String id) {
        return h.getLevel().getRecipeManager().byKey(ViridianEngineering.asResource(id)).orElseThrow()
                .value().getResultItem(h.getLevel().registryAccess());
    }

    @GameTest(template = TEMPLATE)
    public static void sylvanCraftingGridKeepsTerrasteelComponents(GameTestHelper h) {
        var level = h.getLevel();
        var registries = level.registryAccess();
        var enchantment = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
        var trim = new ArmorTrim(registries.lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(TrimMaterials.IRON),
                registries.lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(TrimPatterns.SENTRY));
        var name = Component.literal("The Living Forge");
        var bases = List.of(BotaniaItems.terrasteelHelm, BotaniaItems.terrasteelChest, BotaniaItems.terrasteelLegs, BotaniaItems.terrasteelBoots);
        var outputs = List.of(VEGear.SYLVAN_HELMET.get(), VEGear.SYLVAN_CHESTPLATE.get(), VEGear.SYLVAN_LEGGINGS.get(), VEGear.SYLVAN_BOOTS.get());
        var patterns = List.of(List.of("GTG", " M "), List.of("GMG", "GTG", " M "),
                List.of("GGG", " T ", " M "), List.of("GTG", "M  "));
        for (int piece = 0; piece < bases.size(); piece++) {
            ItemStack base = new ItemStack(bases.get(piece));
            base.enchant(enchantment, 4);
            base.set(DataComponents.TRIM, trim);
            base.set(DataComponents.CUSTOM_NAME, name);
            var rows = patterns.get(piece);
            ListTag grid = new ListTag();
            for (int y = 0; y < rows.size(); y++) for (int x = 0; x < rows.get(y).length(); x++) {
                ItemStack stack = switch (rows.get(y).charAt(x)) {
                    case 'T' -> base;
                    case 'G' -> VEGear.GAIA_SPIRIT_SHEET.asStack();
                    case 'M' -> VEGear.MANA_MECHANISM.asStack();
                    default -> ItemStack.EMPTY;
                };
                if (stack.isEmpty()) continue;
                CompoundTag cell = new CompoundTag();
                cell.putInt("x", x);
                cell.putInt("y", rows.size() - y - 1);
                cell.put("item", stack.save(registries));
                grid.add(cell);
            }
            CompoundTag grouped = new CompoundTag();
            grouped.put("Grid", grid);
            ItemStack forged = RecipeGridHandler.tryToApplyRecipe(level, RecipeGridHandler.GroupedItems.read(grouped, registries));
            h.assertTrue(forged != null && forged.is(outputs.get(piece)) && forged.getEnchantmentLevel(enchantment) == 4
                    && trim.equals(forged.get(DataComponents.TRIM)) && name.equals(forged.get(DataComponents.CUSTOM_NAME)),
                    "Mechanical Crafting lost a component on " + base);
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void terrasteelMixerRequiresProgressionPowerAndMana(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos basinPos = new BlockPos(4, 1, 4);
        BlockPos mixerPos = basinPos.above(2);
        BlockPos cogPos = mixerPos.east();
        BlockPos motorPos = cogPos.above();
        h.setBlock(basinPos, AllBlocks.BASIN.get());
        h.setBlock(mixerPos, AllBlocks.MECHANICAL_MIXER.get());
        h.setBlock(cogPos, AllBlocks.COGWHEEL.getDefaultState().setValue(CogWheelBlock.AXIS, Direction.Axis.Y));
        h.setBlock(motorPos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.DOWN));
        var mixer = (MechanicalMixerBlockEntity) h.getBlockEntity(mixerPos);
        var module = (IAlfheimMixer) mixer;
        var basin = (BasinBlockEntity) h.getBlockEntity(basinPos);
        var motor = (CreativeMotorBlockEntity) h.getBlockEntity(motorPos);
        var player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "VE_Alloy_Test"), ClientInformation.createDefault());
        h.runAfterDelay(2, () -> {
            motor.generatedSpeed.setValue(256);
            module.ve$setAlfheimModule(true);
            var receiver = level.getCapability(BotaniaCapabilities.MANA_RECEIVER, h.absolutePos(mixerPos), Direction.NORTH);
            h.assertTrue(receiver != null, "Upgraded Mixer does not accept piped or spreader mana");
            receiver.receiveMana(350_000);
            basin.getInputInventory().setStackInSlot(0, new ItemStack(BotaniaItems.manaSteel));
            basin.getInputInventory().setStackInSlot(1, new ItemStack(BotaniaItems.manaPearl));
            basin.getInputInventory().setStackInSlot(2, new ItemStack(BotaniaItems.manaDiamond));
            AlfheimMixerHandler.unlockIfQualified(mixer, player);
            h.assertTrue(!module.ve$isTerrasteelUnlocked(), "Mixer accepted a player without the Botania advancement");
        });
        h.runAfterDelay(65, () -> {
            h.assertTrue(basin.getOutputInventory().isEmpty() && module.ve$getMana() == 350_000, "A locked Mixer processed Terrasteel");
            var advancement = level.getServer().getAdvancements().get(ManaInfusionRecipe.TERRASTEEL_WEAPON);
            h.assertTrue(advancement != null, "Pinned Botania advancement ID is missing");
            h.assertTrue(player.getAdvancements().getOrStartProgress(advancement).grantProgress("terrablade"),
                    "Pinned Botania weapon criterion is missing");
            motor.generatedSpeed.setValue(128);
            AlfheimMixerHandler.unlockIfQualified(mixer, player);
            h.assertTrue(module.ve$isTerrasteelUnlocked(), "Botania advancement did not authorize the Mixer");
        });
        h.runAfterDelay(125, () -> {
            h.assertTrue(basin.getOutputInventory().isEmpty() && module.ve$getMana() == 350_000, "Mixer made Terrasteel below 256 RPM");
            module.ve$setMana(349_999);
            motor.generatedSpeed.setValue(256);
        });
        h.runAfterDelay(190, () -> {
            h.assertTrue(basin.getOutputInventory().isEmpty() && module.ve$getMana() == 349_999, "Mixer made Terrasteel short of mana");
            level.getCapability(BotaniaCapabilities.MANA_RECEIVER, h.absolutePos(mixerPos), Direction.NORTH).receiveMana(8);
        });
        h.runAfterDelay(270, () -> {
            ItemStack output = basin.getOutputInventory().getStackInSlot(0);
            h.assertTrue(output.is(BotaniaItems.terrasteel) && output.getCount() == 1 && module.ve$getMana() == 7,
                    "Authorized Mixer did not produce one ingot for exactly 350,000 mana: " + output + ", " + module.ve$getMana());
            h.assertTrue(basin.getInputInventory().isEmpty(), "Alloy did not consume all three ingredients");
            h.succeed();
        });
    }
}
