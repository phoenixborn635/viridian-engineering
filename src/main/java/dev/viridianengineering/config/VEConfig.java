package dev.viridianengineering.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import dev.viridianengineering.content.infusion.ManaInfusionRecipe;

/**
 * Spec section 5: viridian_engineering-common.toml.
 * Defaults are the LOCKED balance values from spec 1.2; change them only through the config file.
 */
public final class VEConfig {

    public static final ModConfigSpec SPEC;

    // [mana_industry]
    public static final ModConfigSpec.DoubleValue MANASTEEL_PIPE_THROUGHPUT_MULTIPLIER;
    public static final ModConfigSpec.IntValue TERRASTEEL_PIPE_PASSIVE_RATE;
    public static final ModConfigSpec.DoubleValue TERRASTEEL_PIPE_PUMP_RPM_MULTIPLIER;
    public static final ModConfigSpec.IntValue REACTOR_LAVA_MB_PER_BUCKET_MANA;
    public static final ModConfigSpec.IntValue REACTOR_MIN_RPM;
    public static final ModConfigSpec.DoubleValue REACTOR_STRESS_PER_RPM;
    public static final ModConfigSpec.IntValue REACTOR_MANA_BUFFER;
    public static final ModConfigSpec.IntValue REACTOR_MAX_RADIUS;
    public static final ModConfigSpec.DoubleValue PUMP_SPEED_MULTIPLIER;
    public static final ModConfigSpec.IntValue REACTOR_LAVA_TANK_MB;
    public static final ModConfigSpec.DoubleValue REACTOR_LAVA_MB_PER_TICK_AT_256;
    public static final ModConfigSpec.IntValue ALFHEIM_MIXER_MANA_CAPACITY;

    // [elementium_logistics] (not in spec section 5; numbers chosen in Phase 4, see comments)
    public static final ModConfigSpec.IntValue ELEMENTIUM_MANA_PER_ITEM;
    public static final ModConfigSpec.IntValue ELEMENTIUM_MANA_BUFFER;
    public static final ModConfigSpec.IntValue ELEMENTIUM_LINK_RANGE;
    public static final ModConfigSpec.IntValue ELEMENTIUM_FUNNEL_INTERVAL;
    public static final ModConfigSpec.IntValue ELEMENTIUM_FUNNEL_BATCH;
    public static final ModConfigSpec.IntValue ELEMENTIUM_INTERFACE_UPKEEP;

    // [mana_battery]
    public static final ModConfigSpec.LongValue MANA_PER_LAYER;
    public static final ModConfigSpec.IntValue MIN_LAYERS;
    public static final ModConfigSpec.IntValue MAX_LAYERS;
    public static final ModConfigSpec.IntValue PASSIVE_IO_RATE;
    public static final ModConfigSpec.IntValue PUMPED_IO_RATE;
    public static final ModConfigSpec.BooleanValue BATTERY_EXPLOSION_TERRAIN_DAMAGE;
    public static final ModConfigSpec.IntValue BATTERY_EXPLOSION_MIN_RADIUS;
    public static final ModConfigSpec.IntValue BATTERY_EXPLOSION_MAX_RADIUS;
    public static final ModConfigSpec.IntValue BATTERY_FAILURE_TICKS;
    public static final ModConfigSpec.IntValue BATTERY_SHELL_BLOCKS_PER_TICK;
    public static final ModConfigSpec.DoubleValue BATTERY_EXPLOSION_MAX_ENTITY_DAMAGE;

    // [corporea_engineering]
    public static final ModConfigSpec.IntValue CONTROLLER_MIN_RPM;
    public static final ModConfigSpec.DoubleValue CONTROLLER_STRESS_PER_RPM;
    public static final ModConfigSpec.DoubleValue ARCANE_SPARK_BASE_RANGE;
    public static final ModConfigSpec.DoubleValue ARCANE_SPARK_MAX_RANGE;
    public static final ModConfigSpec.IntValue ARCANE_SPARK_MANA_PER_BLOCK;
    public static final ModConfigSpec.IntValue ARCANE_SPARK_ACTIVE_DRAIN_PER_TICK;
    public static final ModConfigSpec.IntValue MAX_COMPONENT_PAYLOAD_BYTES;

