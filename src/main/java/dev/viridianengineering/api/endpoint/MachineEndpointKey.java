package dev.viridianengineering.api.endpoint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Spec 0.2 "Sublevel-Safe Coordinates Everywhere".
 * <p>
 * The permanent identity of any machine, port, pylon or multiblock. When the block
 * sits on a Sable airship, {@code sableSubLevelId} is that ship's id and
 * {@code localPos} is relative to the ship's plot anchor, so the key stays valid
 * however far the ship flies. On ordinary terrain {@code sableSubLevelId} is null and
 * {@code localPos} is the world position.
 * <p>
 * Build keys with {@link dev.viridianengineering.compat.SableCompat#endpointKey}, never by hand.
 */
public record MachineEndpointKey(
        ResourceKey<Level> dimension,
        @Nullable UUID sableSubLevelId,
        BlockPos localPos,
        Direction accessFace
) {

    public MachineEndpointKey {
        localPos = localPos.immutable();
    }

    public boolean isOnShip() {
        return sableSubLevelId != null;
    }

    public static final Codec<MachineEndpointKey> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(MachineEndpointKey::dimension),
            UUIDUtil.CODEC.optionalFieldOf("sublevel").forGetter(k -> Optional.ofNullable(k.sableSubLevelId())),
            BlockPos.CODEC.fieldOf("pos").forGetter(MachineEndpointKey::localPos),
            Direction.CODEC.fieldOf("face").forGetter(MachineEndpointKey::accessFace)
    ).apply(i, (dim, sub, pos, face) -> new MachineEndpointKey(dim, sub.orElse(null), pos, face)));

    public static final StreamCodec<ByteBuf, MachineEndpointKey> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), MachineEndpointKey::dimension,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), k -> Optional.ofNullable(k.sableSubLevelId()),
            BlockPos.STREAM_CODEC, MachineEndpointKey::localPos,
            Direction.STREAM_CODEC, MachineEndpointKey::accessFace,
            (dim, sub, pos, face) -> new MachineEndpointKey(dim, sub.orElse(null), pos, face)
    );
}
