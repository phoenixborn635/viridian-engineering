package dev.viridianengineering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.viridianengineering.content.gear.GearData;
import dev.viridianengineering.content.gear.SylvanArmorItem;
import dev.viridianengineering.content.gear.SylvanTiara;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import velrondevs.botania.common.item.equipment.bauble.FlugelTiaraItem;

/**
 * Sylvan armor 3D attachments (spec 6, item 5), drawn on the player model and moving with its limbs:
 * forearm air cylinders (Pneumatic Bracer Tanks; a second, slimmer pair with two modules), the dorsal
 * livingrock mana core (Livingrock Mana Pack), leg piston linkages (Pneumatic Striders) and ankle exhaust
 * nozzles (Pneumatic Boot Thrusters); and the Flugel Tiara wings, in their normal back position, for a
 * helmet with a tiara fitted (Botania's own tiara renderer).
 * <p>
 * The attachment models (models/block/gear/*, tools/gen_gear_art.py) are authored in block space
 * centred on (8, 8, 8): +y up, north (-z) facing away from the body part they sit on.
 */
public class SylvanArmorLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final FlugelTiaraItem.Renderer TIARA = new FlugelTiaraItem.Renderer();

    public SylvanArmorLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ms, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        PlayerModel<AbstractClientPlayer> model = getParentModel();

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (SylvanArmorItem.isSylvan(chest)) {
            int tanks = GearData.count(chest, GearData.Module.BRACER_TANKS);
            if (tanks > 0) {
                // Outside the 3D vambrace (SylvanArmorGeometry: right x -3.5..1.5, z -2.5..2.5, y 1.5..10,
                // cuff to x -3.8, pauldron rim to x -4.15 above y 1.6). Tank depth behind its centre = 2 px.
                attach(ms, buffers, light, model.rightArm, VEPartials.GEAR_BRACER_TANK, -5.8, 5.6, 0, 90, 0.75f);
                attach(ms, buffers, light, model.leftArm, VEPartials.GEAR_BRACER_TANK, 5.8, 5.6, 0, -90, 0.75f);
                if (tanks > 1) {
                    attach(ms, buffers, light, model.rightArm, VEPartials.GEAR_BRACER_TANK, -1, 6, 4.3, 180, 0.65f);
                    attach(ms, buffers, light, model.leftArm, VEPartials.GEAR_BRACER_TANK, 1, 6, 4.3, 180, 0.65f);
                }
            }
            if (GearData.has(chest, GearData.Module.MANA_PACK))
                attach(ms, buffers, light, model.body, VEPartials.GEAR_MANA_PACK, 0, 5, 5.2, 0, 0.9f); // behind the cuirass/spine (z 4)
        }
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        if (SylvanArmorItem.isSylvan(legs) && GearData.has(legs, GearData.Module.STRIDERS)) {
            // outside the greave/tasset (x -2.7) and the boot cuff (x -3, y 7..9)
            attach(ms, buffers, light, model.rightLeg, VEPartials.GEAR_STRIDER, -4.3, 5, 0, 90, 0.9f);
            attach(ms, buffers, light, model.leftLeg, VEPartials.GEAR_STRIDER, 4.3, 5, 0, -90, 0.9f);
        }
        ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);
        if (SylvanArmorItem.isSylvan(feet) && GearData.has(feet, GearData.Module.BOOT_THRUSTERS)) {
            // behind the boot cuff (z 3)
            attach(ms, buffers, light, model.rightLeg, VEPartials.GEAR_BOOT_THRUSTER, 0, 10, 4.8, 0, 0.8f);
            attach(ms, buffers, light, model.leftLeg, VEPartials.GEAR_BOOT_THRUSTER, 0, 10, 4.8, 0, 0.8f);
        }

        if (SylvanTiara.active(player)) {
            ItemStack tiara = SylvanTiara.standIn(player);
            ms.pushPose();
            TIARA.doRender(model, tiara, player, ms, buffers, light, limbSwing, limbSwingAmount, partialTick,
                    ageInTicks, netHeadYaw, headPitch);
            ms.popPose();
        }
    }

    /**
     * Draws {@code partial} on {@code part}: (x, y, z) is where its centre sits in the part's model space
     * (pixels, y down, +z behind the player); {@code yaw} turns the model's north face (its outward side)
     * to face away from the part.
     */
    private static void attach(PoseStack ms, MultiBufferSource buffers, int light, ModelPart part, PartialModel partial,
                               double x, double y, double z, float yaw, float scale) {
        ms.pushPose();
        part.translateAndRotate(ms);
        ms.translate(x / 16.0, y / 16.0, z / 16.0);
        ms.scale(scale, -scale, -scale); // model space is y-down and faces -z; block models are y-up
        ms.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
        ms.translate(-0.5, -0.5, -0.5);
        var air = Blocks.AIR.defaultBlockState();
        CachedBuffers.partial(partial, air).light(light).overlay(OverlayTexture.NO_OVERLAY)
                .renderInto(ms, buffers.getBuffer(Sheets.cutoutBlockSheet()));
        PartialModel gem = VEPartials.crystal(partial);
        if (gem != null)
            CachedBuffers.partial(gem, air).light(LightTexture.FULL_BRIGHT).overlay(OverlayTexture.NO_OVERLAY)
                    .renderInto(ms, buffers.getBuffer(Sheets.translucentCullBlockSheet()));
        ms.popPose();
    }
}