    public static final ModConfigSpec.IntValue CORPOREA_MAX_WIRED_NODES;
    public static final ModConfigSpec.IntValue ARCANE_SPARK_CAPACITY;
    public static final ModConfigSpec.IntValue CORPOREA_PORT_INTERVAL;
    public static final ModConfigSpec.IntValue CORPOREA_PORT_BATCH;

    // [gaia_cannon]
    public static final ModConfigSpec.IntValue PROJECTILE_MANA_COST;
    public static final ModConfigSpec.IntValue PROJECTILE_CHARGE_TICKS;
    public static final ModConfigSpec.DoubleValue PROJECTILE_MAX_DAMAGE;
    public static final ModConfigSpec.IntValue BEAM_MANA_PER_TICK;
    public static final ModConfigSpec.DoubleValue BEAM_DAMAGE_PER_HALF_SECOND;
    public static final ModConfigSpec.IntValue BEAM_MAX_DURATION_TICKS;
    public static final ModConfigSpec.IntValue BEAM_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue CANNON_MANA_BUFFER;
    public static final ModConfigSpec.IntValue BEAM_RANGE;
    public static final ModConfigSpec.BooleanValue BOLT_BREAKS_TERRAIN;
    public static final ModConfigSpec.DoubleValue BOLT_BLAST_RADIUS;

    // [aeronautics]
    public static final ModConfigSpec.IntValue LEVITATION_HOVER_MANA_PER_TICK;
    public static final ModConfigSpec.IntValue LEVITATION_ACTIVE_MANA_PER_TICK;
    public static final ModConfigSpec.IntValue GAIA_THRUSTER_MAX_MANA_PER_TICK;
    public static final ModConfigSpec.IntValue BIFROST_MANA_PER_BLOCK;
    public static final ModConfigSpec.IntValue BIFROST_TRAIL_LIFETIME_TICKS;
    public static final ModConfigSpec.IntValue BIFROST_MAX_ACTIVE_BLOCKS_PER_SHIP;
    public static final ModConfigSpec.IntValue FLUGEL_AUTOPILOT_MANA_PER_TICK;
    // Phase 5 values the spec doesn't give (marked "not in the spec" in the file)
    public static final ModConfigSpec.IntValue AIRSHIP_BLOCK_MANA_BUFFER;
    public static final ModConfigSpec.DoubleValue LEVITATION_LIFT_CAPACITY;
    public static final ModConfigSpec.DoubleValue LEVITATION_CLIMB_SPEED;
    public static final ModConfigSpec.DoubleValue GAIA_THRUSTER_MAX_THRUST;
    public static final ModConfigSpec.DoubleValue FLUGEL_CRUISE_SPEED;
    public static final ModConfigSpec.IntValue FLUGEL_ARRIVAL_HEIGHT;
    public static final ModConfigSpec.IntValue FLUGEL_ARRIVAL_RADIUS;

