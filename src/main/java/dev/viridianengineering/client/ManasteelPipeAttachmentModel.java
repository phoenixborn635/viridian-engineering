package dev.viridianengineering.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.simibubi.create.content.fluids.PipeAttachmentModel;

import dev.viridianengineering.ViridianEngineering;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Create's {@link PipeAttachmentModel} adds connection stubs, rim collars, end rims, drains and the
 * casing plate as partial models that are hard-wired to Create's copper {@code create:block/pipes}
 * sprite. This wrapper keeps all of Create's attachment logic and only re-points those quads at
 * {@code viridian_engineering:block/manasteel_pipes}, which uses the same UV layout.
 */
public class ManasteelPipeAttachmentModel extends PipeAttachmentModel {

    private static final ResourceLocation COPPER = ResourceLocation.fromNamespaceAndPath("create", "block/pipes");
    private static final ResourceLocation MANASTEEL = ViridianEngineering.asResource("block/manasteel_pipes");

    /** Vertex layout of DefaultVertexFormat.BLOCK: pos(3) colour(1) uv0(2) uv2(1) normal(1). */
    private static final int STRIDE = 8;
    private static final int UV = 4;

    // Attachment partials are baked once, so caching by quad identity keeps this allocation-free.
    private static final Map<BakedQuad, BakedQuad> CACHE = new ConcurrentHashMap<>();
    private static volatile TextureAtlasSprite cachedTarget;

    public static ManasteelPipeAttachmentModel withAO(BakedModel template) {
        return new ManasteelPipeAttachmentModel(template, true);
    }

    public ManasteelPipeAttachmentModel(BakedModel template, boolean ao) {
        super(template, ao);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType) {
        List<BakedQuad> quads = super.getQuads(state, side, rand, data, renderType);
        TextureAtlasSprite target = sprite(MANASTEEL);
        if (target != cachedTarget) { // resource reload: drop quads baked against the old atlas
            CACHE.clear();
            cachedTarget = target;
        }
        List<BakedQuad> out = null;
        for (int i = 0; i < quads.size(); i++) {
            BakedQuad q = quads.get(i);
            if (!COPPER.equals(q.getSprite().contents().name()))
                continue;
            if (out == null)
                out = new ArrayList<>(quads);
            out.set(i, CACHE.computeIfAbsent(q, k -> retexture(k, target)));
        }
        return out == null ? quads : out;
    }

    private static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(id);
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
