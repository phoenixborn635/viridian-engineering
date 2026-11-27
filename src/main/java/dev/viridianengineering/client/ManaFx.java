package dev.viridianengineering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Mana effects built from Botania's own mana-wisp sprite (the soft glow its mana particles use),
 * drawn as solid shapes instead of loose particles: a camera-facing disc reads as a glowing sphere,
 * a camera-facing ribbon as a solid laser. Additive and full-bright ({@code RenderType.eyes}).
 */
public final class ManaFx {

    private ManaFx() {}

    public static final ResourceLocation WISP = ResourceLocation.fromNamespaceAndPath("botania", "textures/misc/wisp_large.png");

    /** Botania mana blue, its pale core, and the Gaia pink used by the cannon. */
    public static final int MANA = 0x37c8ff, MANA_CORE = 0xe8fbff, GAIA = 0xff6fcf, TERRA = 0x6dff9e;

    /**
     * The glow buffer: our additive 3D mesh type ({@link VERenderTypes#GLOW}). Every glow is real
     * geometry now; the wisp sprite is only used for the battery's window light ({@link #spriteBuffer}).
     */
    static VertexConsumer buffer(MultiBufferSource buffers) {
        return buffers.getBuffer(VERenderTypes.GLOW);
    }

    static VertexConsumer spriteBuffer(MultiBufferSource buffers) {
        return buffers.getBuffer(RenderType.eyes(WISP));
    }

    private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float vv, int rgb, float a) {
        vc.addVertex(pose, x, y, z)
                .setColor((rgb >> 16 & 255) / 255f * a, (rgb >> 8 & 255) / 255f * a, (rgb & 255) / 255f * a, 1f)
                .setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }

    /**
     * A soft glowing ball of radius {@code r} at (x, y, z): a real 3D sphere (from {@link #buffer})
     * that is brightest face-on and fades to nothing at its silhouette, so it reads as a volume of
     * light from every angle instead of a flat sprite.
     */
    public static void orb(PoseStack ms, VertexConsumer vc, double x, double y, double z, float r, int rgb, float alpha) {
        float t = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        FxMesh.sphere(vc, ms.last().pose(), FxMesh.camera(ms), (float) x, (float) y, (float) z, r * 0.8f,
                FxMesh.mix(rgb, 0xffffff, 0.25f), rgb, alpha * 0.4f, 0f, 0.04f, t, 10);
    }

    /**
     * A solid laser from {@code a} to {@code b} (pose-relative), {@code width} wide: a ribbon turned
     * toward the camera, the wisp sprite's cross-section giving it soft glowing edges.
     */
    public static void beam(PoseStack ms, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 camRel, float width, int rgb, float alpha) {
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1e-6) return;
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = dir.cross(camRel.subtract(mid));
        if (side.lengthSqr() < 1e-6) side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(width / 2);
        PoseStack.Pose pose = ms.last();
        v(vc, pose, (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z), 0, 0.5f, rgb, alpha);
        v(vc, pose, (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z), 1, 0.5f, rgb, alpha);
        v(vc, pose, (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z), 1, 0.5f, rgb, alpha);
        v(vc, pose, (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z), 0, 0.5f, rgb, alpha);
    }

    /** Camera position relative to a block entity's render origin. */
    public static Vec3 cameraRel(net.minecraft.core.BlockPos origin) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return cam.subtract(Vec3.atLowerCornerOf(origin));
    }

    /** The reactor core's halo while it runs. */
    static void coreGlow(PoseStack ms, MultiBufferSource buffers, double x, double y, double z, float time) {
        VertexConsumer vc = buffer(buffers);
        float pulse = 0.8f + 0.2f * (float) Math.sin(time * 0.2);
        orb(ms, vc, x, y, z, 1.6f * pulse, MANA, 0.55f);
        orb(ms, vc, x, y, z, 0.7f, MANA_CORE, 0.8f);
    }

    /** Stored mana: a glowing column behind each side panel's window, as high as the battery is full. */
    static void batteryLevel(PoseStack ms, MultiBufferSource buffers, int bx, int by, int bz, int layers, double fill, float time) {
        if (fill <= 0 || layers <= 0) return;
        VertexConsumer vc = spriteBuffer(buffers);
        PoseStack.Pose pose = ms.last();
        float y0 = by + 1 / 16f;
        float y1 = y0 + (float) ((layers - 2 / 16f) * Math.min(1, fill));
        float a = 0.65f + 0.15f * (float) Math.sin(time * 0.15);
        float lo = 5 / 16f, hi = 11 / 16f, in = 2.9f / 16f;
        // north, south (planes of constant z) and west, east (constant x); u runs across the window
        quadZ(vc, pose, bz - 1 + in, bx + lo, bx + hi, y0, y1, a);
        quadZ(vc, pose, bz + 2 - in, bx + lo, bx + hi, y0, y1, a);
        quadX(vc, pose, bx - 1 + in, bz + lo, bz + hi, y0, y1, a);
        quadX(vc, pose, bx + 2 - in, bz + lo, bz + hi, y0, y1, a);
        float band = Math.max(y0, y1 - 2 / 16f);
        quadZ(vc, pose, bz - 1 + in - .002f, bx + lo, bx + hi, band, y1, 1);
        quadZ(vc, pose, bz + 2 - in + .002f, bx + lo, bx + hi, band, y1, 1);
        quadX(vc, pose, bx - 1 + in - .002f, bz + lo, bz + hi, band, y1, 1);
        quadX(vc, pose, bx + 2 - in + .002f, bz + lo, bz + hi, band, y1, 1);
    }

    private static void quadZ(VertexConsumer vc, PoseStack.Pose p, float z, float x0, float x1, float y0, float y1, float a) {
        v(vc, p, x0, y0, z, 0, 0.5f, MANA, a); v(vc, p, x1, y0, z, 1, 0.5f, MANA, a);
        v(vc, p, x1, y1, z, 1, 0.5f, MANA, a); v(vc, p, x0, y1, z, 0, 0.5f, MANA, a);
        v(vc, p, x0, y1, z, 0, 0.5f, MANA, a); v(vc, p, x1, y1, z, 1, 0.5f, MANA, a);
        v(vc, p, x1, y0, z, 1, 0.5f, MANA, a); v(vc, p, x0, y0, z, 0, 0.5f, MANA, a);
    }

    private static void quadX(VertexConsumer vc, PoseStack.Pose p, float x, float z0, float z1, float y0, float y1, float a) {
        v(vc, p, x, y0, z0, 0, 0.5f, MANA, a); v(vc, p, x, y0, z1, 1, 0.5f, MANA, a);
        v(vc, p, x, y1, z1, 1, 0.5f, MANA, a); v(vc, p, x, y1, z0, 0, 0.5f, MANA, a);
        v(vc, p, x, y1, z0, 0, 0.5f, MANA, a); v(vc, p, x, y1, z1, 1, 0.5f, MANA, a);
        v(vc, p, x, y0, z1, 1, 0.5f, MANA, a); v(vc, p, x, y0, z0, 0, 0.5f, MANA, a);
    }

}