    // Viridian spec Phase 5a: gaia gear
    public static final ModConfigSpec.DoubleValue LANCE_BASE_DAMAGE;
    public static final ModConfigSpec.DoubleValue LANCE_MAX_VELOCITY_BONUS;
    public static final ModConfigSpec.IntValue LANCE_STRIKE_MANA_COST;
    public static final ModConfigSpec.IntValue LANCE_LUNGE_MANA_COST;
    public static final ModConfigSpec.DoubleValue LANCE_GALE_DAMAGE;
    public static final ModConfigSpec.IntValue LANCE_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue LANCE_AIR_PER_STRIKE;
    public static final ModConfigSpec.IntValue LANCE_LUNGE_AIR_COST;
    public static final ModConfigSpec.IntValue LANCE_MANA_CAPACITY;
    public static final ModConfigSpec.DoubleValue LANCE_REACH_BONUS;
    public static final ModConfigSpec.IntValue LANCE_LUNGE_CHARGE_TICKS;
    public static final ModConfigSpec.DoubleValue LANCE_GALE_RANGE;
    public static final ModConfigSpec.DoubleValue LANCE_FULL_BONUS_SPEED;
    public static final ModConfigSpec.DoubleValue LANCE_LUNGE_SPEED;
    public static final ModConfigSpec.DoubleValue LANCE_LUNGE_LIFT;
    public static final ModConfigSpec.DoubleValue LANCE_GALE_KNOCKBACK;
    public static final ModConfigSpec.IntValue SYLVAN_MANA_REGEN_PER_TICK;
    public static final ModConfigSpec.DoubleValue SYLVAN_MANA_DISCOUNT;
    public static final ModConfigSpec.IntValue BRACER_TANKS_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue BRACER_TANKS_MANA_PER_AIR_TRICKLE;
    public static final ModConfigSpec.IntValue BRACER_TANKS_TRICKLE_AIR_PER_TICK;
    public static final ModConfigSpec.IntValue MANA_PACK_CAPACITY;
    public static final ModConfigSpec.IntValue MANA_PACK_REGEN_PER_TICK;
    public static final ModConfigSpec.DoubleValue STRIDER_SPRINT_BOOST_MULTIPLIER;
    public static final ModConfigSpec.IntValue STRIDER_AIR_PER_TICK;
    public static final ModConfigSpec.IntValue STRIDER_MANA_FALLBACK_PER_TICK;
    public static final ModConfigSpec.DoubleValue STRIDER_STEP_HEIGHT;
    public static final ModConfigSpec.IntValue BOOT_THRUSTER_AIR_PER_BURST;
    public static final ModConfigSpec.DoubleValue BOOT_THRUSTER_BURST_SPEED;
    public static final ModConfigSpec.IntValue BOOT_THRUSTER_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue BOOT_THRUSTER_MAX_AIR_JUMPS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("mana_industry");
        MANASTEEL_PIPE_THROUGHPUT_MULTIPLIER = b.comment("Manasteel fluid pipe throughput vs. Create copper pipes at the same pump RPM.")
                .defineInRange("manasteelPipeThroughputMultiplier", 2.0, 0.1, 64.0);
        TERRASTEEL_PIPE_PASSIVE_RATE = b.comment("Mana/tick a Terrasteel pipe equalizes with no pump.")
                .defineInRange("terrasteelPipePassiveRate", 40, 0, 1_000_000);
        TERRASTEEL_PIPE_PUMP_RPM_MULTIPLIER = b.comment("Pumped rate = passive + multiplier * |RPM|.")
                .defineInRange("terrasteelPipePumpRpmMultiplier", 1.25, 0.0, 1000.0);
        ALFHEIM_MIXER_MANA_CAPACITY = b.comment("Mana capacity of an Alfheim-upgraded Mixer (not in the spec).")
                .defineInRange("alfheimMixerManaCapacity", 500_000, ManaInfusionRecipe.TERRASTEEL_MANA, Integer.MAX_VALUE);
        REACTOR_LAVA_MB_PER_BUCKET_MANA = b.comment("Mana produced per 1,000 mB of lava burned.")
                .defineInRange("reactorLavaMbPerBucketMana", 25_000, 1, 10_000_000);
        REACTOR_MIN_RPM = b.defineInRange("reactorMinRpm", 64, 1, 256);
        REACTOR_STRESS_PER_RPM = b.defineInRange("reactorStressPerRpm", 16.0, 0.0, 1024.0);
        PUMP_SPEED_MULTIPLIER = b.comment("Mana Pump and Manasteel Pump speed vs. a Create pump at the same RPM (user request: 2x; does not stack with the Manasteel pipe bonus).")
                .defineInRange("pumpSpeedMultiplier", 2.0, 0.1, 64.0);
        REACTOR_MANA_BUFFER = b.comment("Reactor output buffer (spec 3.3: 100,000).").defineInRange("reactorManaBuffer", 100_000, 1_000, Integer.MAX_VALUE);
        REACTOR_MAX_RADIUS = b.comment("Not in the spec (user request): the reactor bowl scales from 7x7 (radius 3) up to this radius.",
                        "Each step up adds the base output, lava burn, stress and buffers once more (9x9 = 2x, 15x15 = 5x).")
                .defineInRange("reactorMaxRadius", 7, 3, 12);
        REACTOR_LAVA_TANK_MB = b.comment("Reactor lava tank size in mB (not in the spec).").defineInRange("reactorLavaTankMb", 4_000, 100, 1_000_000);
        REACTOR_LAVA_MB_PER_TICK_AT_256 = b.comment("Lava burned per tick at 256 RPM; scales linearly with RPM (spec 3.3: 5.0).")
                .defineInRange("reactorLavaMbPerTickAt256", 5.0, 0.01, 1000.0);
        b.pop();

