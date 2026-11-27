package dev.viridianengineering.content.autocraft;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.deployer.DeployerBlock;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlock;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.kinetics.saw.SawBlock;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import dev.viridianengineering.api.endpoint.MachineEndpointKey;
import dev.viridianengineering.compat.SableCompat;
import dev.viridianengineering.content.corporea.CorporeaNetworkManager;
import dev.viridianengineering.content.corporea.CorporeaPortBlock;
import dev.viridianengineering.content.corporea.CorporeaPortBlockEntity;
import dev.viridianengineering.registry.VEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spec 3.7 endpoint discovery: scans the machine ports a terminal reaches (wired, or through
 * powered Arcane Sparks) and inspects what each one faces.
 * <ul>
 *   <li><b>Depot machine</b>: the port faces a Depot with a Mechanical Press, or a Deployer pointing
 *       down, two blocks above it.</li>
 *   <li><b>Assembly line</b>: machine ports face the first and the last segment of one belt (first =
 *       where items enter, from the belt's current direction). The processors two blocks above the
 *       segments, in travel order, are the line's steps; a sequenced recipe runs on a line only when
 *       its steps match exactly.</li>
 * </ul>
 * Results are cached per terminal for {@value #CACHE_TICKS} ticks or until the network topology
 * changes. Endpoints are identified by their (input) machine port's {@link MachineEndpointKey}.
 */
public final class MachineDiscovery {

    public static final int CACHE_TICKS = 40;

    public sealed interface Endpoint permits DepotMachine, AssemblyLine, Station {
        MachineEndpointKey key();
        int priority();
    }

    public record DepotMachine(MachineEndpointKey key, BlockPos depot, BlockPos machine, CraftPattern.Op op, int priority) implements Endpoint {}
    public record Station(MachineEndpointKey key, BlockPos target, CraftPattern.Op op, int priority) implements Endpoint {}

    public record AssemblyLine(MachineEndpointKey key, BlockPos input, BlockPos output, int length,
                               List<CraftPattern.Op> ops, List<BlockPos> machines, int priority) implements Endpoint {}

    private record Cached(long tick, int version, List<Endpoint> endpoints) {}

    private static final Map<MachineEndpointKey, Cached> CACHE = new HashMap<>();

    private MachineDiscovery() {}

    public static List<Endpoint> endpoints(ServerLevel level, BlockPos terminal) {
        MachineEndpointKey key = SableCompat.endpointKey(level, terminal, Direction.DOWN);
        Cached cached = CACHE.get(key);
        long now = level.getGameTime();
        if (cached != null && cached.version == CorporeaNetworkManager.version() && now - cached.tick < CACHE_TICKS && now >= cached.tick)
            return cached.endpoints;
        List<Endpoint> found = discover(level, terminal);
        CACHE.put(key, new Cached(now, CorporeaNetworkManager.version(), found));
        return found;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    static List<Endpoint> discover(ServerLevel level, BlockPos terminal) {
        List<Endpoint> out = new ArrayList<>();
        Map<BlockPos, Map<Integer, BlockPos>> beltPorts = new LinkedHashMap<>(); // controller -> segment index -> port
        for (BlockPos pos : CorporeaNetworkManager.automationNodes(level, terminal)) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(VEBlocks.CORPOREA_MACHINE_PORT.get())) continue;
            BlockPos target = pos.relative(state.getValue(CorporeaPortBlock.FACING));
            if (!level.isLoaded(target)) continue;
            var be = level.getBlockEntity(target);
            CraftPattern.Op station = stationAt(level, target);
            if (station != null) {
                out.add(new Station(key(level, pos), target, station, priority(level, pos)));
            } else if (be instanceof DepotBlockEntity) {
                CraftPattern.Op op = machineAbove(level, target);
                if (op != null) out.add(new DepotMachine(key(level, pos), target, target.above(2), op, priority(level, pos)));
            } else if (be instanceof BeltBlockEntity belt && belt.getController() != null) {
                beltPorts.computeIfAbsent(belt.getController(), k -> new HashMap<>()).put(belt.index, pos);
            }
        }
        beltPorts.forEach((controllerPos, ports) -> {
            BeltBlockEntity controller = BeltHelper.getControllerBE(level, controllerPos);
            if (controller == null || controller.beltLength < 2) return;
            float speed = controller.getDirectionAwareBeltMovementSpeed();
            if (speed == 0) return;
            int length = controller.beltLength;
            int first = speed > 0 ? 0 : length - 1, last = speed > 0 ? length - 1 : 0, dir = speed > 0 ? 1 : -1;
            BlockPos in = ports.get(first), outPort = ports.get(last);
            if (in == null || outPort == null) return;
            List<CraftPattern.Op> ops = new ArrayList<>();
            List<BlockPos> machines = new ArrayList<>();
            for (int i = first; i != last + dir; i += dir) {
                BlockPos seg = BeltHelper.getPositionForOffset(controller, i);
                CraftPattern.Op op = machineAbove(level, seg);
                if (op == null) continue;
                ops.add(op);
                machines.add(seg.above(2));
            }
            if (ops.isEmpty()) return;
            out.add(new AssemblyLine(key(level, in), BeltHelper.getPositionForOffset(controller, first),
                    BeltHelper.getPositionForOffset(controller, last), length, ops, machines, priority(level, in)));
        });
        out.sort(Comparator.comparingInt(Endpoint::priority).reversed());
        return out;
    }

    /** True for blocks a machine port treats as an autocrafting endpoint rather than storage. */
    public static boolean isMachineTarget(ServerLevel level, BlockPos target) {
        var be = level.getBlockEntity(target);
        return be instanceof BeltBlockEntity || be instanceof MechanicalCrafterBlockEntity
                || (be instanceof DepotBlockEntity && machineAbove(level, target) != null) || stationAt(level, target) != null;
    }

    static CraftPattern.Op stationAt(ServerLevel level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        if (be instanceof BasinBlockEntity && be.getBlockState().getValue(BasinBlock.FACING) == Direction.DOWN
                && level.getBlockEntity(pos.above(2)) instanceof MechanicalMixerBlockEntity) return CraftPattern.Op.MIX;
        if (be instanceof SawBlockEntity && be.getBlockState().getValue(SawBlock.FACING) == Direction.UP) return CraftPattern.Op.CUT;
        if (be instanceof MechanicalCrafterBlockEntity crafter && RecipeGridHandler.getTargetingCrafter(crafter) == null)
            return CraftPattern.Op.MECHANICAL_CRAFT;
        return null;
    }

    /** A press, or a deployer facing down, two blocks above {@code pos}. */
    static CraftPattern.Op machineAbove(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos.above(2));
        if (state.getBlock() instanceof MechanicalPressBlock) return CraftPattern.Op.PRESS;
        if (level.getBlockEntity(pos.above(2)) instanceof SpoutBlockEntity) return CraftPattern.Op.FILL;
        if (state.getBlock() instanceof DeployerBlock && state.getValue(DeployerBlock.FACING) == Direction.DOWN) return CraftPattern.Op.DEPLOY;
        return null;
    }

    private static MachineEndpointKey key(ServerLevel level, BlockPos port) {
        return SableCompat.endpointKey(level, port, Direction.DOWN);
    }

    private static int priority(ServerLevel level, BlockPos port) {
        return level.getBlockEntity(port) instanceof CorporeaPortBlockEntity be ? be.priority() : 0;
    }
}
