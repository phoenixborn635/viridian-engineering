package dev.viridianengineering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.content.gear.GaiaKineticLanceItem;
import dev.viridianengineering.content.gear.GearData;
import dev.viridianengineering.content.gear.GearPower;
import dev.viridianengineering.content.gear.SylvanArmorItem;
import dev.viridianengineering.network.VENetwork;
import dev.viridianengineering.registry.VEGear;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client side of the gaia gear: the 3D Sylvan armor, the 3D lance and its animations, the attachment layer, input. */
public final class GearClient {

    private static int chargeTicks;
    private static boolean chargeStalled;

    private GearClient() {}

    public static void acceptCharge(VENetwork.LanceCharge charge) {
        chargeTicks = charge.ticks();
        chargeStalled = charge.stalled();
    }

    public static float windUpProgress(LivingEntity entity) {
        float ticks = entity == Minecraft.getInstance().player ? chargeTicks : entity.getTicksUsingItem();
        return Mth.clamp(ticks / VEConfig.LANCE_LUNGE_CHARGE_TICKS.get(), 0, 1);
    }

    private static void lanceHud(GuiGraphics gui, DeltaTracker time) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || player.isSpectator() || client.screen != null || client.options.hideGui) return;
        ItemStack lance = player.getMainHandItem().is(VEGear.GAIA_KINETIC_LANCE.get()) ? player.getMainHandItem() : player.getOffhandItem();
        if (!lance.is(VEGear.GAIA_KINETIC_LANCE.get())) return;
        float remaining = player.getCooldowns().getCooldownPercent(lance.getItem(), time.getGameTimeDeltaPartialTick(false));
        boolean charging = player.isUsingItem() && player.getUseItem() == lance;
        float progress;
        int color;
        String state;
        if (remaining > 0) {
            progress = 1 - remaining;
            color = 0xffe2bd80;
            state = "cooldown";
        } else if (charging) {
            progress = windUpProgress(player);
            color = chargeStalled ? 0xffef8e83 : 0xffbce8ba;
            state = chargeStalled ? "stalled" : progress == 1 ? "release" : "charging";
        } else {
            int mana = GearPower.discounted(player, lance, VEConfig.LANCE_LUNGE_MANA_COST.get());
            boolean ready = player.getAbilities().instabuild ||
                    GearPower.air(player) >= VEConfig.LANCE_LUNGE_AIR_COST.get() && GearPower.canPay(player, lance, mana);
            progress = ready ? 1 : 0;
            color = ready ? 0xffbce8ba : 0xffef8e83;
            state = ready ? "ready" : "stalled";
        }
        Component label = Component.translatable("hud.viridian_engineering.lance." + state);
        int right = gui.guiWidth() - 10;
        int bottom = gui.guiHeight() - 10;
        int width = Math.max(48, client.font.width(label));
        int left = right - width;
        gui.drawString(client.font, label, right - client.font.width(label), bottom - 16, color);
        gui.fill(left, bottom - 4, right, bottom, 0xd0242130);
        gui.fill(left + 1, bottom - 3, left + 1 + Math.round((width - 2) * progress), bottom - 1, color);
    }

    @EventBusSubscriber(modid = ViridianEngineering.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Mod {
        @SubscribeEvent
        public static void hud(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.CROSSHAIR, ViridianEngineering.asResource("lance"), GearClient::lanceHud);
        }

        @SubscribeEvent
        public static void layers(EntityRenderersEvent.AddLayers event) {
            for (PlayerSkin.Model skin : event.getSkins())
                if (event.getSkin(skin) instanceof PlayerRenderer r) r.addLayer(new SylvanArmorLayer(r));
        }

        @SubscribeEvent
        public static void layerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(SylvanArmorModel.OUTER, SylvanArmorModel::outerLayer);
            event.registerLayerDefinition(SylvanArmorModel.INNER, SylvanArmorModel::innerLayer);
        }

        @SubscribeEvent
        public static void extensions(RegisterClientExtensionsEvent event) {
            event.registerItem(new IClientItemExtensions() {
                @Override
                public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot,
                                                              HumanoidModel<?> original) {
                    return SylvanArmorModel.get(slot);
                }
            }, VEGear.SYLVAN_HELMET.get(), VEGear.SYLVAN_CHESTPLATE.get(), VEGear.SYLVAN_LEGGINGS.get(), VEGear.SYLVAN_BOOTS.get());
            event.registerItem(SimpleLance.create(), VEGear.GAIA_KINETIC_LANCE.get());
        }
    }

    /**
     * The lance's client extension: Create's custom renderer (3D model + spinning cog), plus the first-person
     * animation: a straight thrust instead of a sword swing, and drawing back and trembling while the
     * wind-lunge winds up. (With Better Combat installed its spear animations take over the swing.)
     */
    static final class SimpleLance extends SimpleCustomRenderer {
        private SimpleLance() { super(new LanceRenderer()); }

        static SimpleLance create() {
            com.simibubi.create.foundation.item.render.CustomRenderedItems.register(VEGear.GAIA_KINETIC_LANCE.get());
            return new SimpleLance();
        }

        @Override
        public boolean applyForgeHandTransform(PoseStack ms, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                               float partialTick, float equip, float swing) {
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            ms.translate(side * 0.56F, -0.52F + equip * -0.6F, -0.72F);
            float charge = player.isUsingItem() && player.getUseItem() == stack
                    ? windUpProgress(player) : 0f;
            float cooldown = player.getCooldowns().getCooldownPercent(stack.getItem(), partialTick);
            float followThrough = Mth.clamp(1 - (1 - cooldown) * VEConfig.LANCE_COOLDOWN_TICKS.get() / 7f, 0, 1);
            if (charge > 0f) {
                float c = charge * charge * (3 - 2 * charge);
                ms.translate(-side * 0.08F * c, -0.05F * c, 0.32F * c);       // drawn back, couched
                ms.mulPose(Axis.XP.rotationDegrees(6F * c));
                if (charge >= 1f) {                                          // fully wound: it trembles
                    float t = player.tickCount + partialTick;
                    ms.translate(Mth.sin(t * 2.7F) * 0.006F, Mth.sin(t * 3.3F) * 0.006F, 0);
                }
            } else if (followThrough > 0) {
                ms.translate(-side * 0.08F * followThrough, 0.09F * followThrough, -0.7F * followThrough);
                ms.mulPose(Axis.XP.rotationDegrees(-7F * followThrough));
            } else if (swing > 0f) {
                float k = swing < 0.3F ? swing / 0.3F : Math.max(0F, 1F - (swing - 0.3F) / 0.7F);
                k = k * k * (3 - 2 * k);                                     // smoothstep in, ease back out
                ms.translate(-side * 0.06F * k, 0.05F * k, -0.62F * k);      // straight thrust forward
                ms.mulPose(Axis.XP.rotationDegrees(-5F * k));
            }
            return true;
        }

        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, net.minecraft.world.InteractionHand hand, ItemStack stack) {
            return entity.isUsingItem() && entity.getUseItem() == stack ? HumanoidModel.ArmPose.THROW_SPEAR : null;
        }
    }

    /**
     * Client tick: boot-thruster input, and the wind-up's mana wisps spiralling into the blade of every
     * player winding a lance.
     */
    @EventBusSubscriber(modid = ViridianEngineering.MOD_ID, value = Dist.CLIENT)
    public static final class Game {
        private static boolean jumpDown, sprintDown;
        private static long lastSprintTap = -100, ticks;
        private static int airTicks;

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            ticks++;
            windUpWisps(mc.level);
            LocalPlayer p = mc.player;
            if (p == null || !p.isUsingItem() || !p.getUseItem().is(VEGear.GAIA_KINETIC_LANCE.get())) {
                chargeTicks = 0;
                chargeStalled = false;
            }
            if (p == null || mc.screen != null) { jumpDown = sprintDown = false; return; }
            airTicks = p.onGround() ? 0 : airTicks + 1;
            boolean jump = mc.options.keyJump.isDown(), sprint = mc.options.keySprint.isDown();
            boolean jumpPressed = jump && !jumpDown, sprintPressed = sprint && !sprintDown;
            jumpDown = jump;
            sprintDown = sprint;
            var boots = p.getItemBySlot(EquipmentSlot.FEET);
            if (!SylvanArmorItem.isSylvan(boots) || !GearData.has(boots, GearData.Module.BOOT_THRUSTERS)) return;
            boolean midairJump = jumpPressed && airTicks > 2
                    && !p.getAbilities().flying && !p.isInWater() && !p.onClimbable();
            boolean doubleTap = false;
            if (sprintPressed) {
                doubleTap = ticks - lastSprintTap <= 7;
                lastSprintTap = doubleTap ? -100 : ticks;
            }
            if (!midairJump && !doubleTap) return;
            float strafe = p.input.leftImpulse, forward = p.input.forwardImpulse;
            float yaw = p.getYRot() * Mth.DEG_TO_RAD;
            float sin = Mth.sin(yaw), cos = Mth.cos(yaw);
            float dx = strafe * cos - forward * sin, dz = forward * cos + strafe * sin;
            PacketDistributor.sendToServer(new VENetwork.DeckDash(dx, dz, midairJump));
        }

        private static void windUpWisps(ClientLevel level) {
            if (level == null || Minecraft.getInstance().isPaused()) return;
            for (Player p : level.players()) {
                if (!p.isUsingItem() || !(p.getUseItem().getItem() instanceof GaiaKineticLanceItem)) continue;
                float charge = windUpProgress(p);
                Vec3 look = p.getLookAngle();
                Vec3 tip = p.getEyePosition().add(look.scale(1.6)).add(0, -0.25, 0);
                int n = charge >= 1f ? 1 : 2;
                for (int i = 0; i < n; i++) {
                    double a = level.random.nextDouble() * Math.PI * 2, r = 0.8 + 0.6 * level.random.nextDouble();
                    Vec3 from = tip.add(Math.cos(a) * r, (level.random.nextDouble() - 0.5) * r, Math.sin(a) * r);
                    Vec3 v = tip.subtract(from).scale(0.12);
                    FxParticles.wisp(level, from, v, i % 2 == 0 ? LanceEffects.PINK : LanceEffects.GOLD,
                            0.12f + 0.15f * charge, 0.35f);
                }
            }
        }
    }
}
