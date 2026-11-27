package dev.viridianengineering.content.airship;

import com.mojang.brigadier.CommandDispatcher;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.viridianengineering.registry.VEBlocks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /viridian demo bifrost} (operators): builds a small airship a few blocks in front of the player
 * (a stone deck with a Levitation Pylon, a Bifrost Road Projector under the deck and an Eye of the Flügel
 * Navigation Core holding an Eye bound 40 blocks ahead), fills them with mana and assembles it. The
 * core flies it forward and the projector lays a fading rainbow road under it. For showing the blocks
 * off; in survival you build the same thing and assemble it with Aeronautics' physics assembler.
 */
public final class AirshipDemo {

    private AirshipDemo() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("viridian").requires(s -> s.hasPermission(2))
                .then(Commands.literal("demo").then(Commands.literal("bifrost").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    String problem = whyNot(player);
                    if (problem != null) {
                        ctx.getSource().sendFailure(Component.literal(problem));
                        return 0;
                    }
                    int blocks = bifrost(player);
                    if (blocks <= 0) {
                        ctx.getSource().sendFailure(Component.literal("The demo airship could not be assembled here."));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("Demo airship assembled (" + blocks
                            + " blocks): it flies 40 blocks ahead and lays a Bifrost road under itself."), false);
                    return 1;
                }))
                        .then(Commands.literal("cannons").executes(ctx -> report(ctx.getSource(),
                                VfxDemos.cannons(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("thruster").executes(ctx -> report(ctx.getSource(),
                                VfxDemos.thrusters(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("reactor").executes(ctx -> report(ctx.getSource(),
                                VfxDemos.reactor(ctx.getSource().getPlayerOrException()))))
                        .then(Commands.literal("explosion")
                                .executes(ctx -> report(ctx.getSource(), VfxDemos.explosion(ctx.getSource().getPlayerOrException(), 24f)))
                                .then(Commands.argument("radius", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(4f, 200f))
                                        .executes(ctx -> report(ctx.getSource(), VfxDemos.explosion(ctx.getSource().getPlayerOrException(),
                                                com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "radius"))))))));
    }

    private static int report(CommandSourceStack source, String result) {
        if (result.startsWith("!")) { source.sendFailure(Component.literal(result.substring(1))); return 0; }
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static Rotation rotation(Direction forward) {
        return switch (forward) {
            case SOUTH -> Rotation.CLOCKWISE_90;
            case WEST -> Rotation.CLOCKWISE_180;
            case NORTH -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** Authored facing east (+x); the origin is 4 blocks ahead of the player and 6 up. */
    private static BlockPos origin(ServerPlayer player) {
        return player.blockPosition().relative(player.getDirection(), 4).above(6);
    }

    /**
     * Why the demo can't be built where the player stands, or null if it can. The ship (7 x 3 x 5, plus
     * its flight path 43 blocks ahead) has to fit inside the build height, in loaded chunks, in air:
     * running it while falling through the void used to build at y = -241 and crash Sable's assembly.
     */
    @org.jetbrains.annotations.Nullable
    static String whyNot(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (player.getY() < level.getMinBuildHeight() || player.getY() > level.getMaxBuildHeight())
            return "Stand inside the world (y " + level.getMinBuildHeight() + " to " + level.getMaxBuildHeight() + ") to build the demo airship.";
        Rotation rot = rotation(player.getDirection());
        BlockPos origin = origin(player);
        for (int x = 0; x <= 6; x++)
            for (int y = 0; y <= 2; y++)
                for (int z = -2; z <= 2; z++) {
                    BlockPos p = at(origin, rot, x, y, z);
                    if (level.isOutsideBuildHeight(p) || level.isOutsideBuildHeight(p.above(8)))
                        return "Not enough height here for the demo airship: move down a little.";
                    if (!level.isLoaded(p)) return "The chunks in front of you aren't loaded.";
                    if (!level.getBlockState(p).canBeReplaced())
                        return "Something is in the way: the demo needs a clear 7 x 3 x 5 space 4 blocks ahead of you and 6 up.";
                }
        return null;
    }

    /** Builds, fills and assembles the demo ship; returns its block count (0 if assembly failed). */
    public static int bifrost(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Direction forward = player.getDirection();
        Rotation rot = rotation(forward);
        BlockPos origin = origin(player);
        List<BlockPos> ship = new ArrayList<>();
        BlockState stone = Blocks.POLISHED_ANDESITE.defaultBlockState();
        for (int x = 0; x <= 6; x++)
            for (int z = -2; z <= 2; z++) put(level, origin, rot, ship, x, 0, z, stone);
        BlockState pylon = VEBlocks.MANA_LEVITATION_PYLON.getDefaultState();
        put(level, origin, rot, ship, 3, 1, 0, pylon);
        put(level, origin, rot, ship, 3, 2, 0, pylon.setValue(AirshipBlocks.LevitationPylon.HALF, DoubleBlockHalf.UPPER));
        put(level, origin, rot, ship, 3, 0, 2, VEBlocks.BIFROST_PROJECTOR.getDefaultState().setValue(DirectionalBlock.FACING, Direction.DOWN));
        put(level, origin, rot, ship, 5, 1, 0, VEBlocks.FLUGEL_NAVIGATION_CORE.getDefaultState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));

        BlockPos pylonPos = at(origin, rot, 3, 1, 0), projectorPos = at(origin, rot, 3, 0, 2), corePos = at(origin, rot, 5, 1, 0);
        if (level.getBlockEntity(pylonPos) instanceof LevitationPylonBlockEntity p) p.mana.set(p.mana.getManaCapacity());
        if (level.getBlockEntity(projectorPos) instanceof BifrostProjectorBlockEntity b) {
            b.mana.set(b.mana.getManaCapacity());
            b.setOwner(player.getUUID());
        }
        if (level.getBlockEntity(corePos) instanceof FlugelNavigationCoreBlockEntity c) {
            c.mana.set(c.mana.getManaCapacity());
            BlockPos target = origin.relative(forward, 43).below(dev.viridianengineering.config.VEConfig.FLUGEL_ARRIVAL_HEIGHT.get());
            ItemStack eye = new ItemStack(velrondevs.botania.registry.BotaniaItems.flugelEye);
            velrondevs.botania.common.helper.ItemNBTHelper.set(eye, "target_" + level.dimension().location(),
                    BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, target).getOrThrow());
            c.setEye(eye);
        }
        ServerSubLevel s;
        try {
            s = SubLevelAssemblyHelper.assembleBlocks(level, ship.get(0), ship, BoundingBox3i.from(ship));
        } catch (RuntimeException e) {
            dev.viridianengineering.ViridianEngineering.LOGGER.warn("Demo airship assembly failed at {}", origin, e);
            return 0;
        }
        return s == null ? 0 : ship.size();
    }

    private static BlockPos at(BlockPos origin, Rotation rot, int x, int y, int z) {
        return origin.offset(new BlockPos(x, y, z).rotate(rot));
    }

    private static void put(ServerLevel level, BlockPos origin, Rotation rot, List<BlockPos> ship, int x, int y, int z, BlockState state) {
        BlockPos p = at(origin, rot, x, y, z);
        level.setBlock(p, state.rotate(rot), 3);
        if (!ship.contains(p)) ship.add(p);
    }
}
