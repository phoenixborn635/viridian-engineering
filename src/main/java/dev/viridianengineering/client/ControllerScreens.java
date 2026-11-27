package dev.viridianengineering.client;

import dev.viridianengineering.ViridianEngineering;
import dev.viridianengineering.compat.SableCompat;
import dev.viridianengineering.config.VEConfig;
import dev.viridianengineering.content.airship.LevitationPylonBlockEntity;
import dev.viridianengineering.content.battery.BatteryControllerBlockEntity;
import dev.viridianengineering.content.elementium.RequestInterfaceBlockEntity;
import dev.viridianengineering.content.reactor.ReactorControllerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.locale.Language;

import java.util.List;
import java.util.Locale;

/**
 * Read-only status panels for the Mana Reactor and Mana Battery controllers (right-click the
 * controller). They draw the controller's synced block-entity state every frame; nothing is sent to
 * the server. Art: tools/gen_gui.py (reactor_controller.png / battery_controller.png) — keep the
 * coordinates here in step with it.
 */
public final class ControllerScreens {

    private ControllerScreens() {}

    public static void openReactor(BlockPos pos) {
        Minecraft.getInstance().setScreen(new Reactor(pos));
    }

    public static void openBattery(BlockPos pos) {
        Minecraft.getInstance().setScreen(new Battery(pos));
    }

    public static void openPylon(BlockPos pos) {
        Minecraft.getInstance().setScreen(new Pylon(pos));
    }

    public static void openRequests(BlockPos pos) {
        Minecraft.getInstance().setScreen(new Requests(pos));
    }

    private static final int LABEL = 0xff8ff0b4, VALUE = 0xffe6fff0, WARN = 0xffff8a7a, TITLE = 0xfff1e4d0;

    private abstract static class Panel<T extends BlockEntity> extends Screen {
        static final int W = 176, H = 126;
        final BlockPos pos;
        final Class<T> type;
        final ResourceLocation bg;
        int left, top;

        Panel(BlockPos pos, Class<T> type, String texture, Component title) {
            super(title);
            this.pos = pos;
            this.type = type;
            this.bg = ViridianEngineering.asResource("textures/gui/" + texture + ".png");
        }

        @Override
        protected void init() {
            left = (width - W) / 2;
            top = (height - H) / 2;
        }

        T be() {
            var level = Minecraft.getInstance().level;
            if (level == null) return null;
            BlockEntity be = level.getBlockEntity(pos);
            return type.isInstance(be) ? type.cast(be) : null;
        }

        @Override
        public void tick() {
            var player = Minecraft.getInstance().player;
            if (be() == null || player == null
                    || player.position().distanceToSqr(SableCompat.toWorld(player.level(), Vec3.atCenterOf(pos))) > 64)
                onClose();
        }

        @Override
        public boolean isPauseScreen() { return false; }

        @Override
        public void render(GuiGraphics g, int mx, int my, float partial) {
            super.render(g, mx, my, partial);
            g.blit(bg, left, top, 0, 0, W, H, 256, 256);
            g.drawString(font, title, left + 8, top + 2, TITLE, true);
            T be = be();
            if (be != null) draw(g, be, mx - left, my - top);
        }

        abstract void draw(GuiGraphics g, T be, int mx, int my);

        /** Vertical gauge filled from the bottom; u/v is the full-height fill strip in the texture. */
        void vGauge(GuiGraphics g, int x, int y, int w, int h, int u, int v, double frac) {
            int fill = (int) Math.round(Math.max(0, Math.min(1, frac)) * h);
            if (fill > 0) g.blit(bg, left + x, top + y + h - fill, u, v + h - fill, w, fill, 256, 256);
        }

        void text(GuiGraphics g, Component c, int x, int y, int colour) {
            g.drawString(font, c, left + x, top + y, colour, false);
        }

