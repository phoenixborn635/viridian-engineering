package dev.viridianengineering.content.battery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Spec 3.4 step e: one pending detonation. {@code controllerPos} is the controller's
 * {@link dev.viridianengineering.api.endpoint.MachineEndpointKey#localPos() local position}
 * (relative to its Sable ship when {@code subLevelId} is set), so the failure follows a moving ship.
 * Saved in {@link BatteryFailureSavedData}; the roll is never repeated after a restart.
 */
public record BatteryFailureEntry(UUID eventId, ResourceKey<Level> dim, @Nullable UUID subLevelId, BlockPos controllerPos,
                                  int validLayers, double fillFraction, long detonationGameTick) {

    public static final Codec<BatteryFailureEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("event").forGetter(BatteryFailureEntry::eventId),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dim").forGetter(BatteryFailureEntry::dim),
            UUIDUtil.CODEC.optionalFieldOf("sublevel").forGetter(e -> Optional.ofNullable(e.subLevelId())),
            BlockPos.CODEC.fieldOf("controller").forGetter(BatteryFailureEntry::controllerPos),
            Codec.INT.fieldOf("layers").forGetter(BatteryFailureEntry::validLayers),
            Codec.DOUBLE.fieldOf("fill").forGetter(BatteryFailureEntry::fillFraction),
            Codec.LONG.fieldOf("deadline").forGetter(BatteryFailureEntry::detonationGameTick)
    ).apply(i, (id, dim, sub, pos, layers, fill, tick) -> new BatteryFailureEntry(id, dim, sub.orElse(null), pos, layers, fill, tick)));

    public dev.viridianengineering.api.endpoint.MachineEndpointKey key() {
        return new dev.viridianengineering.api.endpoint.MachineEndpointKey(dim, subLevelId, controllerPos, net.minecraft.core.Direction.UP);
    }
}
