package dev.viridianengineering.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressRenderer;
import dev.viridianengineering.client.VEPartials;
import dev.viridianengineering.content.infusion.IAlfheimPress;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MechanicalPressRenderer.class, remap = false)
abstract class AlfheimPressRendererMixin {
    @Inject(method = "renderSafe(Lcom/simibubi/create/content/kinetics/press/MechanicalPressBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", at = @At("TAIL"))
    private void ve$renderAlfheimModule(MechanicalPressBlockEntity press, float partialTicks, PoseStack pose,
                                       MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        if (!((IAlfheimPress) press).ve$hasAlfheimModule()) return;
        var state = press.getBlockState();
        var facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        CachedBuffers.partialFacing(VEPartials.ALFHEIM_PRESS_MODULE, state, facing)
                .light(light).renderInto(pose, buffers.getBuffer(RenderType.cutoutMipped()));
        CachedBuffers.partialFacing(VEPartials.ALFHEIM_PRESS_CRYSTAL, state, facing)
                .light(LightTexture.FULL_BRIGHT).renderInto(pose, buffers.getBuffer(RenderType.translucent()));
    }
}
