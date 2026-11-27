package dev.viridianengineering.content.corporea;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.viridianengineering.content.autocraft.CraftingScheduler;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.registry.VEBlockEntities;
import dev.viridianengineering.registry.VEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import velrondevs.botania.api.BotaniaCapabilities;
import java.util.List;

/** Persisted transfer escrow keeps partially accepted items safe across saves and block removal. */
public class CorporeaPortBlockEntity extends SmartBlockEntity {
    public final ItemStackHandler buffer=new ItemStackHandler(1) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private ItemStack filter=ItemStack.EMPTY;
    public CorporeaPortBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state) { super(type,pos,state); }
    private com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour priority;
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Spec 3.7 optional machine priority: autocrafting prefers higher-priority machine ports.
        priority = new com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour(
                net.minecraft.network.chat.Component.translatable("gui.viridian_engineering.machine_priority"), this, new PrioritySlot())
                .between(-99, 99)
                .onlyActiveWhen(() -> getBlockState().is(VEBlocks.CORPOREA_MACHINE_PORT.get()));
        behaviours.add(priority);
    }
    public int priority() { return priority == null ? 0 : priority.getValue(); }
    public void setPriority(int value) { if (priority != null) priority.setValue(value); }

    /** Value box on the port body's free side faces (not the mouth, the neck, or a side pipe joint). */
    private static final class PrioritySlot extends com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform.Sided {
        @Override protected net.minecraft.world.phys.Vec3 getSouthLocation() { return net.minecraft.world.phys.Vec3.ZERO; }
        @Override public net.minecraft.world.phys.Vec3 getLocalOffset(net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockState state) {
            net.minecraft.core.Direction facing = state.getValue(CorporeaPortBlock.FACING);
            // Body centre sits 1 px towards the mouth from the block centre; its side faces are 5 px out.
            net.minecraft.world.phys.Vec3 centre = new net.minecraft.world.phys.Vec3(.5, .5, .5)
                    .add(net.minecraft.world.phys.Vec3.atLowerCornerOf(facing.getNormal()).scale(1 / 16d));
            return centre.add(net.minecraft.world.phys.Vec3.atLowerCornerOf(direction.getNormal()).scale(5.05 / 16d));
        }
        @Override protected boolean isSideActive(BlockState state, net.minecraft.core.Direction side) {
            return side.getAxis() != state.getValue(CorporeaPortBlock.FACING).getAxis()
                    && !state.getValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(side));
        }
    }
    public void setFilter(ItemStack stack) { filter=stack.copyWithCount(1); setChanged(); sendData(); }
    public ItemStack filter() { return filter.copy(); }
    public static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(BotaniaCapabilities.MANA_RECEIVER,VEBlockEntities.CORPOREA_PORT.get(),(be,side)->ArcaneSparkEntity.at(be.getLevel(),be.getBlockPos()));
        event.registerBlockEntity(BotaniaCapabilities.MANA_RECEIVER,dev.viridianengineering.registry.VEStorage.ARCHIVE_PYLON_BE.get(),
                (be, side) -> ArcaneSparkEntity.at(be.getLevel(), be.getBlockPos()));
        // No item capability: native Corporea Sparks may attach only to dedicated bridge ports.
    }
    @Override public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel server) || level.getGameTime()%VEConfig.CORPOREA_PORT_INTERVAL.get()!=0 || !CorporeaNetworkManager.isPowered(server,worldPosition)) return;
        var state=getBlockState();
        if (state.is(VEBlocks.CORPOREA_MACHINE_PORT.get())) return;
        var face=state.getValue(CorporeaPortBlock.FACING); BlockPos target=worldPosition.relative(face);
        if (!level.hasChunkAt(target) || CraftingScheduler.isClaimedMachine(server, target)) return;
        var external=level.getCapability(Capabilities.ItemHandler.BLOCK,target,face.getOpposite());
        if (external==null) return;
        int batch=VEConfig.CORPOREA_PORT_BATCH.get();
        TransactionContext tx=new TransactionContext(); tx.visitInventory(CorporeaNetworkManager.inventoryKey(server,target));
        if (state.is(VEBlocks.CORPOREA_INPUT_PORT.get())) {
            if (buffer.getStackInSlot(0).isEmpty()) {
                for (int slot=0;slot<external.getSlots();slot++) {
                    ItemStack candidate=external.getStackInSlot(slot);
                    if (candidate.isEmpty() || (!filter.isEmpty() && !ItemStack.isSameItemSameComponents(candidate,filter))) continue;
                    ItemStack got=external.extractItem(slot,Math.min(batch,candidate.getMaxStackSize()),false);
                    if (!got.isEmpty()) { buffer.setStackInSlot(0,got); break; }
                }
            }
            ItemStack held=buffer.getStackInSlot(0);
            if (!held.isEmpty()) buffer.setStackInSlot(0,CorporeaNetworkManager.deposit(server,worldPosition,held,CorporeaNetworkManager.Access.AUTOMATED,tx));
        } else {
            if (buffer.getStackInSlot(0).isEmpty() && !filter.isEmpty()) {
                ItemStack got=CorporeaNetworkManager.withdraw(server,worldPosition,filter,batch,CorporeaNetworkManager.Access.AUTOMATED,tx);
                buffer.setStackInSlot(0,got);
            }
            ItemStack held=buffer.getStackInSlot(0);
            if (!held.isEmpty()) buffer.setStackInSlot(0,ItemHandlerHelper.insertItemStacked(external,held,false));
        }
    }
    @Override protected void write(CompoundTag tag,HolderLookup.Provider registries,boolean clientPacket) {
        super.write(tag,registries,clientPacket);tag.put("Buffer",buffer.serializeNBT(registries));
        if (!filter.isEmpty()) tag.put("Filter",filter.save(registries));
    }
    @Override protected void read(CompoundTag tag,HolderLookup.Provider registries,boolean clientPacket) {
        super.read(tag,registries,clientPacket);buffer.deserializeNBT(registries,tag.getCompound("Buffer"));
        filter=ItemStack.parseOptional(registries,tag.getCompound("Filter"));
    }
}