        b.push("elementium_logistics");
        ELEMENTIUM_MANA_PER_ITEM = b.comment("Mana a Smart Funnel or Smart Tunnel spends per item moved (not in the spec).")
                .defineInRange("manaPerItem", 5, 0, 100_000);
        ELEMENTIUM_MANA_BUFFER = b.comment("Mana buffer of each Elementium logistics block.").defineInRange("manaBuffer", 10_000, 100, 10_000_000);
        ELEMENTIUM_LINK_RANGE = b.comment("Smart Funnels and Tunnels follow the nearest Request Interface within this many blocks.")
                .defineInRange("requestLinkRange", 16, 1, 64);
        ELEMENTIUM_FUNNEL_INTERVAL = b.comment("Ticks between Smart Funnel transfers.").defineInRange("funnelInterval", 8, 1, 200);
        ELEMENTIUM_FUNNEL_BATCH = b.comment("Most items a Smart Funnel moves per transfer.").defineInRange("funnelBatch", 16, 1, 64);
        ELEMENTIUM_INTERFACE_UPKEEP = b.comment("Mana/tick a Request Interface uses while it has open requests.")
                .defineInRange("interfaceUpkeepPerTick", 1, 0, 10_000);
        b.pop();

        b.push("mana_battery");
        MANA_PER_LAYER = b.defineInRange("manaPerLayer", 2_500_000L, 1L, Long.MAX_VALUE / 16);
        MIN_LAYERS = b.defineInRange("minLayers", 2, 1, 64);
        MAX_LAYERS = b.defineInRange("maxLayers", 8, 1, 64);
        PASSIVE_IO_RATE = b.defineInRange("passiveIoRate", 1_000, 0, Integer.MAX_VALUE);
        PUMPED_IO_RATE = b.defineInRange("pumpedIoRate", 4_000, 0, Integer.MAX_VALUE);
        BATTERY_EXPLOSION_TERRAIN_DAMAGE = b.comment("Whether a detonating battery destroys terrain (entities are always hurt).")
                .define("batteryExplosionTerrainDamage", true);
        BATTERY_EXPLOSION_MIN_RADIUS = b.defineInRange("batteryExplosionMinRadius", 16, 1, 512);
        BATTERY_EXPLOSION_MAX_RADIUS = b.defineInRange("batteryExplosionMaxRadius", 200, 1, 512);
        BATTERY_FAILURE_TICKS = b.defineInRange("batteryFailureTicks", 200, 1, 72_000);
        BATTERY_SHELL_BLOCKS_PER_TICK = b.comment("Block-removal budget per server tick for the shell-batched blast.")
                .defineInRange("batteryShellBlocksPerTick", 200_000, 1_000, 10_000_000);
        BATTERY_EXPLOSION_MAX_ENTITY_DAMAGE = b.comment("Entity damage at the centre of a full (100%) detonation; scales with charge and falls off with distance (not in the spec).")
                .defineInRange("batteryExplosionMaxEntityDamage", 100.0, 0.0, 10_000.0);
        b.pop();

