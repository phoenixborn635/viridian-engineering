package dev.viridianengineering.compat;

import dev.viridianengineering.api.endpoint.MachineEndpointKey;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The only place that converts between world positions and {@link MachineEndpointKey}s.
 * Keeping Sable calls in one class means an API change in Sable is a one-file fix.
 */
public final class SableCompat {

    private SableCompat() {}

    @Nullable
    public static SubLevel containingSubLevel(Level level, BlockPos pos) {
        return Sable.HELPER.getContaining(level, pos);
    }

    public static boolean isOnShip(Level level, BlockPos pos) {
        return containingSubLevel(level, pos) != null;
    }

    @Nullable
    public static SubLevel trackedShip(net.minecraft.world.entity.Entity entity) {
        SubLevel ship = ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension) entity)
                .sable$getTrackingSubLevel();
        return ship != null && !ship.isRemoved() ? ship : null;
    }

    public static MachineEndpointKey endpointKey(Level level, BlockPos pos, Direction face) {
        SubLevel subLevel = containingSubLevel(level, pos);
        if (subLevel == null) {
            return new MachineEndpointKey(level.dimension(), null, pos, face);
        }
        BlockPos anchor = subLevel.getPlot().getCenterBlock();
        return new MachineEndpointKey(level.dimension(), subLevel.getUniqueId(), pos.subtract(anchor), face);
    }

    /**
     * Turns a key back into the block's current position in {@code level}.
     *
     * @return null if the key is for another dimension, or its ship no longer exists.
     */
    @Nullable
    public static BlockPos resolve(Level level, MachineEndpointKey key) {
        if (!level.dimension().equals(key.dimension())) return null;
        if (key.sableSubLevelId() == null) return key.localPos();

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        SubLevel subLevel = container.getSubLevel(key.sableSubLevelId());
        if (subLevel == null || subLevel.isRemoved()) return null;
        return subLevel.getPlot().getCenterBlock().offset(key.localPos());
    }

    /** A point in {@code level}'s block space (a ship's plot, or terrain) as a world-space point. */
    public static net.minecraft.world.phys.Vec3 toWorld(Level level, net.minecraft.world.phys.Vec3 point) {
        SubLevel subLevel = containingSubLevel(level, BlockPos.containing(point));
        return subLevel == null ? point : subLevel.logicalPose().transformPosition(point);
    }

    /** A direction in a ship's plot space as a world-space direction (identity on terrain). */
    public static net.minecraft.world.phys.Vec3 toWorldNormal(Level level, BlockPos pos, net.minecraft.world.phys.Vec3 dir) {
        SubLevel subLevel = containingSubLevel(level, pos);
        return subLevel == null ? dir : subLevel.logicalPose().transformNormal(dir);
    }
}
