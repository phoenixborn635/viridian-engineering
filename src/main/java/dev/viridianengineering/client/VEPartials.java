package dev.viridianengineering.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.viridianengineering.ViridianEngineering;

/** Extra models rendered by block entity renderers (registered before model baking). */
public final class VEPartials {

    // declared first: the fields below fill it during static initialisation
    /** Each formed piece's translucent crystal half ({@code <piece>_crystal}, see tools/gen_phase4_art.py). */
    private static final java.util.Map<PartialModel, PartialModel> CRYSTALS = new java.util.HashMap<>();


    public static final PartialModel ALFHEIM_PRESS_MODULE = of("block/alfheim_press_module");
    public static final PartialModel ALFHEIM_PRESS_CRYSTAL = of("block/alfheim_press_module_crystal");

    public static final PartialModel MANA_PUMP_COG = of("block/mana_pump/cog");
    public static final PartialModel MANASTEEL_PUMP_COG = of("block/manasteel_pump/cog");

    // formed multiblocks: one piece model per role, placed by the controller renderers
    public static final PartialModel REACTOR_WALL_LOW = of("block/formed/reactor_wall_low");
    public static final PartialModel REACTOR_WALL_HIGH = of("block/formed/reactor_wall_high");
    public static final PartialModel REACTOR_CORNER_LOW = of("block/formed/reactor_corner_low");
    public static final PartialModel REACTOR_CORNER_HIGH = of("block/formed/reactor_corner_high");
    public static final PartialModel REACTOR_INNER = of("block/formed/reactor_inner");
    public static final PartialModel REACTOR_INNER_CORNER = of("block/formed/reactor_inner_corner");
    public static final PartialModel REACTOR_PEDESTAL = of("block/formed/reactor_pedestal");
    public static final PartialModel REACTOR_CORE = of("block/formed/reactor_core");
    public static final PartialModel REACTOR_RING = of("block/formed/reactor_ring");
    public static final PartialModel BATTERY_SIDE = of("block/formed/battery_side");
    public static final PartialModel BATTERY_CORNER = of("block/formed/battery_corner");
    public static final PartialModel BATTERY_CENTER = of("block/formed/battery_center");
    public static final PartialModel BATTERY_CAP = of("block/formed/battery_cap");

    // Gaia cannon: the core held in the claws, and the shards orbiting it
    public static final PartialModel CANNON_CORE = of("block/formed/cannon_core");
    public static final PartialModel CANNON_SHARDS = of("block/formed/cannon_shards");

    // Sylvan armor 3D attachments (drawn on the player model by client.SylvanArmorLayer)
    public static final PartialModel GEAR_BRACER_TANK = of("block/gear/bracer_tank");
    public static final PartialModel GEAR_MANA_PACK = of("block/gear/mana_pack");
    public static final PartialModel GEAR_STRIDER = of("block/gear/strider");
    public static final PartialModel GEAR_BOOT_THRUSTER = of("block/gear/boot_thruster");

    // Terra Drive cartridges standing in an Archive Pylon's bays (tools/gen_drive_cartridges.py)
    public static final PartialModel DRIVE_16K = of("block/drive/cartridge_16k");
    public static final PartialModel DRIVE_64K = of("block/drive/cartridge_64k");
    public static final PartialModel DRIVE_256K = of("block/drive/cartridge_256k");
    public static final PartialModel DRIVE_1M = of("block/drive/cartridge_1m");

    private VEPartials() {}

    private static PartialModel of(String path) {
        PartialModel m = PartialModel.of(ViridianEngineering.asResource(path));
        if (path.startsWith("block/formed/") || path.startsWith("block/gear/") || path.startsWith("block/drive/"))
            CRYSTALS.put(m, PartialModel.of(ViridianEngineering.asResource(path + "_crystal")));
        return m;
    }

    /** The translucent crystal part of a formed piece (never null for formed pieces). */
    public static PartialModel crystal(PartialModel piece) {
        return CRYSTALS.get(piece);
    }

    /** Called from the mod constructor on the client so the models exist before baking. */
    public static void init() {}
}
