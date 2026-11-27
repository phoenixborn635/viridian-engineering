package dev.viridianengineering.content.elementium;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import dev.viridianengineering.api.mana.ManaBuffer;
import dev.viridianengineering.config.VEConfig;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shared base of the Smart Funnel and Smart Tunnel: a mana buffer (fed by Terrasteel pipes or Botania
 * spreaders), a Create filter slot on top, and the link to the nearest Request Interface.
 * <p>
 * Decision rule for each item, in order:
 * <ol>
 *   <li>no mana for the item: nothing moves;</li>
 *   <li>linked to an online Request Interface that has open requests: only requested items qualify,
 *       at most as many as are still wanted (the filter, if set, must also match);</li>
 *   <li>otherwise: the filter decides (an empty filter matches everything).</li>
 * </ol>
 */
public abstract class ElementiumLogisticsBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    public final ManaBuffer mana = new ManaBuffer(this, () -> VEConfig.ELEMENTIUM_MANA_BUFFER.get(), true, false);
    protected FilteringBehaviour filtering;
    private long movedTotal;

    protected ElementiumLogisticsBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        filtering = new FilteringBehaviour(this, new TopSlot()).withCallback(s -> sendData());
        behaviours.add(filtering);
    }

    /** The filter slot sits on the top face. */
    private static class TopSlot extends ValueBoxTransform.Sided {
        @Override protected Vec3 getSouthLocation() { return VecHelper.voxelSpace(8, 8, 15.5); }
        @Override protected boolean isSideActive(BlockState state, Direction direction) { return direction == Direction.UP; }
    }

    @Nullable
    public RequestInterfaceBlockEntity link() {
        return level == null ? null : RequestInterfaceBlockEntity.nearest(level, worldPosition);
    }

    /** Requests in force: an online, linked interface that has open requests. */
    @Nullable
    protected RequestInterfaceBlockEntity activeRequests() {
        RequestInterfaceBlockEntity link = link();
        return link != null && link.isOnline() && link.hasRequests() ? link : null;
    }

    protected boolean filterMatches(ItemStack stack) {
        return filtering == null || filtering.test(stack);
    }

    /**
     * How many of {@code stack} may move now (up to {@code max}), mana permitting. Callers then
     * {@link #charge} for what really moved.
     */
    protected int allowance(ItemStack stack, int max, @Nullable RequestInterfaceBlockEntity requests) {
        if (stack.isEmpty() || max <= 0 || !filterMatches(stack)) return 0;
        long cost = VEConfig.ELEMENTIUM_MANA_PER_ITEM.get();
        long affordable = cost <= 0 ? Long.MAX_VALUE : mana.getStoredMana() / cost;
        long n = Math.min(max, affordable);
        if (requests != null) n = Math.min(n, requests.outstanding(stack));
        return (int) Math.max(0, n);
    }

    /** Pays for {@code count} moved items and counts them against the request. */
    protected void charge(ItemStack moved, int count, @Nullable RequestInterfaceBlockEntity requests) {
        if (count <= 0) return;
        mana.consume((long) VEConfig.ELEMENTIUM_MANA_PER_ITEM.get() * count);
        if (requests != null) requests.fulfil(moved, count);
        movedTotal += count;
    }

    public long movedTotal() { return movedTotal; }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable("viridian_engineering.goggles.elementium_logistics")));
        tooltip.add(Component.literal("     ").append(Component.translatable("viridian_engineering.goggles.mana",
                mana.getStoredMana(), mana.getManaCapacity())).withStyle(ChatFormatting.AQUA));
        RequestInterfaceBlockEntity link = link();
        tooltip.add(Component.literal("     ").append(link == null
                ? Component.translatable("viridian_engineering.goggles.request_link.none").withStyle(ChatFormatting.GRAY)
                : Component.translatable(link.isOnline() ? "viridian_engineering.goggles.request_link.online"
                        : "viridian_engineering.goggles.request_link.offline", link.requests().size())
                        .withStyle(link.isOnline() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.RED)));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        mana.write(tag, "Mana");
        tag.putLong("Moved", movedTotal);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        mana.read(tag, "Mana");
        movedTotal = tag.getLong("Moved");
    }
}
