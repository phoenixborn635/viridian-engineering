package dev.viridianengineering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.viridianengineering.ViridianEngineering;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.HashMap;

/**
 * The 3D Gaia Kinetic Lance, drawn like Create's own tools: the static lance, its crystal strips and
 * inlays full-bright, and the cog around the shaft spinning. The cog idles slowly, spins up with the
 * wielder's speed, and whirls while the wind-lunge winds up; during the wind-up a gaia glow gathers at
 * the blade with a turning rune ring at the cog, flaring when fully wound.
 */
public class LanceRenderer extends CustomRenderedItemModelRenderer {

    static final PartialModel COG = PartialModel.of(ViridianEngineering.asResource("item/gaia_kinetic_lance/cog"));
    static final PartialModel GEM = PartialModel.of(ViridianEngineering.asResource("item/gaia_kinetic_lance/gem"));

    /**
     * Cog angle and the time it was last advanced, per holder (entity id; -1 = not held, e.g. GUI).
     * Not per ItemStack: the client replaces the held stack whenever its mana or air changes, which reset
     * the angle every few degrees.
     */
    private static final Map<Integer, float[]> COGS = new HashMap<>();

    /** Called from the mod constructor on the client so the partials exist before baking. */
    public static void init() {}

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext ctx, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        renderer.render(model.getOriginalModel(), light);
        renderer.renderGlowing(GEM.get(), LightTexture.FULL_BRIGHT);

        Player holder = holder(stack);
        float charge = holder == null ? 0f : charge(holder, stack);
        float time = AnimationTickHolder.getRenderTime();
        int key = holder == null || ctx == ItemDisplayContext.GUI ? -1 : holder.getId();
        if (COGS.size() > 64) COGS.clear();
        float[] st = COGS.computeIfAbsent(key, k -> new float[]{0f, time});
        float dt = time - st[1];
        if (dt < 0f || dt > 5f) dt = 0f;                                      // world change / pause
        float speed = 3f;                                                     // degrees per tick, idle
        if (holder != null) {
            speed += (float) holder.getDeltaMovement().horizontalDistance() * 60f;
            speed += charge * 45f;
        }
        st[0] = (st[0] + speed * dt) % 360f;
        st[1] = time;
        ms.pushPose();
        ms.mulPose(Axis.YP.rotationDegrees(st[0]));
        renderer.render(COG.get(), light);
        ms.popPose();

        if (charge > 0f && ctx != ItemDisplayContext.GUI) chargeGlow(ms, buffers, charge, time);
    }

    /** The player using or holding this exact stack, if one is in the client world. */
    static Player holder(ItemStack stack) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return null;
        for (Player p : level.players())
            if (p.getMainHandItem() == stack || p.getOffhandItem() == stack) return p;
        return null;
    }

    /** Wind-up progress 0..1 while {@code p} is using this stack. */
    static float charge(Player p, ItemStack stack) {
        if (!p.isUsingItem() || p.getUseItem() != stack) return 0f;
        return GearClient.windUpProgress(p);
    }

    private static void chargeGlow(PoseStack ms, MultiBufferSource buffers, float charge, float time) {
        VertexConsumer vc = buffers.getBuffer(VERenderTypes.GLOW);
        Matrix4f m = ms.last().pose();
        Vector3f cam = FxMesh.camera(ms);
        boolean full = charge >= 1f;
        float pulse = full ? 0.75f + 0.25f * Mth.sin(time * 0.9f) : 1f;
        // gathering glow around the blade (model centre is y 8; the blade's middle is y ~25 -> +1.06)
        FxMesh.sphere(vc, m, cam, 0, 1.06f, 0, (0.15f + 0.3f * charge) * pulse, 0xffffff, LanceEffects.PINK,
                0.28f * charge, 0.25f, 0.18f, time, 10);
        FxMesh.sphere(vc, m, cam, 0, 1.06f, 0, (0.07f + 0.12f * charge) * pulse, 0xffffff, LanceEffects.GOLD,
                0.55f * charge, 0.6f, 0.05f, time, 8);
        // a rune ring turning at the cog, and a second one up the shaft once fully wound
        FxMesh.ring(vc, m, new Vector3f(0, -0.25f, 0), new Vector3f(0, 1, 0), 0.32f + 0.12f * charge, 0.07f,
                LanceEffects.GOLD, 0.6f * charge, 6, time * 0.4f);
        if (full) FxMesh.ring(vc, m, new Vector3f(0, 0.5f, 0), new Vector3f(0, 1, 0), 0.25f, 0.06f,
                LanceEffects.PINK, 0.5f * pulse, 4, -time * 0.6f);
    }
}
