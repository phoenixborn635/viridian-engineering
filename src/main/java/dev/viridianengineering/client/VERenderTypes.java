package dev.viridianengineering.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Our own render types. {@link #GLOW} is for the 3D effect meshes ({@link FxMesh}): untextured
 * POSITION_COLOR, additive (like lightning), both faces drawn and no depth write, so overlapping
 * shells and plumes add up into soft light instead of hiding each other. Depth is still tested, so
 * terrain in front of an effect hides it.
 */
public final class VERenderTypes extends RenderType {

    private VERenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling,
                          boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    public static final RenderType GLOW = create("viridian_engineering_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1 << 18, false, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setOutputState(WEATHER_TARGET)
                    .createCompositeState(false));
}
