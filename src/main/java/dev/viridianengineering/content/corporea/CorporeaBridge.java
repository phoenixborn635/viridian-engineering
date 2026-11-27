package dev.viridianengineering.content.corporea;

import dev.viridianengineering.compat.SableCompat;
import dev.viridianengineering.registry.VEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import velrondevs.botania.api.BotaniaAPI;
import velrondevs.botania.api.corporea.*;
import java.util.*;

/** Native Corporea request adapter. Shared request identity carries context across sibling bridges. */
public final class CorporeaBridge {
    private static final Map<CorporeaRequest,TransactionContext> REQUESTS=new WeakHashMap<>();
    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(()->BotaniaAPI.instance().registerCorporeaNodeDetector((world,spark)->
                world.getBlockState(spark.getAttachPos()).is(VEBlocks.CORPOREA_BRIDGE_PORT.get()) ? new Adapter(world,spark) : null));
    }
    public record Adapter(Level getWorld, CorporeaSpark getSpark) implements velrondevs.botania.api.corporea.CorporeaNode {
        @Override public BlockPos getPos() { return getSpark.getAttachPos(); }
        @Override public List<ItemStack> countItems(CorporeaRequest request) { return apply(request,false); }
        @Override public List<ItemStack> extractItems(CorporeaRequest request) { return apply(request,true); }
        private List<ItemStack> apply(CorporeaRequest request,boolean extract) {
            if (!(getWorld instanceof ServerLevel level) || !level.hasChunkAt(getPos()) || !CorporeaNetworkManager.isPowered(level,getPos())) return List.of();
            TransactionContext tx=REQUESTS.computeIfAbsent(request,k->new TransactionContext());
            if (!tx.enterBridge(SableCompat.endpointKey(level,getPos(),Direction.DOWN))) return List.of();
            // Native Corporea will visit these nodes itself, regardless of iteration order.
            // Excluding them here avoids double-counting shared Vaults through a bridge.
            for (var node:CorporeaHelper.instance().getNodesOnNetwork(getSpark)) {
                if (node!=null && node.getWorld()==level && level.hasChunkAt(node.getPos()) && !level.getBlockState(node.getPos()).is(VEBlocks.CORPOREA_BRIDGE_PORT.get()))
                    tx.visitInventory(CorporeaNetworkManager.inventoryKey(level,node.getPos()));
            }
            List<ItemStack> result=new ArrayList<>();
            for (var inventory:CorporeaNetworkManager.inventories(level,getPos(),CorporeaNetworkManager.Access.AUTOMATED,tx)) {
                for (int slot=0;slot<inventory.getSlots();slot++) {
                    ItemStack stack=inventory.getStackInSlot(slot);
                    if (stack.isEmpty() || !request.getMatcher().test(stack)) continue;
                    request.trackFound(stack.getCount());
                    int amount=request.getStillNeeded()==-1 ? stack.getCount():Math.min(stack.getCount(),Math.max(0,request.getStillNeeded()));
                    if (amount==0) continue;
                    ItemStack got=inventory.extractItem(slot,amount,!extract);
                    if (got.isEmpty()) continue;
                    request.trackSatisfied(got.getCount());
                    if (extract) { request.trackExtracted(got.getCount()); getSpark.onItemExtracted(got); }
                    result.add(got);
                }
            }
            return result;
        }
    }
}
