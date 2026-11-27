package dev.viridianengineering.gametest;

import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.registry.VECreativeTabs;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Regression: building creative tabs must not throw, and our tab must list every item exactly once. */
@GameTestHolder(ViridianEngineering.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreativeTabTests {

    private CreativeTabTests() {}

    @GameTest(template = "empty_12x6x12", timeoutTicks = 20)
    public static void creativeTabBuildsWithoutDuplicates(GameTestHelper h) {
        var level = h.getLevel();
        CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
        var shown = VECreativeTabs.MAIN.get().getDisplayItems();
        for (var entry : ViridianEngineering.REGISTRATE.getAll(Registries.ITEM)) {
            long n = shown.stream().filter(s -> ItemStack.isSameItem(s, new ItemStack(entry.get()))).count();
            h.assertTrue(n == 1, entry.getId() + " appears " + n + " times in the Viridian tab");
        }
        h.succeed();
    }
}
