package dev.viridianengineering.content.pump;

import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.registry.VEBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Mana Pump (for Terrasteel mana pipes) and the Manasteel Pump (for fluids). Both are Create
 * Mechanical Pumps underneath (same placement, cog, rotation rules), retextured Botania-first, and
 * both move {@code pumpSpeedMultiplier} (2x) as much as a plain pump at the same RPM.
 * <p>
 * The 2x does not stack with the Manasteel pipe's own 2x: a Manasteel pump feeding Manasteel pipes
 * is still 2x ({@link PumpBoost}).
 */
public final class VEPumps {

    private VEPumps() {}

    /** Manasteel Pump: a Create pump whose pressure is boosted while it distributes it. */
    public static class ManasteelPump extends PumpBlock {
        public ManasteelPump(Properties p) { super(p); }

        @Override
        public BlockEntityType<? extends PumpBlockEntity> getBlockEntityType() {
            return VEBlockEntities.MANASTEEL_PUMP.get();
        }
    }

    public static class ManasteelPumpEntity extends PumpBlockEntity {
        public ManasteelPumpEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

        @Override
        protected void distributePressureTo(Direction side) {
            PumpBoost.begin(VEConfig.PUMP_SPEED_MULTIPLIER.get().floatValue());
            try {
                super.distributePressureTo(side);
            } finally {
                PumpBoost.end();
            }
        }
    }

    /**
     * Mana Pump: drives Terrasteel mana pipes (the pipes look for any {@link PumpBlock}); its pumped
     * mana rate is multiplied by {@code pumpSpeedMultiplier}. It moves no fluid.
     */
    public static class ManaPump extends PumpBlock {
        public ManaPump(Properties p) { super(p); }

        @Override
        public BlockEntityType<? extends PumpBlockEntity> getBlockEntityType() {
            return VEBlockEntities.MANA_PUMP.get();
        }
    }

    public static class ManaPumpEntity extends PumpBlockEntity {
        public ManaPumpEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

        @Override
        protected void distributePressureTo(Direction side) {
            // mana only: never push fluid pressure into Create pipes
        }
    }

    /** Thread-confined pressure multiplier set while a Manasteel pump distributes pressure. */
    public static final class PumpBoost {
        private static final ThreadLocal<Float> BOOST = new ThreadLocal<>();

        private PumpBoost() {}

        static void begin(float multiplier) { BOOST.set(multiplier); }

        static void end() { BOOST.remove(); }

        /** The active pump boost, or 1. */
        public static float current() {
            Float f = BOOST.get();
            return f == null ? 1f : f;
        }

        public static boolean active() { return BOOST.get() != null; }
    }
}
