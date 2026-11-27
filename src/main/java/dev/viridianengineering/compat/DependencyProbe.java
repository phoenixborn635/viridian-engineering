package dev.viridianengineering.compat;

import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.registry.ModCapabilities;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import velrondevs.botania.api.BotaniaCapabilities;
import velrondevs.botania.api.mana.ManaReceiver;

/**
 * Phase 0 verification gate: proves at runtime that every mandatory dependency
 * loaded and that the APIs the spec relies on actually resolve.
 * Look for lines tagged [VE Phase0] in the client and server logs.
 */
public final class DependencyProbe {

    private static final String TAG = "[VE Phase0] ";
    private static final String[] REQUIRED_MODS = {"create", "botania", "aeronautics", "simulated", "sable"};

    private DependencyProbe() {}

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            boolean ok = true;
            for (String modId : REQUIRED_MODS) {
                var info = ModList.get().getModContainerById(modId);
                if (info.isPresent()) {
                    ViridianEngineering.LOGGER.info(TAG + "mod '{}' loaded, version {}", modId,
                            info.get().getModInfo().getVersion());
                } else {
                    ViridianEngineering.LOGGER.error(TAG + "REQUIRED mod '{}' is MISSING", modId);
                    ok = false;
                }
            }

            ok &= check("Sable.HELPER", () -> Sable.HELPER.getClass().getName());
            ok &= check("Botania ManaReceiver", ManaReceiver.class::getName);
            ok &= check("Botania MANA_RECEIVER capability", () -> BotaniaCapabilities.MANA_RECEIVER.name().toString());
            ok &= check("VE MANA_PIPE_NODE capability", () -> ModCapabilities.MANA_PIPE_NODE.name().toString());
            ok &= check("VE Registrate", () -> ViridianEngineering.REGISTRATE.getModid());

            if (ok) ViridianEngineering.LOGGER.info(TAG + "ALL DEPENDENCY CHECKS PASSED");
            else ViridianEngineering.LOGGER.error(TAG + "ONE OR MORE DEPENDENCY CHECKS FAILED");
        });
    }

    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        check("Sable SubLevelContainer (overworld, server)", () -> {
            SubLevelContainer container = SubLevelContainer.getContainer(overworld);
            if (container == null) throw new IllegalStateException("container is null");
            return container.getClass().getName();
        });
    }

    private static boolean check(String what, ThrowingSupplier supplier) {
        try {
            ViridianEngineering.LOGGER.info(TAG + "{} -> OK ({})", what, supplier.get());
            return true;
        } catch (Throwable t) {
            ViridianEngineering.LOGGER.error(TAG + "{} -> FAILED", what, t);
            return false;
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        Object get() throws Exception;
    }
}