        int wrapped(GuiGraphics g, Component c, int x, int y, int width, int colour) {
            List<FormattedCharSequence> lines = font.split(c, width);
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, left + x, top + y, colour, false);
                y += 10;
            }
            return y;
        }

        static boolean in(int mx, int my, int x, int y, int w, int h) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }

        static String num(long n) { return String.format("%,d", n); }
    }

    private static final class Pylon extends Panel<LevitationPylonBlockEntity> {
        Pylon(BlockPos pos) {
            super(pos, LevitationPylonBlockEntity.class, "levitation_pylon",
                    Component.translatable("block.viridian_engineering.mana_levitation_pylon"));
        }

        @Override
        void draw(GuiGraphics g, LevitationPylonBlockEntity p, int mx, int my) {
            vGauge(g, 11, 17, 26, 98, 212, 0, (double) p.mana.getStoredMana() / Math.max(1, p.mana.getManaCapacity()));
            text(g, Component.translatable("viridian_engineering.goggles.pylon.mode."
                    + p.mode().name().toLowerCase(Locale.ROOT)), 46, 17, LABEL);
            text(g, Component.translatable("viridian_engineering.goggles.mana_per_tick",
                    LevitationPylonBlockEntity.manaFor(p.mode())), 46, 30, VALUE);
            text(g, Component.translatable("gui.viridian_engineering.pylon.signal", p.signal()), 46, 47, LABEL);
            text(g, Component.translatable("gui.viridian_engineering.pylon.hover"), 46, 61, VALUE);
            text(g, Component.translatable("gui.viridian_engineering.pylon.ascend"), 46, 73, VALUE);
            text(g, Component.translatable("gui.viridian_engineering.pylon.descend"), 46, 85, VALUE);
            if (in(mx, my, 11, 17, 26, 98))
                g.renderTooltip(font, Component.translatable("viridian_engineering.goggles.mana",
                        num(p.mana.getStoredMana()), num(p.mana.getManaCapacity())), left + mx, top + my);
        }
    }

    private static final class Requests extends Panel<RequestInterfaceBlockEntity> {
        private static final int ROWS = 4;
        private int first;

        Requests(BlockPos pos) {
            super(pos, RequestInterfaceBlockEntity.class, "request_interface",
                    Component.translatable("block.viridian_engineering.elementium_request_interface"));
        }

        @Override
        void draw(GuiGraphics g, RequestInterfaceBlockEntity requests, int mx, int my) {
            boolean online = requests.isOnline();
            text(g, Component.translatable(online ? "gui.viridian_engineering.requests.online"
                    : "gui.viridian_engineering.requests.offline"), 10, 17, online ? LABEL : WARN);
            String mana = num(requests.mana.getStoredMana()) + " / " + num(requests.mana.getManaCapacity());
            text(g, Component.literal(mana), 166 - font.width(mana), 17, VALUE);
            var requestsByItem = requests.requests();
            int countOfRequests = requestsByItem.size();
            first = Math.clamp(first, 0, Math.max(0, countOfRequests - ROWS));
            if (countOfRequests == 0) text(g, Component.translatable("viridian_engineering.request_interface.empty"), 10, 38, LABEL);
            var visible = requestsByItem.entrySet().stream().skip(first).limit(ROWS).iterator();
            for (int i = 0; visible.hasNext(); i++) {
                var row = visible.next();
                var item = row.getKey().toStack(1);
                int y = 30 + 17 * i;
                g.renderItem(item, left + 10, top + y);
                String count = num(row.getValue());
                int x = 165 - font.width(count);
                g.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(item.getHoverName(), Math.max(0, x - 33))), left + 28, top + y + 4, VALUE, false);
                text(g, Component.literal(count), x, y + 4, LABEL);
                if (in(mx, my, 8, y, 162, 16))
                    g.renderTooltip(font, item, left + mx, top + my);
            }
            if (countOfRequests > ROWS)
                text(g, Component.literal((first + 1) + "–" + (first + ROWS) + " / " + countOfRequests), 10, 106, LABEL);
            else
                text(g, Component.translatable("gui.viridian_engineering.requests.hint"), 10, 106, LABEL);
        }

        @Override
        public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
            if (vertical == 0 || !in((int) x - left, (int) y - top, 5, 28, 165, 70)) return false;
            var requests = be();
            if (requests == null) return false;
            first = Math.clamp(first + (vertical < 0 ? 1 : -1), 0, Math.max(0, requests.requests().size() - ROWS));
            return true;
        }
    }

    // ================================================================================== reactor
    private static final class Reactor extends Panel<ReactorControllerBlockEntity> {
        Reactor(BlockPos pos) {
            super(pos, ReactorControllerBlockEntity.class, "reactor_controller",
                    Component.translatable("block.viridian_engineering.terrasteel_mana_reactor_controller"));
        }

        @Override
        void draw(GuiGraphics g, ReactorControllerBlockEntity r, int mx, int my) {
            double lavaFrac = r.lava.getCapacity() <= 0 ? 0 : (double) r.lava.getFluidAmount() / r.lava.getCapacity();
            double manaFrac = r.mana.getManaCapacity() <= 0 ? 0 : (double) r.mana.getStoredMana() / r.mana.getManaCapacity();
            vGauge(g, 10, 17, 14, 62, 180, 0, lavaFrac);
            vGauge(g, 38, 17, 14, 62, 196, 0, manaFrac);
            g.drawCenteredString(font, Component.translatable("gui.viridian_engineering.reactor.lava"), left + 17, top + 83, LABEL);
            g.drawCenteredString(font, Component.translatable("gui.viridian_engineering.reactor.mana"), left + 45, top + 83, LABEL);

            int min = VEConfig.REACTOR_MIN_RPM.get();
            float rpm = Math.abs(r.rpm());
            if (!r.isFormed()) {
                text(g, Component.translatable("gui.viridian_engineering.reactor.unformed"), 60, 17, WARN);
                wrapped(g, Component.translatable("gui.viridian_engineering.reactor.unformed_hint"), 60, 31, 104, LABEL);
            } else {
                int y = 17;
                text(g, Component.translatable("gui.viridian_engineering.reactor.size", 2 * r.radius() + 1, 2 * r.radius() + 1, r.factor()), 60, y, VALUE);
                text(g, Component.translatable("viridian_engineering.goggles.reactor.su", String.format("%.0f", r.stress())), 60, y += 12, VALUE);
                text(g, Component.literal(String.format("-%.2f mB/t ", r.lavaRate())).append(Component.translatable("gui.viridian_engineering.reactor.lava")),
                        60, y += 12, 0xffffb46a);
                text(g, Component.literal(String.format("+%.1f /t ", r.manaRate())).append(Component.translatable("gui.viridian_engineering.reactor.mana")),
                        60, y += 12, 0xff9fd6ff);
                Component status = r.isRunning() ? Component.translatable("gui.viridian_engineering.reactor.running")
                        : Component.translatable(rpm < min ? "viridian_engineering.goggles.reactor.slow"
                        : r.lava.getFluidAmount() < 5 ? "viridian_engineering.goggles.reactor.no_lava"
                        : "viridian_engineering.goggles.reactor.full", min);
                wrapped(g, status, 60, y + 16, 104, r.isRunning() ? LABEL : WARN);
            }

            // shaft speed bar (0..256 RPM) with the minimum marked
            text(g, Component.translatable("gui.viridian_engineering.reactor.speed"), 11, 94, LABEL);
            String speed = String.format("%.0f / %d RPM", rpm, min);
            g.drawString(font, speed, left + 165 - font.width(speed), top + 94, rpm >= min ? VALUE : WARN, false);
            int bar = (int) Math.round(Math.min(1, rpm / 256.0) * 154);
            if (bar > 0) g.blit(bg, left + 11, top + 106, 0, rpm >= min ? 200 : 210, bar, 6, 256, 256);
            int mark = left + 11 + (int) Math.round(Math.min(1, min / 256.0) * 154);
            g.fill(mark, top + 105, mark + 1, top + 113, 0xfff3d9a0);

            if (in(mx, my, 10, 17, 14, 62))
                g.renderTooltip(font, Component.literal(num(r.lava.getFluidAmount()) + " / " + num(r.lava.getCapacity()) + " mB"), mx + left, my + top);
            else if (in(mx, my, 38, 17, 14, 62))
                g.renderTooltip(font, Component.literal(num(r.mana.getStoredMana()) + " / " + num(r.mana.getManaCapacity()) + " mana"), mx + left, my + top);
        }
    }

    // ================================================================================== battery
    private static final class Battery extends Panel<BatteryControllerBlockEntity> {
        Battery(BlockPos pos) {
            super(pos, BatteryControllerBlockEntity.class, "battery_controller",
                    Component.translatable("block.viridian_engineering.terrasteel_mana_battery_controller"));
        }

        @Override
        void draw(GuiGraphics g, BatteryControllerBlockEntity b, int mx, int my) {
            boolean unstable = b.isUnstable();
            double fill = unstable ? b.failureFill() : b.fillFraction();
            vGauge(g, 11, 17, 26, 98, 212, unstable ? 100 : 0, fill);
            int layers = Math.max(1, b.validLayers());
            for (int k = 1; k < layers; k++) {
                int y = top + 17 + Math.round(k * 98f / layers);
                g.fill(left + 11, y, left + 37, y + 1, 0x70000000);
            }

            int y = 17;
            if (unstable) {
                boolean blink = (Minecraft.getInstance().level.getGameTime() / 8) % 2 == 0;
                y = wrapped(g, Component.translatable("gui.viridian_engineering.battery.unstable"), 46, y, 122, blink ? WARN : 0xffffd0c8);
                y += 4;
            } else if (!b.isFormed()) {
                y = wrapped(g, Component.translatable("viridian_engineering.goggles.battery.unformed", VEConfig.MIN_LAYERS.get()), 46, y, 122, WARN);
                y += 4;
            } else {
                text(g, Component.translatable("gui.viridian_engineering.battery.stable"), 46, y, LABEL);
                y += 12;
            }
            text(g, Component.translatable("viridian_engineering.goggles.battery.layers", b.validLayers(), VEConfig.MAX_LAYERS.get()), 46, y, VALUE);
            text(g, Component.translatable("gui.viridian_engineering.battery.stored", num(b.mana.getStoredMana())), 46, y += 12, 0xff9fd6ff);
            text(g, Component.translatable("gui.viridian_engineering.battery.capacity", num(b.maxCapacity())), 46, y += 12, VALUE);
            text(g, Component.translatable("gui.viridian_engineering.battery.charge", String.format("%.1f", fill * 100)), 46, y += 12,
                    fill > 0.5 ? 0xfff3d9a0 : VALUE);
            if (b.isFormed() && !unstable) {
                text(g, Component.translatable("gui.viridian_engineering.battery.io", num(b.ioLimit())), 46, y += 12, VALUE);
                text(g, Component.translatable("gui.viridian_engineering.battery.mode", Component.translatable(b.isPumped()
                        ? "viridian_engineering.goggles.battery.pumped" : "viridian_engineering.goggles.battery.passive")), 46, y += 12, VALUE);
                if (fill > 0) wrapped(g, Component.translatable("gui.viridian_engineering.battery.risk", String.format("%.0f", fill * 100)),
                        46, y + 14, 122, fill > 0.5 ? WARN : LABEL);
            }

            if (in(mx, my, 11, 17, 26, 98))
                g.renderTooltip(font, Component.literal(num(b.mana.getStoredMana()) + " / " + num(b.maxCapacity()) + " mana"), mx + left, my + top);
        }
    }
}
