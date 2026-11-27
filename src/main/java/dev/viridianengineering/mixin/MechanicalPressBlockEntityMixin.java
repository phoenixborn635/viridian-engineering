package dev.viridianengineering.mixin;

import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import dev.viridianengineering.content.infusion.AlfheimPressHandler;
import dev.viridianengineering.content.infusion.IAlfheimPress;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Gives every Mechanical Press an optional Alfheim Infusion Module and a mana buffer,
 * and lets mana-infusion recipes take priority over pressing when the module is fitted.
 */
@Mixin(value = MechanicalPressBlockEntity.class, remap = false)
public abstract class MechanicalPressBlockEntityMixin implements IAlfheimPress {

    @Unique private static final String VE$TAG = "ViridianAlfheim";

    @Unique private boolean ve$module;
    @Unique private int ve$mana;

    @Override public boolean ve$hasAlfheimModule() { return ve$module; }
    @Override public void ve$setAlfheimModule(boolean attached) { ve$module = attached; }
    @Override public int ve$getMana() { return ve$mana; }
    @Override public void ve$setMana(int mana) { ve$mana = Math.max(0, Math.min(AlfheimPressHandler.MANA_CAPACITY, mana)); }

    @Inject(method = "tryProcessOnBelt", at = @At("HEAD"), cancellable = true)
    private void ve$infuseOnBelt(TransportedItemStack input, List<ItemStack> outputList, boolean simulate,
                                 CallbackInfoReturnable<Boolean> cir) {
        Boolean result = AlfheimPressHandler.processOnBelt((MechanicalPressBlockEntity) (Object) this,
                input, outputList, simulate);
        if (result != null) cir.setReturnValue(result);
    }

    @Inject(method = "tryProcessInWorld", at = @At("HEAD"), cancellable = true)
    private void ve$infuseInWorld(ItemEntity itemEntity, boolean simulate, CallbackInfoReturnable<Boolean> cir) {
        Boolean result = AlfheimPressHandler.processInWorld((MechanicalPressBlockEntity) (Object) this,
                itemEntity, simulate);
        if (result != null) cir.setReturnValue(result);
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void ve$write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (!ve$module) return;
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Module", true);
        tag.putInt("Mana", ve$mana);
        compound.put(VE$TAG, tag);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void ve$read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        CompoundTag tag = compound.getCompound(VE$TAG);
        ve$module = tag.getBoolean("Module");
        ve$setMana(tag.getInt("Mana"));
    }
}