        b.push("corporea_engineering");
        ARCANE_SPARK_CAPACITY = b.comment("Arcane Spark mana buffer; spec default 10,000.").defineInRange("arcaneSparkCapacity",10000,1,1000000);
        CORPOREA_PORT_INTERVAL = b.comment("Ticks between automatic port transfers.").defineInRange("portTransferInterval",10,1,1200);
        CORPOREA_PORT_BATCH = b.comment("Maximum items moved by one port per transfer.").defineInRange("portTransferBatch",16,1,64);
        CORPOREA_MAX_WIRED_NODES = b.comment("Maximum nodes visited by one wired graph rebuild; oversized networks refuse access until reduced.").defineInRange("maxWiredNodes", 4096, 64, 65536);
        CONTROLLER_MIN_RPM = b.defineInRange("controllerMinRpm", 32, 1, 256);
        CONTROLLER_STRESS_PER_RPM = b.defineInRange("controllerStressPerRpm", 8.0, 0.0, 1024.0);
        ARCANE_SPARK_BASE_RANGE = b.defineInRange("arcaneSparkBaseRange", 8.0, 0.0, 256.0);
        ARCANE_SPARK_MAX_RANGE = b.defineInRange("arcaneSparkMaxRange", 48.0, 0.0, 256.0);
        ARCANE_SPARK_MANA_PER_BLOCK = b.defineInRange("arcaneSparkManaPerBlock", 250, 1, 1_000_000);
        ARCANE_SPARK_ACTIVE_DRAIN_PER_TICK = b.defineInRange("arcaneSparkActiveDrainPerTick", 2, 0, 1_000_000);
        MAX_COMPONENT_PAYLOAD_BYTES = b.comment("Items whose component data is larger than this are rejected by Terra Drives.")
                .defineInRange("maxComponentPayloadBytes", 8_192, 256, 2_097_152);
        b.pop();

        b.push("gaia_cannon");
        PROJECTILE_MANA_COST = b.defineInRange("projectileManaCost", 15_000, 0, Integer.MAX_VALUE);
        PROJECTILE_CHARGE_TICKS = b.defineInRange("projectileChargeTicks", 60, 1, 72_000);
        PROJECTILE_MAX_DAMAGE = b.defineInRange("projectileMaxDamage", 42.0, 0.0, 10_000.0);
        BEAM_MANA_PER_TICK = b.defineInRange("beamManaPerTick", 200, 0, Integer.MAX_VALUE);
        BEAM_DAMAGE_PER_HALF_SECOND = b.defineInRange("beamDamagePerHalfSecond", 7.0, 0.0, 10_000.0);
        BEAM_MAX_DURATION_TICKS = b.defineInRange("beamMaxDurationTicks", 200, 1, 72_000);
        BEAM_COOLDOWN_TICKS = b.defineInRange("beamCooldownTicks", 80, 0, 72_000);
        CANNON_MANA_BUFFER = b.comment("Cannon internal buffer (spec 3.8: 50,000).").defineInRange("cannonManaBuffer", 50_000, 1_000, Integer.MAX_VALUE);
        BEAM_RANGE = b.comment("Beam raycast range in blocks (spec 3.8: 64).").defineInRange("beamRange", 64, 1, 256);
        BOLT_BREAKS_TERRAIN = b.comment("Not in the spec (user request): the charged shot blasts terrain where it lands. The beam never does.")
                .define("boltBreaksTerrain", true);
        BOLT_BLAST_RADIUS = b.comment("Not in the spec (user request): terrain blast radius of a full-charge shot; scales with charge.",
                        "The blast never damages or knocks back entities (the shot's own damage is the spec's).")
                .defineInRange("boltBlastRadius", 3.0, 0.0, 16.0);
        b.pop();

