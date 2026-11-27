package dev.viridianengineering.client;

import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/** Spins a pump's own cog model (Mana Pump / Manasteel Pump). */
public class VEPumpRenderer extends KineticBlockEntityRenderer<PumpBlockEntity> {

    private final PartialModel cog;

    public VEPumpRenderer(BlockEntityRendererProvider.Context context, PartialModel cog) {
        super(context);
        this.cog = cog;
    }

    public static VEPumpRenderer mana(BlockEntityRendererProvider.Context c) { return new VEPumpRenderer(c, VEPartials.MANA_PUMP_COG); }

    public static VEPumpRenderer manasteel(BlockEntityRendererProvider.Context c) { return new VEPumpRenderer(c, VEPartials.MANASTEEL_PUMP_COG); }

    /**
     * Create skips its BER when Flywheel visualization is on and relies on a registered visual; our
     * pumps have none, so the cog was never drawn. Always draw it here.
     */
    @Override
    protected void renderSafe(PumpBlockEntity be, float pt, com.mojang.blaze3d.vertex.PoseStack ms,
                              net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay) {
        BlockState state = getRenderedBlockState(be);
        renderRotatingBuffer(be, getRotatedModel(be, state), ms, buffers.getBuffer(getRenderType(be, state)), light);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(PumpBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing(cog, state);
    }
}
