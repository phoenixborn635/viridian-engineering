package dev.viridianengineering.mixin;

import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import dev.viridianengineering.content.infusion.AlfheimMixerHandler;
import dev.viridianengineering.content.infusion.IAlfheimMixer;
import dev.viridianengineering.content.infusion.ManaInfusionRecipe;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BasinOperatingBlockEntity.class, remap = false)
abstract class BasinOperatingBlockEntityMixin {
    @Inject(method = "matchBasinRecipe", at = @At("HEAD"), cancellable = true)
    private void ve$checkInfusion(Recipe<?> candidate, CallbackInfoReturnable<Boolean> cir) {
        if (candidate instanceof ManaInfusionRecipe infusion
                && (!((Object) this instanceof MechanicalMixerBlockEntity mixer) || !AlfheimMixerHandler.canProcess(mixer, infusion)))
            cir.setReturnValue(false);
    }

    @Redirect(method = "applyBasinRecipe", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/processing/basin/BasinRecipe;apply(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;Lnet/minecraft/world/item/crafting/Recipe;)Z"))
    private boolean ve$applyInfusion(BasinBlockEntity basin, Recipe<?> recipe) {
        if (!(recipe instanceof ManaInfusionRecipe infusion)) return BasinRecipe.apply(basin, recipe);
        if (!((Object) this instanceof MechanicalMixerBlockEntity mixer) || !AlfheimMixerHandler.canProcess(mixer, infusion))
            return false;
        if (!BasinRecipe.apply(basin, recipe)) return false;
        IAlfheimMixer module = (IAlfheimMixer) mixer;
        module.ve$setMana(module.ve$getMana() - infusion.getMana());
        mixer.setChanged();
        dev.viridianengineering.content.infusion.AlfheimPressHandler.playInfusion(mixer);
        return true;
    }
}
