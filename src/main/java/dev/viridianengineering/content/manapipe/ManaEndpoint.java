package dev.viridianengineering.content.manapipe;

import dev.viridianengineering.api.mana.IManaPipeNode;
import dev.viridianengineering.registry.ModCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import velrondevs.botania.api.BotaniaCapabilities;
import velrondevs.botania.api.mana.ManaCollector;
import velrondevs.botania.api.mana.ManaPool;
import velrondevs.botania.api.mana.ManaReceiver;

/**
 * Uniform view of "something next to a mana pipe that can hold mana".
 * <p>
 * Every transfer reports the amount that really moved, so callers can keep the
 * pipe network strictly conservative ({@code extracted == inserted}). Botania's
 * {@code receiveMana(int)} returns nothing, so its effect is measured from
 * {@code getCurrentMana()} before and after.
 */
public interface ManaEndpoint {

    long stored();

    /** Total capacity, or {@code -1} for a pure sink whose capacity is unknown. */
    long capacity();

    /** @return mana actually accepted */
    long insert(long amount);

    /** @return mana actually removed */
    long extract(long amount);

    default boolean canExtract() {
        return capacity() >= 0;
    }

    default IManaPipeNode.Role role() {
        return IManaPipeNode.Role.STORE;
    }

    /**
     * @param side the face of the target block that touches the pipe
     */
    @Nullable
    static ManaEndpoint find(Level level, BlockPos pos, Direction side) {
        IManaPipeNode node = level.getCapability(ModCapabilities.MANA_PIPE_NODE, pos, side);
        if (node != null && node.canConnectMana(side))
            return new NodeEndpoint(node);
        ManaReceiver receiver = level.getCapability(BotaniaCapabilities.MANA_RECEIVER, pos, side);
        if (receiver != null)
            return new BotaniaEndpoint(receiver);
        return null;
    }

    static boolean isConnectable(Level level, BlockPos pos, Direction side) {
        return level.getCapability(ModCapabilities.MANA_PIPE_NODE, pos, side) != null
                || level.getCapability(BotaniaCapabilities.MANA_RECEIVER, pos, side) != null;
    }

    record NodeEndpoint(IManaPipeNode node) implements ManaEndpoint {
        @Override public long stored() { return node.getStoredMana(); }
        @Override public long capacity() { return node.getManaCapacity(); }
        @Override public long insert(long amount) { return amount <= 0 ? 0 : node.insertMana(amount, false); }
        @Override public long extract(long amount) { return amount <= 0 ? 0 : node.extractMana(amount, false); }
        @Override public IManaPipeNode.Role role() { return node.role(); }
    }

    record BotaniaEndpoint(ManaReceiver receiver) implements ManaEndpoint {

        @Override
        public long stored() {
            return Math.max(0, receiver.getCurrentMana());
        }

        @Override
        public long capacity() {
            if (receiver instanceof ManaPool pool) return pool.getMaxMana();
            if (receiver instanceof ManaCollector collector) return collector.getMaxMana();
            return -1;
        }

        @Override
        public long insert(long amount) {
            if (amount <= 0 || receiver.isFull()) return 0;
            int request = (int) Math.min(amount, Integer.MAX_VALUE);
            int before = receiver.getCurrentMana();
            receiver.receiveMana(request);
            long moved = (long) receiver.getCurrentMana() - before;
            return Math.max(0, Math.min(moved, request));
        }

        /**
         * Botania's creative pool always reads full and ignores withdrawals, so measuring it before and
         * after would say nothing moved. Treat it as the endless source it is.
         */
        @Override
        public IManaPipeNode.Role role() {
            return isCreative() ? IManaPipeNode.Role.SOURCE : IManaPipeNode.Role.STORE;
        }

        boolean isCreative() {
            net.minecraft.world.level.Level level = receiver.getManaReceiverLevel();
            if (level == null) return false;
            net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(receiver.getManaReceiverPos()).getBlock());
            return id.getNamespace().equals("botania") && id.getPath().equals("creative_pool");
        }

        @Override
        public long extract(long amount) {
            if (amount <= 0 || !canExtract()) return 0;
            if (isCreative()) return amount;
            int request = (int) Math.min(amount, receiver.getCurrentMana());
            if (request <= 0) return 0;
            int before = receiver.getCurrentMana();
            receiver.receiveMana(-request);
            long moved = before - (long) receiver.getCurrentMana();
            return Math.max(0, Math.min(moved, request));
        }
    }
}