        b.push("aeronautics");
        LEVITATION_HOVER_MANA_PER_TICK = b.defineInRange("levitationHoverManaPerTick", 15, 0, 1_000_000);
        LEVITATION_ACTIVE_MANA_PER_TICK = b.defineInRange("levitationActiveManaPerTick", 45, 0, 1_000_000);
        GAIA_THRUSTER_MAX_MANA_PER_TICK = b.defineInRange("gaiaThrusterMaxManaPerTick", 60, 0, 1_000_000);
        BIFROST_MANA_PER_BLOCK = b.defineInRange("bifrostManaPerBlock", 50, 0, 1_000_000);
        BIFROST_TRAIL_LIFETIME_TICKS = b.defineInRange("bifrostTrailLifetimeTicks", 200, 1, 72_000);
        BIFROST_MAX_ACTIVE_BLOCKS_PER_SHIP = b.defineInRange("bifrostMaxActiveBlocksPerShip", 64, 1, 4_096);
        FLUGEL_AUTOPILOT_MANA_PER_TICK = b.defineInRange("flugelAutopilotManaPerTick", 10, 0, 1_000_000);
        AIRSHIP_BLOCK_MANA_BUFFER = b.comment("Not in the spec: internal mana buffer of each pylon, thruster, Bifrost projector and Flugel core.")
                .defineInRange("airshipBlockManaBuffer", 10_000, 100, Integer.MAX_VALUE);
        LEVITATION_LIFT_CAPACITY = b.comment("Not in the spec: ship mass (Sable units; stone = 2 per block) one levitation pylon can hold up.",
                        "A pylon can push up to 1.5x this so a fully loaded ship can still climb. Heavier ships sink.")
                .defineInRange("levitationLiftCapacity", 150.0, 1.0, 1.0e9);
        LEVITATION_CLIMB_SPEED = b.comment("Not in the spec: vertical speed (blocks/s) at full climb or descent signal.")
                .defineInRange("levitationClimbSpeed", 4.0, 0.1, 64.0);
        GAIA_THRUSTER_MAX_THRUST = b.comment("Not in the spec: thrust at 100% throttle (Sable force units: mass x blocks/s^2).")
                .defineInRange("gaiaThrusterMaxThrust", 1200.0, 0.0, 1.0e9);
        FLUGEL_CRUISE_SPEED = b.comment("Not in the spec: top horizontal speed (blocks/s) of the Eye of the Flugel autopilot.")
                .defineInRange("flugelCruiseSpeed", 8.0, 0.5, 64.0);
        FLUGEL_ARRIVAL_HEIGHT = b.comment("Not in the spec: the autopilot holds the ship this many blocks above the bound position.")
                .defineInRange("flugelArrivalHeight", 6, 0, 256);
        FLUGEL_ARRIVAL_RADIUS = b.comment("Not in the spec: horizontal distance (blocks) at which the autopilot counts as arrived and stops draining mana.")
                .defineInRange("flugelArrivalRadius", 3, 1, 64);
        b.pop();

        // Viridian spec Phase 5a: Gaia Kinetic Lance and Sylvan armor (spec section 5, [gaia_gear] and the module rows).
        b.push("gaia_gear");
        LANCE_BASE_DAMAGE = b.defineInRange("lanceBaseDamage", 9.0, 0.0, 10_000.0);
        LANCE_MAX_VELOCITY_BONUS = b.defineInRange("lanceMaxVelocityBonus", 11.0, 0.0, 10_000.0);
        LANCE_STRIKE_MANA_COST = b.defineInRange("lanceStrikeManaCost", 250, 0, 1_000_000);
        LANCE_LUNGE_MANA_COST = b.comment("Total over the wind-up (spread evenly: 100 mana/t over 30 ticks).")
                .defineInRange("lanceLungeManaCost", 3000, 0, 10_000_000);
        LANCE_GALE_DAMAGE = b.defineInRange("lanceGaleDamage", 18.0, 0.0, 10_000.0);
        LANCE_COOLDOWN_TICKS = b.defineInRange("lanceCooldownTicks", 40, 0, 72_000);
        LANCE_AIR_PER_STRIKE = b.defineInRange("lanceAirPerStrike", 15, 0, 100_000);
        LANCE_LUNGE_AIR_COST = b.comment("Total over the wind-up (2 air/t over 30 ticks).")
                .defineInRange("lanceLungeAirCost", 60, 0, 100_000);
        LANCE_MANA_CAPACITY = b.comment("Spec 3.8: internal ManaItem capacity.").defineInRange("lanceManaCapacity", 50_000, 0, Integer.MAX_VALUE);
        LANCE_REACH_BONUS = b.comment("Spec 3.8: extra entity reach (blocks).").defineInRange("lanceReachBonus", 1.5, 0.0, 16.0);
        LANCE_LUNGE_CHARGE_TICKS = b.comment("Spec 3.8: wind-up time of the wind-lunge.").defineInRange("lanceLungeChargeTicks", 30, 1, 1200);
        LANCE_GALE_RANGE = b.comment("Spec 3.8: reach of the piercing gale shockwave (blocks).").defineInRange("lanceGaleRange", 8.0, 1.0, 64.0);
        LANCE_FULL_BONUS_SPEED = b.comment("Not in the spec: world speed (blocks/s) at which a strike gets the whole velocity bonus.",
                        "The bonus grows linearly from 0 at rest. Sprinting is ~5.6 b/s, a Flugel autopilot ship 8 b/s, a dive 20+.")
                .defineInRange("lanceFullBonusSpeed", 16.0, 0.5, 200.0);
        LANCE_LUNGE_SPEED = b.comment("Not in the spec: launch speed of the wind-lunge along the look direction (blocks/tick).")
                .defineInRange("lanceLungeSpeed", 1.5, 0.0, 10.0);
        LANCE_LUNGE_LIFT = b.comment("Not in the spec: extra upward launch speed of the wind-lunge (blocks/tick), so it arcs onto a deck.")
                .defineInRange("lanceLungeLift", 0.45, 0.0, 5.0);
        LANCE_GALE_KNOCKBACK = b.comment("Not in the spec: knockback strength of the gale (a sword's Knockback II is about 1.0).")
                .defineInRange("lanceGaleKnockback", 2.0, 0.0, 20.0);

