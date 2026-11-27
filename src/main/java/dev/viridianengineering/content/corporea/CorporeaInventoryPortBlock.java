package dev.viridianengineering.content.corporea;

import com.simibubi.create.foundation.block.IBE;
import dev.viridianengineering.content.autocraft.CraftingScheduler;
import dev.viridianengineering.registry.VEBlocks;
import dev.viridianengineering.registry.VEBlockEntities;
import dev.viridianengineering.registry.VECorporea;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class CorporeaInventoryPortBlock extends CorporeaPortBlock implements IBE<CorporeaPortBlockEntity> {
    public CorporeaInventoryPortBlock(Properties properties) { super(properties); }
    @Override public Class<CorporeaPortBlockEntity> getBlockEntityClass() { return CorporeaPortBlockEntity.class; }
    @Override public BlockEntityType<? extends CorporeaPortBlockEntity> getBlockEntityType() { return VEBlockEntities.CORPOREA_PORT.get(); }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if (stack.is(VECorporea.ARCANE_SPARK.get()) || stack.is(com.simibubi.create.AllItems.WRENCH.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CorporeaPortBlockEntity port) {
            port.setFilter(player.isShiftKeyDown() ? ItemStack.EMPTY:stack);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.viridian_engineering.port_filter",port.filter().isEmpty() ? "*":port.filter().getHoverName().getString()),true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState replacement,boolean moving) {
        if (!moving && level instanceof ServerLevel server && state.is(VEBlocks.CORPOREA_MACHINE_PORT.get())
                && (!replacement.is(state.getBlock()) || replacement.getValue(FACING) != state.getValue(FACING))) {
            CraftingScheduler.stationPortRemoved(server, pos, state.getValue(FACING));
        }
        if (!state.is(replacement.getBlock()) && !moving && !level.isClientSide && level.getBlockEntity(pos) instanceof CorporeaPortBlockEntity port) {
            Containers.dropItemStack(level,pos.getX(),pos.getY(),pos.getZ(),port.buffer.getStackInSlot(0));
            port.buffer.setStackInSlot(0,ItemStack.EMPTY);
        }
        super.onRemove(state,level,pos,replacement,moving);
    }
}
