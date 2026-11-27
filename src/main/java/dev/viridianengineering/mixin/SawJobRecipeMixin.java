package dev.viridianengineering.mixin;

import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import dev.viridianengineering.content.autocraft.CraftPattern;
import dev.viridianengineering.content.autocraft.CraftingScheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = SawBlockEntity.class, remap = false)
abstract class SawJobRecipeMixin {
    @Inject(method = "getRecipes", at = @At("RETURN"), cancellable = true)
    private void ve$selectRequestedRecipe(CallbackInfoReturnable<List<RecipeHolder<? extends Recipe<?>>>> cir) {
        var saw = (SawBlockEntity) (Object) this;
        if (!(saw.getLevel() instanceof ServerLevel level)) return;
        var wanted = CraftingScheduler.claimedRecipe(level, saw.getBlockPos(), CraftPattern.Op.CUT);
        if (wanted == null) return;
        var matches = new ArrayList<>(cir.getReturnValue());
        matches.removeIf(holder -> !wanted.equals(holder.id()));
        cir.setReturnValue(matches);
    }
}
