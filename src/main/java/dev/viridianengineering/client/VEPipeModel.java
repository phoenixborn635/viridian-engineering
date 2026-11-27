package dev.viridianengineering.client;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes;
import com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes.ComponentPartials;
import com.simibubi.create.foundation.model.BakedModelWrapperWithData;
import dev.viridianengineering.content.pipe.VEPipeShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Manasteel pipe's look for our non-fluid pipes (Terrasteel mana pipe, Corporea pipe). The
 * core comes from Create's {@code BlockStateGen.pipe()} multipart; this wrapper adds Create's own
 * connection / rim / drain / junction-casing partials exactly where Create would (decided by
 * {@link VEPipeShape#attachment}, the same rules as Create's fluid pipes), and re-points their
 * quads from Create's copper {@code create:block/pipes} sprite to this pipe's sheet (same layout).
 */
public class VEPipeModel extends BakedModelWrapperWithData {

    private static final ResourceLocation COPPER = ResourceLocation.fromNamespaceAndPath("create", "block/pipes");
    private static final ModelProperty<Attachments> PROPERTY = new ModelProperty<>();
    /** Vertex layout of DefaultVertexFormat.BLOCK: pos(3) colour(1) uv0(2) uv2(1) normal(1). */
    private static final int STRIDE = 8, UV = 4;

    private final ResourceLocation target;
    private final Map<BakedQuad, BakedQuad> cache = new ConcurrentHashMap<>();
    private volatile TextureAtlasSprite cachedSprite;

    // Method references (not lambdas) keep client classes out of common registration code.
    public static VEPipeModel terrasteel(BakedModel template) {
        return new VEPipeModel(template, dev.viridianengineering.ViridianEngineering.asResource("block/terrasteel_pipes"));
    }

    public static VEPipeModel corporea(BakedModel template) {
        return new VEPipeModel(template, dev.viridianengineering.ViridianEngineering.asResource("block/corporea_pipes"));
    }

    public VEPipeModel(BakedModel template, ResourceLocation target) {
        super(template);
        this.target = target;
    }

    private record Attachments(AttachmentTypes[] sides, boolean encased) {}

    @Override
    protected ModelData.Builder gatherModelData(ModelData.Builder builder, BlockAndTintGetter world, BlockPos pos,
                                                BlockState state, ModelData blockEntityData) {
        AttachmentTypes[] sides = new AttachmentTypes[6];
        for (Direction d : Direction.values()) sides[d.get3DDataValue()] = VEPipeShape.attachment(world, pos, state, d);
        return builder.with(PROPERTY, new Attachments(sides, VEPipeShape.isJunction(state)));
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        List<ChunkRenderTypeSet> sets = new ArrayList<>();
        sets.add(super.getRenderTypes(state, rand, data));
        sets.add(AllPartialModels.FLUID_PIPE_CASING.get().getRenderTypes(state, rand, data));
        Attachments a = data.get(PROPERTY);
        if (a != null)
            for (Direction d : Direction.values())
                for (ComponentPartials partial : a.sides()[d.get3DDataValue()].partials)
                    sets.add(AllPartialModels.PIPE_ATTACHMENTS.get(partial).get(d).get().getRenderTypes(state, rand, data));
        return ChunkRenderTypeSet.union(sets);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType) {
        List<BakedQuad> quads = new ArrayList<>(super.getQuads(state, side, rand, data, renderType));
        Attachments a = data.get(PROPERTY);
        if (a != null) {
            for (Direction d : Direction.values())
                for (ComponentPartials partial : a.sides()[d.get3DDataValue()].partials)
                    quads.addAll(AllPartialModels.PIPE_ATTACHMENTS.get(partial).get(d).get().getQuads(state, side, rand, data, renderType));
            if (a.encased())
                quads.addAll(AllPartialModels.FLUID_PIPE_CASING.get().getQuads(state, side, rand, data, renderType));
        }
        return retextured(quads);
    }

    @Override
    public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
        return TriState.TRUE;
    }

    // ------------------------------------------------------------------ retexturing

    private List<BakedQuad> retextured(List<BakedQuad> quads) {
        TextureAtlasSprite to = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(target);
        if (to != cachedSprite) { // resource reload: drop quads baked against the old atlas
            cache.clear();
            cachedSprite = to;
        }
        for (int i = 0; i < quads.size(); i++) {
            BakedQuad q = quads.get(i);
            if (COPPER.equals(q.getSprite().contents().name()))
                quads.set(i, cache.computeIfAbsent(q, k -> retexture(k, to)));
        }
        return quads;
    }

    private static BakedQuad retexture(BakedQuad quad, TextureAtlasSprite to) {
        TextureAtlasSprite from = quad.getSprite();
        int[] v = quad.getVertices().clone();
        for (int i = 0; i + STRIDE <= v.length; i += STRIDE) {
            float u = Float.intBitsToFloat(v[i + UV]);
            float w = Float.intBitsToFloat(v[i + UV + 1]);
            float fu = (u - from.getU0()) / (from.getU1() - from.getU0());
            float fv = (w - from.getV0()) / (from.getV1() - from.getV0());
            v[i + UV] = Float.floatToRawIntBits(to.getU0() + fu * (to.getU1() - to.getU0()));
            v[i + UV + 1] = Float.floatToRawIntBits(to.getV0() + fv * (to.getV1() - to.getV0()));
        }
        return new BakedQuad(v, quad.getTintIndex(), quad.getDirection(), to, quad.isShade(), quad.hasAmbientOcclusion());
    }
}
