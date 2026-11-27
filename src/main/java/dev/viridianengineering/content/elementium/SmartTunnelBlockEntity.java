package dev.viridianengineering.content.elementium;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Elementium Smart Tunnel: an inline router. Items enter from the back (a belt, chute, funnel or pipe
 * of items), and leave either straight ahead (the front) or out of the diverter side (the tunnel's
 * left, seen from behind). An item is diverted when it is requested by the linked Request Interface,
 * or, with no open requests, when it matches the filter; everything else passes straight through.
 * Diverting costs mana; passing through is free. With no mana, nothing is diverted.
 */
public class SmartTunnelBlockEntity extends ElementiumLogisticsBlockEntity {

    private ItemStack buffer = ItemStack.EMPTY;
    private final IItemHandler input = new Input();

    public SmartTunnelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public Direction front() { return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING); }

    public Direction divertSide() { return front().getCounterClockWise(); }

    /** Items may enter from any side except the two outputs. */
    @Nullable
    public IItemHandler inputFrom(@Nullable Direction side) {
        return side == front() || side == divertSide() ? null : input;
    }

    public ItemStack buffered() { return buffer; }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || buffer.isEmpty()) return;

        RequestInterfaceBlockEntity requests = activeRequests();
        boolean wanted = filterMatches(buffer)
                && (requests != null ? requests.outstanding(buffer) > 0 : !filtering.getFilter().isEmpty());
        if (wanted) {
            int n = allowance(buffer, buffer.getCount(), requests);
            int done = n > 0 ? push(divertSide(), buffer.copyWithCount(n)) : 0;
            if (done > 0) {
                charge(buffer, done, requests);
                buffer.shrink(done);
                changed();
            }
            return; // wanted but no mana or no room on the diverter side: hold it rather than let it slip past
        }
        int done = push(front(), buffer.copy());
        if (done > 0) {
            buffer.shrink(done);
            changed();
        }
    }

    private int push(Direction side, ItemStack stack) {
        IItemHandler target = level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.relative(side), side.getOpposite());
        if (target == null) return 0;
        ItemStack rest = ItemHandlerHelper.insertItem(target, stack, false);
        return stack.getCount() - rest.getCount();
    }

    private void changed() {
        if (buffer.isEmpty()) buffer = ItemStack.EMPTY;
        setChanged();
        sendData();
    }

    private class Input implements IItemHandler {
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { return buffer; }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return stack;
            if (!buffer.isEmpty() && !ItemStack.isSameItemSameComponents(buffer, stack)) return stack;
            int room = Math.min(stack.getMaxStackSize(), 64) - buffer.getCount();
            if (room <= 0) return stack;
            int n = Math.min(room, stack.getCount());
            if (!simulate) {
                if (buffer.isEmpty()) buffer = stack.copyWithCount(n); else buffer.grow(n);
                changed();
            }
            return stack.copyWithCount(stack.getCount() - n);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        if (level != null && !buffer.isEmpty())
            net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, buffer);
        buffer = ItemStack.EMPTY;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!buffer.isEmpty()) tag.put("Buffer", buffer.save(registries));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        buffer = tag.contains("Buffer") ? ItemStack.parseOptional(registries, tag.getCompound("Buffer")) : ItemStack.EMPTY;
    }
}
