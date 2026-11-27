package dev.viridianengineering.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.content.corporea.*;
import dev.viridianengineering.registry.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.gametest.*;
import velrondevs.botania.api.corporea.CorporeaHelper;
import velrondevs.botania.common.entity.CorporeaSparkEntity;
import static dev.viridianengineering.content.corporea.CorporeaNetworkManager.Access.*;

@GameTestHolder(ViridianEngineering.MOD_ID)
@PrefixGameTestTemplate(false)
public class PhaseTwoIntegrationTests {
    private static final BlockPos T=new BlockPos(1,1,1), M=T.below(), P=new BlockPos(3,1,1), V=new BlockPos(4,1,1);
    private static void setup(GameTestHelper h) {
        h.setBlock(T,VEBlocks.CORPOREA_CRAFTING_TERMINAL.getDefaultState().setValue(CorporeaTerminalBlock.FACING,Direction.UP)); // screen up, shaft down; pipes plug into the sides
        h.setBlock(M,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING,Direction.UP));
        h.setBlock(T.east(),VEBlocks.CORPOREA_PIPE.getDefaultState());
        h.setBlock(P,VEBlocks.CORPOREA_MACHINE_PORT.getDefaultState().setValue(CorporeaPortBlock.FACING,Direction.EAST));
        h.setBlock(V,AllBlocks.ITEM_VAULT.getDefaultState());
        h.runAfterDelay(2,()->((CreativeMotorBlockEntity)h.getBlockEntity(M)).generatedSpeed.setValue(32));
    }
    private static ArcaneSparkEntity arcane(GameTestHelper h,BlockPos pos) {
        var spark=VECorporea.SPARK_ENTITY.get().create(h.getLevel()); spark.attach(h.absolutePos(pos)); h.getLevel().addFreshEntity(spark); return spark;
    }
    private static int countGold(net.minecraft.server.level.ServerLevel level,BlockPos pos) {
        var inv=level.getCapability(Capabilities.ItemHandler.BLOCK,pos,Direction.UP); int n=0;
        if (inv!=null) for (int i=0;i<inv.getSlots();i++) if (inv.getStackInSlot(i).is(Items.GOLD_INGOT)) n+=inv.getStackInSlot(i).getCount();
        return n;
    }
    private static CorporeaSparkEntity nativeSpark(GameTestHelper h,BlockPos pos,boolean master) {
        var type=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("botania:corporea_spark"));
        var spark=(CorporeaSparkEntity)type.create(h.getLevel()); var abs=h.absolutePos(pos);
        spark.setPos(abs.getX()+.5,abs.getY()+1.25,abs.getZ()+.5); spark.setMaster(master); h.getLevel().addFreshEntity(spark); return spark;
    }

    @GameTest(template = "empty_12x6x12", timeoutTicks = 120)
    public static void corporeaJunctionDeliversToBothOutputPorts(GameTestHelper h) {
        BlockPos cross = new BlockPos(5, 1, 5);
        BlockPos terminal = cross.west(), motor = terminal.below();
        BlockPos storagePort = cross.east(), storage = storagePort.east();
        BlockPos northPort = cross.north(), southPort = cross.south();
        BlockPos northChest = northPort.north(), southChest = southPort.south();
        h.setBlock(terminal, VEBlocks.CORPOREA_CRAFTING_TERMINAL.getDefaultState()
                .setValue(CorporeaTerminalBlock.FACING, Direction.UP));
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(CreativeMotorBlock.FACING, Direction.UP));
        h.setBlock(storagePort, VEBlocks.CORPOREA_MACHINE_PORT.getDefaultState()
                .setValue(CorporeaPortBlock.FACING, Direction.EAST));
        h.setBlock(storage, Blocks.CHEST);
        h.setBlock(northPort, VEBlocks.CORPOREA_OUTPUT_PORT.getDefaultState()
                .setValue(CorporeaPortBlock.FACING, Direction.NORTH));
        h.setBlock(southPort, VEBlocks.CORPOREA_OUTPUT_PORT.getDefaultState()
                .setValue(CorporeaPortBlock.FACING, Direction.SOUTH));
        h.setBlock(northChest, Blocks.CHEST);
        h.setBlock(southChest, Blocks.CHEST);
        h.setBlock(cross, VEBlocks.CORPOREA_PIPE.getDefaultState());

        h.runAfterDelay(2, () -> {
            ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(32);
            var inventory = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                    h.absolutePos(storage), Direction.WEST);
            inventory.insertItem(0, new ItemStack(Items.GOLD_INGOT, 16), false);
            inventory.insertItem(1, new ItemStack(Items.IRON_INGOT, 16), false);
            ((CorporeaPortBlockEntity) h.getBlockEntity(northPort)).setFilter(new ItemStack(Items.GOLD_INGOT));
            ((CorporeaPortBlockEntity) h.getBlockEntity(southPort)).setFilter(new ItemStack(Items.IRON_INGOT));
        });
        h.succeedWhen(() -> {
            var north = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                    h.absolutePos(northChest), Direction.SOUTH).getStackInSlot(0);
            var south = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                    h.absolutePos(southChest), Direction.NORTH).getStackInSlot(0);
            h.assertTrue(north.is(Items.GOLD_INGOT) && north.getCount() == 16, "North branch did not receive the gold");
            h.assertTrue(south.is(Items.IRON_INGOT) && south.getCount() == 16, "South branch did not receive the iron");
        });
    }

    // Own batch: these sparks reach 48 blocks, so running next to other tests let them link into the
    // neighbouring tests' networks (real behaviour, but it made the bridge test flaky).
    @GameTest(template="empty_12x6x12",timeoutTicks=100,batch="viridian_sparks")
    public static void wirelessDrainAndPowerLoss(GameTestHelper h) {
        setup(h);
        BlockPos remote=new BlockPos(9,1,1), chest=remote.east();
        h.setBlock(remote,VEBlocks.CORPOREA_MACHINE_PORT.getDefaultState().setValue(CorporeaPortBlock.FACING,Direction.EAST));h.setBlock(chest,Blocks.CHEST);
        var a=arcane(h,P);var b=arcane(h,remote);a.receiveMana(10000);b.receiveMana(10000);
        h.runAfterDelay(20,()->{
            var level=h.getLevel();var origin=h.absolutePos(T);
            var inventory=level.getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(chest),Direction.WEST);
            inventory.insertItem(0,new ItemStack(Items.DIAMOND,32),false);
            h.assertTrue(a.range()==48 && a.getCurrentMana()==10000,"Idle Spark range/drain incorrect");
            int count=CorporeaNetworkManager.browse(level,origin,MANUAL_WIRED,new TransactionContext()).stream().mapToInt(ItemStack::getCount).sum();
            h.assertTrue(count==32,"Wireless inventory not visible");
            h.assertTrue(a.getCurrentMana()==10000,"Browsing drained mana without moving items");
            h.assertTrue(CorporeaNetworkManager.withdraw(level,origin,new ItemStack(Items.DIAMOND),8,MANUAL_WIRED,new TransactionContext()).getCount()==8,"Wireless withdrawal failed");
            h.assertTrue(CorporeaNetworkManager.withdraw(level,origin,new ItemStack(Items.DIAMOND),8,MANUAL_WIRED,new TransactionContext()).getCount()==8,"Second wireless withdrawal failed");
            h.assertTrue(a.getCurrentMana()==9998 && b.getCurrentMana()==9998,"Spark must drain exactly 2 per active tick, not per request");
            ((CreativeMotorBlockEntity)h.getBlockEntity(M)).generatedSpeed.setValue(31);
        });
        h.runAfterDelay(40,()->{
            h.assertTrue(CorporeaNetworkManager.withdraw(h.getLevel(),h.absolutePos(T),new ItemStack(Items.DIAMOND),16,MANUAL_WIRED,new TransactionContext()).isEmpty(),"Unpowered terminal crossed wireless hop");
            h.assertTrue(a.getCurrentMana()==9998 && b.getCurrentMana()==9998,"Idle/unpowered Spark drained mana");
            var inventory=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(chest),Direction.WEST);
            h.assertTrue(inventory.getStackInSlot(0).getCount()==16,"Power loss changed remote inventory");
            h.succeed();
        });
    }
    @GameTest(template="empty_12x6x12",timeoutTicks=120)
    public static void nativeBridgeBidirectionalAndLoops(GameTestHelper h) {
        setup(h);
        BlockPos bridge=new BlockPos(1,1,4), bridge2=new BlockPos(3,1,4), chest=new BlockPos(7,1,4), master=new BlockPos(9,1,4);
        h.setBlock(new BlockPos(1,1,2),VEBlocks.CORPOREA_PIPE.getDefaultState());h.setBlock(new BlockPos(1,1,3),VEBlocks.CORPOREA_PIPE.getDefaultState());
        h.setBlock(bridge,VEBlocks.CORPOREA_BRIDGE_PORT.getDefaultState());h.setBlock(bridge.east(),VEBlocks.CORPOREA_PIPE.getDefaultState());h.setBlock(bridge2,VEBlocks.CORPOREA_BRIDGE_PORT.getDefaultState());
        h.setBlock(chest,Blocks.CHEST);h.setBlock(master,Blocks.CHEST);
        nativeSpark(h,master,true);var nativeChest=nativeSpark(h,chest,false);nativeSpark(h,bridge,false);nativeSpark(h,bridge2,false);
        // Native and addon networks both see this Vault: the adapter must not count it twice.
        var vaultSpark=nativeSpark(h,V,false);
        h.runAfterDelay(35,()->{
            var level=h.getLevel();var origin=h.absolutePos(T);
            var vault=level.getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(V),Direction.UP);
            var inv=level.getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(chest),Direction.UP);
            vault.insertItem(0,new ItemStack(Items.IRON_INGOT,20),false);inv.insertItem(0,new ItemStack(Items.GOLD_INGOT,30),false);
            var helper=CorporeaHelper.instance();
            var counted=helper.requestItem(helper.createMatcher(new ItemStack(Items.IRON_INGOT),true),-1,nativeChest,null,false);
            h.assertTrue(counted.matchedCount()==20,"Native bridge double-counted shared Vault or looped");
            var got=CorporeaNetworkManager.withdraw(level,origin,new ItemStack(Items.GOLD_INGOT),12,MANUAL_WIRED,new TransactionContext());
            h.assertTrue(got.getCount()==12 && inv.getStackInSlot(0).getCount()==18,"Terminal did not withdraw from native chest exactly");
            h.assertTrue(CorporeaNetworkManager.deposit(level,origin,got,MANUAL_WIRED,new TransactionContext()).isEmpty(),"Could not deposit native items into Vault");
            TransactionContext tx=new TransactionContext();tx.visitInventory(CorporeaNetworkManager.inventoryKey(level,h.absolutePos(V)));
            var fromVault=CorporeaNetworkManager.withdraw(level,origin,new ItemStack(Items.IRON_INGOT),7,MANUAL_WIRED,new TransactionContext());
            h.assertTrue(CorporeaNetworkManager.deposit(level,origin,fromVault,MANUAL_WIRED,tx).isEmpty(),"Could not route Vault items into native chest");
            h.assertTrue(vault.getStackInSlot(0).getCount()==13,"Vault conservation failed");
            vaultSpark.discard(); // Force a native request to reach the Vault exclusively through the bridge.
        });
        h.runAfterDelay(55,()->{
            var helper=CorporeaHelper.instance();
            var got=helper.requestItem(helper.createMatcher(new ItemStack(Items.IRON_INGOT),true),-1,nativeChest,null,true);
            h.assertTrue(got.extractedCount()==20,"Native request through two bridges duplicated/lost inventory");
            ((CreativeMotorBlockEntity)h.getBlockEntity(M)).generatedSpeed.setValue(0);
        });
        h.runAfterDelay(75,()->{
            var level=h.getLevel();var helper=CorporeaHelper.instance();
            // Order-independent check: earlier deposits may have landed on either side of the bridge
            // (native node order follows random spark UUIDs), so put gold on the addon side directly
            // and require the native request to see exactly the gold held by native inventories.
            var vault=level.getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(V),Direction.UP);
            ItemHandlerHelper.insertItemStacked(vault,new ItemStack(Items.GOLD_INGOT,16),false); // enough for the unpowered wired withdrawal below
            int nativeGold=countGold(level,h.absolutePos(chest))+countGold(level,h.absolutePos(master));
            var got=helper.requestItem(helper.createMatcher(new ItemStack(Items.GOLD_INGOT),true),-1,nativeChest,null,false);
            h.assertTrue(got.matchedCount()==nativeGold,"Unpowered bridge exposed addon inventory: matched "+got.matchedCount()+", native inventories hold "+nativeGold);
            h.assertTrue(CorporeaNetworkManager.withdraw(level,h.absolutePos(T),new ItemStack(Items.GOLD_INGOT),12,MANUAL_WIRED,new TransactionContext()).getCount()==12,"Unpowered wired manual withdrawal failed");h.succeed();
        });
    }
}
