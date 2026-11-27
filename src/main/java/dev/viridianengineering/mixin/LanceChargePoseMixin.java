package dev.viridianengineering.mixin;

import dev.viridianengineering.client.GearClient;
import dev.viridianengineering.content.gear.GaiaKineticLanceItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
abstract class LanceChargePoseMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void ve$lanceCharge(LivingEntity entity, float swing, float amount, float age, float yaw, float pitch, CallbackInfo ci) {
        if (!(entity instanceof Player) || !entity.isUsingItem() || !(entity.getUseItem().getItem() instanceof GaiaKineticLanceItem)) return;
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        HumanoidArm hand = entity.getUsedItemHand() == InteractionHand.MAIN_HAND ? entity.getMainArm() : entity.getMainArm().getOpposite();
        boolean right = hand == HumanoidArm.RIGHT;
        var arm = right ? model.rightArm : model.leftArm;
        float c = GearClient.windUpProgress(entity);
        c = c * c * (3 - 2 * c);
        arm.xRot = model.head.xRot - 1.72f + 0.45f * c;
        arm.yRot = model.head.yRot * .4f + (right ? -.22f : .22f) * c;
        arm.zRot = (right ? .10f : -.10f) * c;
        arm.z += 1.5f * c;
    }
}
