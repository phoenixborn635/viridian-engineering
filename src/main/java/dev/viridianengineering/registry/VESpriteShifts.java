package dev.viridianengineering.registry;

import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import dev.viridianengineering.ViridianEngineering;

/** Connected-texture sheets for the casings (block/&lt;name&gt;.png + block/&lt;name&gt;_connected.png). */
public final class VESpriteShifts {

    public static final CTSpriteShiftEntry
            MANASTEEL_CASING = omni("manasteel_casing"),
            ELEMENTIUM_CASING = omni("elementium_casing"),
            TERRASTEEL_CASING = omni("terrasteel_casing");

    private VESpriteShifts() {}

    private static CTSpriteShiftEntry omni(String name) {
        return CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL,
                ViridianEngineering.asResource("block/" + name),
                ViridianEngineering.asResource("block/" + name + "_connected"));
    }
}
