package dev.viridianengineering.content.elementium;

import dev.viridianengineering.config.VEConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Elementium Smart Funnel: pulls items from the inventory behind it and pushes them into the inventory
 * it faces, every {@code funnelInterval} ticks, up to {@code funnelBatch} items. Request-aware: while
 * its linked Request Interface has open requests it moves only what is still wanted.
 */
public class SmartFunnelBlockEntity extends ElementiumLogisticsBlockEntity {

    private int cooldown;

    public SmartFunnelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public Direction front() { return getBlockState().getValue(BlockStateProperties.FACING); }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (--cooldown > 0) return;
        cooldown = VEConfig.ELEMENTIUM_FUNNEL_INTERVAL.get();
        transfer();
    }

    /** @return items moved */
    public int transfer() {
        Direction front = front();
        IItemHandler source = level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.relative(front.getOpposite()), front);
        IItemHandler target = level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.relative(front), front.getOpposite());
        if (source == null || target == null) return 0;

        RequestInterfaceBlockEntity requests = activeRequests();
        int budget = VEConfig.ELEMENTIUM_FUNNEL_BATCH.get();
        int moved = 0;
        for (int slot = 0; slot < source.getSlots() && budget > 0; slot++) {
            ItemStack peek = source.extractItem(slot, budget, true);
            int n = allowance(peek, Math.min(budget, peek.getCount()), requests);
            if (n <= 0) continue;
            ItemStack offer = peek.copyWithCount(n);
            ItemStack rest = ItemHandlerHelper.insertItem(target, offer, true);
            int fits = n - rest.getCount();
            if (fits <= 0) continue;
            ItemStack taken = source.extractItem(slot, fits, false);
            if (taken.isEmpty()) continue;
            ItemStack left = ItemHandlerHelper.insertItem(target, taken, false);
            int done = taken.getCount() - left.getCount();
            if (!left.isEmpty()) source.insertItem(slot, left, false); // put back anything that did not fit after all
            charge(taken, done, requests);
            moved += done;
            budget -= done;
            if (requests != null && !requests.hasRequests()) requests = null; // all fulfilled
        }
        if (moved > 0) setChanged();
        return moved;
    }
}
