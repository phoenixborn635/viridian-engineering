package dev.viridianengineering.registry;

import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.entry.ItemEntry;
import dev.viridianengineering.content.drive.ArchivePylonBlock;
import dev.viridianengineering.content.drive.ArchivePylonBlockEntity;
import dev.viridianengineering.content.drive.TerraDriveItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.material.MapColor;

import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;
import static dev.viridianengineering.ViridianEngineering.REGISTRATE;

/** Phase 3 storage: Terra Drives (spec 3.6) and the 4-slot Corporea Archive Pylon. */
public final class VEStorage {

    public static final ItemEntry<TerraDriveItem> TERRA_DRIVE_16K = drive("terra_drive_16k", "Terra Drive (16K)", 16_384, Rarity.COMMON);
    public static final ItemEntry<TerraDriveItem> TERRA_DRIVE_64K = drive("terra_drive_64k", "Terra Drive (64K)", 65_536, Rarity.UNCOMMON);
    public static final ItemEntry<TerraDriveItem> TERRA_DRIVE_256K = drive("terra_drive_256k", "Terra Drive (256K)", 262_144, Rarity.RARE);
    public static final ItemEntry<TerraDriveItem> TERRA_DRIVE_1M = drive("terra_drive_1m", "Terra Drive (1M)", 1_048_576, Rarity.EPIC);

    private static ItemEntry<TerraDriveItem> drive(String id, String name, long capacity, Rarity rarity) {
        return REGISTRATE.item(id, p -> new TerraDriveItem(p, capacity))
                .properties(p -> p.stacksTo(1).rarity(rarity))
                .lang(name)
                .register();
    }

    public static final BlockEntry<ArchivePylonBlock> ARCHIVE_PYLON = REGISTRATE
            .block("corporea_archive_pylon", ArchivePylonBlock::new)
            .initialProperties(SharedProperties::stone)
            .properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_PURPLE).lightLevel(s -> {
                int lit = 0;
                for (var bay : ArchivePylonBlock.BAY) if (s.getValue(bay)) lit++;
                return lit == 0 ? 0 : 5 + lit * 2;
            }))
            .transform(pickaxeOnly())
            .blockstate((c, p) -> {}) // hand-written multipart from tools/gen_archive_pylon.py
            .lang("Corporea Archive Pylon")
            .item().model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/corporea_archive_pylon"))).build()
            .register();

    public static final BlockEntityEntry<ArchivePylonBlockEntity> ARCHIVE_PYLON_BE = REGISTRATE
            .blockEntity("corporea_archive_pylon", ArchivePylonBlockEntity::new)
            .validBlocks(ARCHIVE_PYLON)
            .renderer(() -> dev.viridianengineering.client.ArchivePylonRenderer::new)
            .register();

    private VEStorage() {}

    public static void register() {}
}
