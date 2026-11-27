package dev.viridianengineering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.viridianengineering.content.drive.ArchivePylonBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;

/**
 * Floating crystal above the Archive Pylon pedestal (the Botania-style pylon look the user asked
 * for, original art). It turns faster the more drives are active, and turns slowly and unlit when the
 * pylon is empty.
 */
public class ArchivePylonRenderer implements BlockEntityRenderer<ArchivePylonBlockEntity> {

    private static final float PEDESTAL_TOP = 13 / 16f;
    private final VEPylonModel model;

    public ArchivePylonRenderer(BlockEntityRendererProvider.Context context) {
        model = new VEPylonModel(context.bakeLayer(VEPylonModel.ARCHIVE_LAYER));
    }

    @Override
    public void render(ArchivePylonBlockEntity pylon, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        int active = 0;
        for (int i = 0; i < ArchivePylonBlockEntity.SLOTS; i++) if (pylon.isActive(i)) active++;
        float time = pylon.getLevel() == null ? 0 : (pylon.getLevel().getGameTime() + partialTicks) * (active == 0 ? 0.25f : 0.6f + active * 0.2f);
        int glow = active == 0 ? light : 0xF000F0;

        ms.pushPose();
        // The gem's bottom tip hovers 2 px above the pedestal's crystal socket.
        ms.translate(0, PEDESTAL_TOP + 2 / 16f, 0);
        model.renderAnimated(ms, buffers.getBuffer(RenderType.entityTranslucent(VEPylonModel.ARCHIVE_TEXTURE)),
                glow, OverlayTexture.NO_OVERLAY, time);
        ms.popPose();
        cartridges(pylon, ms, buffers);
    }

    /** Bay i's left edge (x, pixels) on a NORTH-facing pylon; tools/gen_archive_pylon.py BAY_COLS mirrored. */
    private static final int[] BAY_X = {12, 9, 5, 2};

    /**
     * Each mounted drive as a 3D cartridge standing out of its bay, tier-coloured crystal edges and LED
     * lit while the drive is active (a drive locked in another pylon shows dark).
     */
    private static void cartridges(ArchivePylonBlockEntity pylon, PoseStack ms, MultiBufferSource buffers) {
        net.minecraft.world.level.Level level = pylon.getLevel();
        if (level == null) return;
        net.minecraft.world.level.block.state.BlockState state = pylon.getBlockState();
        if (!state.hasProperty(dev.viridianengineering.content.drive.ArchivePylonBlock.FACING)) return;
        net.minecraft.core.Direction facing = state.getValue(dev.viridianengineering.content.drive.ArchivePylonBlock.FACING);
        float yaw = FormedRenderers.yaw(facing);
        int light = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, pylon.getBlockPos().relative(facing));
        for (int i = 0; i < ArchivePylonBlockEntity.SLOTS; i++) {
            net.minecraft.world.item.ItemStack drive = pylon.driveIn(i);
            if (drive.isEmpty()) continue;
            dev.engine_room.flywheel.lib.model.baked.PartialModel piece = cartridge(drive);
            net.createmod.catnip.render.CachedBuffers.partial(piece, state)
                    .center().rotateYDegrees(yaw).uncenter().translate(BAY_X[i] / 16f, 0, 0)
                    .light(light).renderInto(ms, FormedRenderers.solid(buffers));
            if (!pylon.isActive(i)) continue;
            net.createmod.catnip.render.CachedBuffers.partial(VEPartials.crystal(piece), state)
                    .center().rotateYDegrees(yaw).uncenter().translate(BAY_X[i] / 16f, 0, 0)
                    .light(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT).renderInto(ms, FormedRenderers.crystal(buffers));
        }
    }

    private static dev.engine_room.flywheel.lib.model.baked.PartialModel cartridge(net.minecraft.world.item.ItemStack drive) {
        if (drive.is(dev.viridianengineering.registry.VEStorage.TERRA_DRIVE_1M.get())) return VEPartials.DRIVE_1M;
        if (drive.is(dev.viridianengineering.registry.VEStorage.TERRA_DRIVE_256K.get())) return VEPartials.DRIVE_256K;
        if (drive.is(dev.viridianengineering.registry.VEStorage.TERRA_DRIVE_64K.get())) return VEPartials.DRIVE_64K;
        return VEPartials.DRIVE_16K;
    }

    @Override
    public AABB getRenderBoundingBox(ArchivePylonBlockEntity pylon) {
        return new AABB(pylon.getBlockPos()).expandTowards(0, 1.5, 0);
    }
}
