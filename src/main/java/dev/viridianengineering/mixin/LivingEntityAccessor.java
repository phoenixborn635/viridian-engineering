package dev.viridianengineering.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** GameTests: FakePlayers never tick, so a test sets the attack-cooldown timer to make a full-strength strike. */
@Mixin(value = LivingEntity.class, remap = false)
public interface LivingEntityAccessor {

    @Accessor(value = "attackStrengthTicker", remap = false)
    void ve$setAttackStrengthTicker(int ticks);
}
