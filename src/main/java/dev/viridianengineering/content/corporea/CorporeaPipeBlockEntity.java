package dev.viridianengineering.content.corporea;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Stores the nearest wired terminal distance and per-side light directions. The server rebuilds
 * these together so both sides of a pipe joint use the same flow and phase.
 */
public class CorporeaPipeBlockEntity extends SmartBlockEntity {

    public record LightRoute(int distance, int towardTerminal, int fromFarther) {
        public static final LightRoute DISCONNECTED = new LightRoute(-1, 0, 0);

        /** +1 leaves the pipe, -1 enters it, 0 has no moving light. */
        public int flowThrough(Direction side) {
            int bit = 1 << side.get3DDataValue();
            return (towardTerminal & bit) != 0 ? 1 : (fromFarther & bit) != 0 ? -1 : 0;
        }
    }

    private LightRoute lightRoute = LightRoute.DISCONNECTED;

    public CorporeaPipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(40);
    }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public LightRoute lightRoute() { return lightRoute; }

    void setLightRoute(LightRoute route) {
        if (route.equals(lightRoute)) return;
        lightRoute = route;
        sendData();
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        // Makes sure the component is (re)built after a topology change even if nothing else asks.
        if (level instanceof ServerLevel server) CorporeaNetworkManager.wiredNodes(server, worldPosition);
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            tag.putInt("Distance", lightRoute.distance());
            tag.putInt("TowardTerminal", lightRoute.towardTerminal());
            tag.putInt("FromFarther", lightRoute.fromFarther());
        }
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket && tag.contains("Distance"))
            lightRoute = new LightRoute(tag.getInt("Distance"), tag.getInt("TowardTerminal"), tag.getInt("FromFarther"));
    }
}
