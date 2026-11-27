package dev.viridianengineering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.viridianengineering.ViridianEngineering;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Floating pylon crystal shared by the Corporea Archive Pylon (Phase 3) and the Mana Levitation
 * Pylon (Phase 5): one faceted gem (body, pointed cap, short bottom tip, turned 45 degrees so it
 * shows an edge) that bobs and slowly turns, with two small shards orbiting it. Original geometry;
 * texture from tools/gen_pylon_crystal.py (32x32).
 * <p>
 * The origin is the gem's bottom tip; {@link #renderAnimated} draws it upwards from the current
 * pose origin (block-local units), so the caller translates to where the tip should hover.
 */
@OnlyIn(Dist.CLIENT)
public class VEPylonModel {

    public static final ModelLayerLocation LEVITATION_LAYER =
            new ModelLayerLocation(ViridianEngineering.asResource("mana_levitation_pylon"), "main");
    public static final ModelLayerLocation ARCHIVE_LAYER =
            new ModelLayerLocation(ViridianEngineering.asResource("corporea_archive_pylon"), "main");

    public static final ResourceLocation LEVITATION_TEXTURE =
            ViridianEngineering.asResource("textures/entity/mana_levitation_pylon.png");
    public static final ResourceLocation ARCHIVE_TEXTURE =
            ViridianEngineering.asResource("textures/entity/corporea_archive_pylon.png");

    private final ModelPart gem;
    private final ModelPart shards;

    public VEPylonModel(ModelPart root) {
        this.gem = root.getChild("gem");
        this.shards = root.getChild("shards");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // Model space is y-down (entity convention): negative y is up. Body -3..-11; the tip and cap each sink
        // 0.25 px into the body so no faces are coplanar (that z-fought).
        PartDefinition gem = root.addOrReplaceChild("gem", CubeListBuilder.create(), PartPose.rotation(0, (float) Math.PI / 4, 0));
        gem.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(16, 6).addBox(-1, -3.25f, -1, 2, 3, 2), PartPose.ZERO);
        gem.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-2, -11, -2, 4, 8, 4), PartPose.ZERO);
        gem.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(16, 0).addBox(-1.5f, -13.75f, -1.5f, 3, 3, 3), PartPose.ZERO);

        PartDefinition shards = root.addOrReplaceChild("shards", CubeListBuilder.create(), PartPose.ZERO);
        CubeListBuilder shard = CubeListBuilder.create().texOffs(24, 0).addBox(-0.5f, -1.5f, -0.5f, 1, 3, 1);
        shards.addOrReplaceChild("a", shard, PartPose.offsetAndRotation(5, -6, 0, 0, 0, 0.35f));
        shards.addOrReplaceChild("b", shard, PartPose.offsetAndRotation(-5, -8, 0, 0, 0, -0.35f));

        return LayerDefinition.create(mesh, 32, 32);
    }

    /**
     * @param time animation clock (game time + partial ticks, pre-scaled by the caller; 0 = still)
     */
    public void renderAnimated(PoseStack ms, VertexConsumer buffer, int light, int overlay, float time) {
        ms.pushPose();
        ms.translate(0.5, Math.sin(time / 16.0) * 0.03, 0.5);
        ms.scale(-1, -1, 1); // entity models are authored upside down

        ms.pushPose();
        ms.mulPose(Axis.YP.rotationDegrees(time * 0.8f));
        gem.render(ms, buffer, light, overlay);
        ms.popPose();

        ms.pushPose();
        ms.mulPose(Axis.YP.rotationDegrees(-time * 2.0f));
        ms.translate(0, Math.sin(time / 9.0) * 0.04, 0);
        shards.render(ms, buffer, light, overlay);
        ms.popPose();

        ms.popPose();
    }
}
