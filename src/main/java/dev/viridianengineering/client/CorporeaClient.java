package dev.viridianengineering.client;

import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.registry.VECorporea;
import dev.viridianengineering.content.corporea.ArcaneSparkEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

@EventBusSubscriber(modid=ViridianEngineering.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class CorporeaClient {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(VECorporea.TERMINAL_MENU.get(),CorporeaTerminalScreen::new); }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) { event.registerEntityRenderer(VECorporea.SPARK_ENTITY.get(),SparkRenderer::new); }
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(VEPylonModel.ARCHIVE_LAYER, VEPylonModel::createBodyLayer);
        event.registerLayerDefinition(VEPylonModel.LEVITATION_LAYER, VEPylonModel::createBodyLayer);
    }
    private static class SparkRenderer extends EntityRenderer<ArcaneSparkEntity> {
        private final net.minecraft.client.renderer.entity.ItemRenderer items;
        SparkRenderer(EntityRendererProvider.Context context) { super(context); items=context.getItemRenderer(); }
        @Override public ResourceLocation getTextureLocation(ArcaneSparkEntity entity) { return net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS; }
        @Override public void render(ArcaneSparkEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
            pose.pushPose(); pose.translate(0,Math.sin((entity.tickCount+partial)*.1)*.08,0); pose.mulPose(entityRenderDispatcher.cameraOrientation()); pose.mulPose(Axis.YP.rotationDegrees(180)); pose.scale(.6f,.6f,.6f);
            items.renderStatic(new ItemStack(VECorporea.ARCANE_SPARK.get()),ItemDisplayContext.FIXED,15728880,OverlayTexture.NO_OVERLAY,pose,buffers,entity.level(),entity.getId());
            pose.popPose();super.render(entity,yaw,partial,pose,buffers,light);
        }
    }
}
