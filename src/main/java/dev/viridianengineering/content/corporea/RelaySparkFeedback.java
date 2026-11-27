package dev.viridianengineering.content.corporea;

import com.simibubi.create.AllItems;
import dev.viridianengineering.compat.SableCompat;
import dev.viridianengineering.registry.VECorporea;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;
import velrondevs.botania.registry.BotaniaItems;

public final class RelaySparkFeedback {
    private static final DustParticleOptions LINK = new DustParticleOptions(new Vector3f(0.72f, 0.45f, 1), 0.85f);

    private RelaySparkFeedback() {}

    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().getGameTime() % 6 != 0
                || !(showsLinks(player.getMainHandItem()) || showsLinks(player.getOffhandItem()))) return;
        var level = player.serverLevel();
        int remaining = 12;
        for (ArcaneSparkEntity spark : ArcaneSparkEntity.loaded(level)) {
            if (spark.attachmentPos() == null) continue;
            Vec3 from = SableCompat.toWorld(level, spark.position());
            if (from.distanceToSqr(player.position()) > 48 * 48) continue;
            for (ArcaneSparkEntity other : ArcaneSparkEntity.linksOf(spark)) {
                Vec3 to = SableCompat.toWorld(level, other.position());
                if (spark.getId() >= other.getId() && to.distanceToSqr(player.position()) <= 48 * 48) continue;
                int points = Math.clamp((int) Math.ceil(from.distanceTo(to)), 2, 24);
                for (int i = 1; i < points; i++) {
                    Vec3 point = from.lerp(to, (double) i / points);
                    level.sendParticles(player, LINK, false, point.x, point.y, point.z, 1, 0, 0, 0, 0);
                }
                if (--remaining == 0) return;
            }
        }
    }

    private static boolean showsLinks(ItemStack held) {
        return VECorporea.ARCANE_SPARK.isIn(held) || AllItems.GOGGLES.isIn(held)
                || held.is(BotaniaItems.twigWand) || held.is(BotaniaItems.dreamwoodWand);
    }
}