        SYLVAN_MANA_REGEN_PER_TICK = b.comment("Spec 1.2: full-set passive mana regeneration (mana/t) into mana items you carry.")
                .defineInRange("sylvanManaRegenPerTick", 2, 0, 100_000);
        SYLVAN_MANA_DISCOUNT = b.comment("Spec 1.2: full-set Botania mana cost discount.").defineInRange("sylvanManaDiscount", 0.2, 0.0, 1.0);
        BRACER_TANKS_BASE_CAPACITY = b.comment("Spec: compressed air per Pneumatic Bracer Tanks module (two modules hold twice this).")
                .defineInRange("bracerTanksBaseCapacity", 1800, 0, 1_000_000);
        BRACER_TANKS_MANA_PER_AIR_TRICKLE = b.comment("Spec: mana per unit of air made by the tanks' trickle compressor.")
                .defineInRange("bracerTanksManaPerAirTrickle", 20, 1, 100_000);
        BRACER_TANKS_TRICKLE_AIR_PER_TICK = b.comment("Not in the spec: air the trickle compressor makes per tick while the tanks aren't full (0 = off).")
                .defineInRange("bracerTanksTrickleAirPerTick", 1, 0, 1000);
        MANA_PACK_CAPACITY = b.comment("Recipe sheet: mana stored per Livingrock Mana Pack module.")
                .defineInRange("manaPackCapacity", 500_000, 0, Integer.MAX_VALUE);
        MANA_PACK_REGEN_PER_TICK = b.comment("Recipe sheet: full-set regeneration (mana/t) while a Livingrock Mana Pack is fitted.")
                .defineInRange("manaPackRegenPerTick", 6, 0, 100_000);
        STRIDER_SPRINT_BOOST_MULTIPLIER = b.defineInRange("striderSprintBoostMultiplier", 1.40, 1.0, 10.0);
        STRIDER_AIR_PER_TICK = b.defineInRange("striderAirPerTick", 1, 0, 1000);
        STRIDER_MANA_FALLBACK_PER_TICK = b.comment("Spec 1.2: mana/t the striders use instead when there is no compressed air.")
                .defineInRange("striderManaFallbackPerTick", 5, 0, 100_000);
        STRIDER_STEP_HEIGHT = b.comment("Spec 1.2: step assist (block height walked up without jumping).")
                .defineInRange("striderStepHeight", 1.0, 0.6, 4.0);
        BOOT_THRUSTER_AIR_PER_BURST = b.defineInRange("bootThrusterAirPerBurst", 25, 0, 100_000);
        BOOT_THRUSTER_BURST_SPEED = b.comment("Not in the spec: speed of a deck-dash burst (blocks/tick).")
                .defineInRange("bootThrusterBurstSpeed", 1.0, 0.0, 10.0);
        BOOT_THRUSTER_COOLDOWN_TICKS = b.comment("Ticks between two deck-dash bursts.")
                .defineInRange("bootThrusterCooldownTicks", 10, 0, 1200);
        BOOT_THRUSTER_MAX_AIR_JUMPS = b.comment("Maximum thruster bursts before landing on ground or a ship deck.")
                .defineInRange("bootThrusterMaxAirJumps", 2, 0, 64);
        b.pop();

        SPEC = b.build();
    }

    private VEConfig() {}

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC);
    }
}
