package dev.viridianengineering.mixin;

import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.content.autocraft.CraftPattern;
import dev.viridianengineering.content.autocraft.CraftingScheduler;
import dev.viridianengineering.content.infusion.AlfheimMixerHandler;
import dev.viridianengineering.content.infusion.IAlfheimMixer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = MechanicalMixerBlockEntity.class, remap = false)
abstract class MechanicalMixerBlockEntityMixin extends BasinOperatingBlockEntity implements IAlfheimMixer {
    @Unique private boolean ve$module;
    @Unique private boolean ve$terrasteelUnlocked;
    @Unique private int ve$mana;

    protected MechanicalMixerBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override public boolean ve$hasAlfheimModule() { return ve$module; }
    @Override public boolean ve$isTerrasteelUnlocked() { return ve$terrasteelUnlocked; }
    @Override public void ve$unlockTerrasteel() { ve$terrasteelUnlocked = true; }
    @Override public int ve$getMana() { return ve$mana; }

    @Override
    public void ve$setAlfheimModule(boolean attached) {
        ve$module = attached;
        if (!attached) ve$terrasteelUnlocked = false;
        if (basinChecker != null) basinChecker.scheduleUpdate();
    }

    @Override
    public void ve$setMana(int mana) {
        int previous = ve$mana;
        ve$mana = Math.clamp(mana, 0, VEConfig.ALFHEIM_MIXER_MANA_CAPACITY.get());
        if (ve$mana > previous && basinChecker != null) basinChecker.scheduleUpdate();
    }

    @Inject(method = "getMatchingRecipes", at = @At("RETURN"))
    private void ve$includeInfusion(CallbackInfoReturnable<List<Recipe<?>>> cir) {
        if (!(level instanceof ServerLevel server)) return;
        if (ve$module) getBasin().ifPresent(basin -> AlfheimMixerHandler.preferInfusion(
                (MechanicalMixerBlockEntity) (Object) this, basin, cir.getReturnValue()));
        var wanted = CraftingScheduler.claimedRecipe(server, worldPosition.below(2), CraftPattern.Op.MIX);
        if (wanted != null) {
            Recipe<?> recipe = server.getRecipeManager().byKey(wanted).map(holder -> holder.value()).orElse(null);
            cir.getReturnValue().removeIf(found -> found != recipe);
        }
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void ve$write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (!ve$module) return;
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Module", true);
        tag.putInt("Mana", ve$mana);
        tag.putBoolean("TerrasteelUnlocked", ve$terrasteelUnlocked);
        compound.put("ViridianAlfheim", tag);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void ve$read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        CompoundTag tag = compound.getCompound("ViridianAlfheim");
        ve$module = tag.getBoolean("Module");
        ve$mana = ve$module ? Math.clamp(tag.getInt("Mana"), 0, VEConfig.ALFHEIM_MIXER_MANA_CAPACITY.get()) : 0;
        ve$terrasteelUnlocked = ve$module && tag.getBoolean("TerrasteelUnlocked");
        if (ve$module && basinChecker != null) basinChecker.scheduleUpdate();
    }
}
