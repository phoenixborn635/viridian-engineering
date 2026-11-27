package dev.viridianengineering.client;

import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.registry.VEEntities;
import dev.viridianengineering.registry.VEFluids;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = ViridianEngineering.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PhaseFourClient {

    private PhaseFourClient() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(VEEntities.ARCANE_BOLT.get(), PhaseFourRenderers.Bolt::new);
    }

    @SubscribeEvent
    public static void fluidRendering(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override public ResourceLocation getStillTexture() { return ResourceLocation.withDefaultNamespace("block/water_still"); }
            @Override public ResourceLocation getFlowingTexture() { return ResourceLocation.withDefaultNamespace("block/water_flow"); }
            @Override public int getTintColor() { return 0xff76efb5; }
        }, VEFluids.LIQUID_MANA_TYPE.get());
    }
}
