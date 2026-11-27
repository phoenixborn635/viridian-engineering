package dev.viridianengineering.client;

import dev.viridianengineering.content.corporea.CorporeaTerminalMenu;
import dev.viridianengineering.content.corporea.TerminalView;
import dev.viridianengineering.network.VENetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Terminal UI. All lists are server-driven: this screen only draws the last
 * {@link VENetwork.TerminalPage} and sends {@link VENetwork.TerminalAction}s.
 */
public class CorporeaTerminalScreen extends AbstractContainerScreen<CorporeaTerminalMenu> {

    private static final int GRID_X = 8, GRID_Y = 30, COLS = 8, ROWS = 8;
    private static final String[] SORT_KEYS = {"name", "count", "mod"};
    static final net.minecraft.resources.ResourceLocation BG = dev.viridianengineering.ViridianEngineering.asResource("textures/gui/corporea_terminal.png");
    static final net.minecraft.resources.ResourceLocation WIDGETS = dev.viridianengineering.ViridianEngineering.asResource("textures/gui/terminal_widgets.png");
    // icon indices in terminal_widgets.png (see tools/gen_gui.py ICON_ORDER)
    static final int ICON_SORT = 0, ICON_TO_PATTERNS = 3, ICON_TO_ITEMS = 4, ICON_RECORD = 5, ICON_LEFT = 6, ICON_RIGHT = 7, ICON_JOBS = 9;
    private static final int TEXT = 0xff2a201b, TEXT_LIGHT = 0xfff1e4d0, TEXT_SCREEN = 0xffe8d6ff;

    private EditBox searchBox;
    private EditBox qtyBox;
    private IconButton modeButton;
    private boolean jobsOpen;
    private IconButton sortButton;
    private int sortIndex;
    private String lastSearch = "";
    private boolean showingPatterns;

    public CorporeaTerminalScreen(CorporeaTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 256;
        imageHeight = 266;
    }

    @Override
    protected void init() {
        super.init();
        searchBox = new EditBox(font, leftPos + 11, topPos + 17, 107, 9, Component.translatable("gui.viridian_engineering.search"));
        searchBox.setBordered(false);
        searchBox.setTextColor(0xfff3e9ff);
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.translatable("gui.viridian_engineering.search").withStyle(s -> s.withColor(0x9a7fc0)));
        addRenderableWidget(searchBox);
        sortButton = addRenderableWidget(new IconButton(leftPos + 122, topPos + 13, ICON_SORT, sortLabel(), () -> {
            sortIndex = (sortIndex + 1) % SORT_KEYS.length;
            sortButton.setIcon(ICON_SORT + sortIndex, sortLabel());
            send(VENetwork.Action.SORT, 0, 0, false, "");
        }));
        modeButton = addRenderableWidget(new IconButton(leftPos + 138, topPos + 13, ICON_TO_PATTERNS, modeLabel(false),
                () -> send(VENetwork.Action.MODE, 0, 0, false, "")));
        addRenderableWidget(new IconButton(leftPos + 232, topPos + 37, ICON_RECORD,
                Component.translatable("gui.viridian_engineering.record.tooltip"), () -> send(VENetwork.Action.RECORD, 0, 0, false, "")));
        addRenderableWidget(new IconButton(leftPos + 161, topPos + 121, ICON_LEFT,
                Component.translatable("gui.viridian_engineering.page.previous"), () -> send(VENetwork.Action.PAGE, -1, 0, false, "")));
        addRenderableWidget(new IconButton(leftPos + 234, topPos + 121, ICON_RIGHT,
                Component.translatable("gui.viridian_engineering.page.next"), () -> send(VENetwork.Action.PAGE, 1, 0, false, "")));
        qtyBox = new EditBox(font, leftPos + 195, topPos + 142, 36, 9, Component.translatable("gui.viridian_engineering.quantity"));
        qtyBox.setBordered(false);
        qtyBox.setTextColor(0xfff3e9ff);
        qtyBox.setMaxLength(7);
        qtyBox.setValue("1");
        qtyBox.setFilter(t -> t.chars().allMatch(Character::isDigit));
        addRenderableWidget(qtyBox);
        addRenderableWidget(new IconButton(leftPos + 235, topPos + 154, ICON_JOBS,
                Component.translatable("gui.viridian_engineering.active_jobs"), () -> {
                    jobsOpen = !jobsOpen;
                    send(VENetwork.Action.JOBS, jobsOpen ? 1 : 0, 0, false, "");
                }));
    }

    /** 14x14 wood button with a 10x10 glyph from terminal_widgets.png; its label is shown as a tooltip. */
    static final class IconButton extends net.minecraft.client.gui.components.AbstractButton {
        private final Runnable action;
        private int icon;

        IconButton(int x, int y, int icon, Component label, Runnable action) {
            super(x, y, 14, 14, label);
            this.icon = icon;
            this.action = action;
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(label));
        }

        void setIcon(int icon, Component label) {
            this.icon = icon;
            setMessage(label);
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(label));
        }

        @Override public void onPress() { action.run(); }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            int state = !active ? 2 : isHoveredOrFocused() ? 1 : 0;
            g.blit(WIDGETS, getX(), getY(), state * 14, 0, 14, 14, 128, 64);
            g.blit(WIDGETS, getX() + 2, getY() + 2 + (state == 1 ? 0 : 0), icon * 10, 16, 10, 10, 128, 64);
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    private static Component modeLabel(boolean patterns) {
        // The button names the view you switch to.
        return Component.translatable("gui.viridian_engineering.mode.switch",
                Component.translatable(patterns ? "gui.viridian_engineering.mode.items" : "gui.viridian_engineering.mode.patterns"));
    }

    private Component sortLabel() {
        return Component.translatable("gui.viridian_engineering.sort." + SORT_KEYS[sortIndex]);
    }

    private void send(VENetwork.Action action, int index, int button, boolean shift, String text) {
        PacketDistributor.sendToServer(new VENetwork.TerminalAction(menu.containerId, action.ordinal(), index, button, shift, text));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        VENetwork.TerminalPage current = menu.clientPage();
        boolean patterns = current != null && current.patterns();
        if (patterns != showingPatterns) {
            showingPatterns = patterns;
            modeButton.setIcon(patterns ? ICON_TO_ITEMS : ICON_TO_PATTERNS, modeLabel(patterns));
        }
        String now = searchBox.getValue();
        if (!now.equals(lastSearch)) {
            lastSearch = now;
            send(VENetwork.Action.SEARCH, 0, 0, false, now);
        }
    }

    // ------------------------------------------------------------------ input

    private int gridIndexAt(double mx, double my) {
        int x = (int) Math.floor((mx - leftPos - GRID_X) / 18);
        int y = (int) Math.floor((my - topPos - GRID_Y) / 18);
        if (mx < leftPos + GRID_X || my < topPos + GRID_Y || x < 0 || y < 0 || x >= COLS || y >= ROWS) return -1;
        return x + y * COLS;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (jobsOpen && overJobs(mx, my)) {
            var jobs = menu.clientJobs();
            int row = (int) Math.floor((my - topPos - 43) / 21);
            if (button == 0 && mx >= leftPos + 136 && jobs != null && row >= 0 && row < jobs.rows().size()
                    && jobs.rows().get(row).cancellable())
                send(VENetwork.Action.CANCEL_JOB, 0, 0, false, jobs.rows().get(row).id().toString());
            return true;
        }
        int index = gridIndexAt(mx, my);
        VENetwork.TerminalPage shown = menu.clientPage();
        if (index >= 0 && shown != null && shown.patterns()) {
            if (index >= shown.entries().size()) return true;
            if (button == 1 && Screen.hasShiftDown()) send(VENetwork.Action.REMOVE_PATTERN, index, 1, true, "");
            else if (button == 0) send(VENetwork.Action.CRAFT, index, 0, false, qtyBox.getValue().isEmpty() ? "1" : qtyBox.getValue());
            return true;
        }
        if (index >= 0 && (button == 0 || button == 1)) {
            boolean shift = Screen.hasShiftDown();
            if (!menu.getCarried().isEmpty() && !shift) {
                send(VENetwork.Action.DEPOSIT, 0, button, false, "");
            } else {
                send(VENetwork.Action.WITHDRAW, index, button, shift, "");
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private boolean overJobs(double x, double y) {
        return x >= leftPos + 8 && x < leftPos + 152 && y >= topPos + 30 && y < topPos + 174;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (jobsOpen && overJobs(x, y) && vertical != 0) {
            send(VENetwork.Action.JOB_SCROLL, vertical < 0 ? 1 : -1, 0, false, "");
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        for (EditBox box : new EditBox[] {searchBox, qtyBox}) {
            if (!box.isFocused()) continue;
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                box.setFocused(false);
                return true;
            }
            box.keyPressed(key, scan, mods);
            return true; // don't let the inventory key close the screen while typing
        }
        return super.keyPressed(key, scan, mods);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        g.blit(BG, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 272);
        VENetwork.TerminalPage page = menu.clientPage();
        if (jobsOpen) {
            drawJobs(g);
        } else if (page != null) {
            List<TerminalView.Entry> entries = page.entries();
            for (int i = 0; i < entries.size() && i < COLS * ROWS; i++) {
                int x = leftPos + GRID_X + (i % COLS) * 18, y = topPos + GRID_Y + (i / COLS) * 18;
                g.renderItem(entries.get(i).template(), x, y);
            }
        }
        int hovered = gridIndexAt(mx, my);
        if (!jobsOpen && hovered >= 0) {
            int x = leftPos + GRID_X + (hovered % COLS) * 18, y = topPos + GRID_Y + (hovered / COLS) * 18;
            g.fill(x, y, x + 16, y + 16, 0x50c9a0ff);
        }
        // status lamp
        if (page != null) {
            int lamp = page.powered() ? 0 : page.wireless() ? 2 : 1;
            g.blit(WIDGETS, leftPos + 162, topPos + 102, lamp * 6, 28, 6, 6, 128, 64);
        }
    }

    private void drawJobs(GuiGraphics g) {
        int x = leftPos + 8, y = topPos + 30;
        g.fill(x, y, x + 144, y + 144, 0xff241939);
        g.drawString(font, Component.translatable("gui.viridian_engineering.active_jobs"), x + 4, y + 2, TEXT_LIGHT, false);
        var jobs = menu.clientJobs();
        if (jobs == null || jobs.rows().isEmpty()) {
            g.drawString(font, Component.translatable("gui.viridian_engineering.no_job"), x + 4, y + 16, TEXT_SCREEN, false);
            return;
        }
        for (int i = 0; i < jobs.rows().size(); i++) {
            var row = jobs.rows().get(i);
            int top = y + 13 + i * 21;
            g.fill(x + 2, top, x + 142, top + 20, (i & 1) == 0 ? 0xff3d2b58 : 0xff35234d);
            g.drawString(font, font.plainSubstrByWidth(row.target(), 95), x + 4, top + 1, TEXT_LIGHT, false);
            String details = row.progress() + (row.machine().isEmpty() ? "" : " · " + row.machine());
            g.drawString(font, font.plainSubstrByWidth(details, 121), x + 4, top + 11, TEXT_SCREEN, false);
            if (row.cancellable()) g.drawString(font, "×", x + 131, top + 5, 0xffff9da9, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 2, TEXT_LIGHT, true);
        g.drawString(font, Component.translatable("gui.viridian_engineering.manual_crafting"), 162, 16, TEXT, false);

        VENetwork.TerminalPage page = menu.clientPage();
        if (page == null) return;
        // counts over the item icons, half size, bottom-right like vanilla
        List<TerminalView.Entry> entries = page.entries();
        if (!jobsOpen && !page.patterns()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            for (int i = 0; i < entries.size() && i < COLS * ROWS; i++) {
                String label = TerminalView.formatCount(entries.get(i).count());
                int x = GRID_X + (i % COLS) * 18 + 17, y = GRID_Y + (i / COLS) * 18 + 12;
                g.pose().pushPose();
                g.pose().scale(0.5f, 0.5f, 1f);
                g.drawString(font, label, (x * 2) - font.width(label) - 1, y * 2, 0xffffffff, true);
                g.pose().popPose();
            }
            g.pose().popPose();
        }

        Component status = Component.translatable(page.powered() ? "gui.viridian_engineering.status.powered"
                : page.wireless() ? "gui.viridian_engineering.status.paused" : "gui.viridian_engineering.status.unpowered");
        g.drawString(font, status, 171, 101, page.powered() ? 0xff1f6b2a : page.wireless() ? 0xff8a5a00 : 0xff9a2020, false);
        g.drawString(font, Component.translatable(page.patterns() ? "gui.viridian_engineering.pattern_count" : "gui.viridian_engineering.types",
                page.totalTypes()), 162, 112, TEXT, false);
        g.drawCenteredString(font, (page.page() + 1) + " / " + Math.max(1, page.pages()), 205, 124, TEXT_LIGHT);
        g.drawString(font, Component.translatable("gui.viridian_engineering.quantity"), 162, 142, TEXT, false);
        String job = menu.clientStatus();
        g.drawString(font, font.plainSubstrByWidth(job.isEmpty() ? Component.translatable("gui.viridian_engineering.no_job").getString() : job, 66), 164, 157, job.isEmpty() ? 0xff8a72aa : TEXT_SCREEN, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
        if (jobsOpen && overJobs(mx, my)) {
            var jobs = menu.clientJobs();
            int row = (int) Math.floor((my - topPos - 43) / 21);
            if (jobs != null && row >= 0 && row < jobs.rows().size()) {
                var current = jobs.rows().get(row);
                if (mx >= leftPos + 136 && current.cancellable())
                    g.renderTooltip(font, Component.translatable("gui.viridian_engineering.cancel_job"), mx, my);
                else if (!current.machine().isEmpty())
                    g.renderTooltip(font, Component.literal(current.machine()), mx, my);
            }
            return;
        }
        String status = menu.clientStatus();
        if (!status.isEmpty() && mx >= leftPos + 161 && mx < leftPos + 233 && my >= topPos + 155 && my < topPos + 167)
            g.renderTooltip(font, Component.literal(status), mx, my);
        VENetwork.TerminalPage shown = menu.clientPage();
        if (shown != null && mx >= leftPos + 161 && mx < leftPos + 240 && my >= topPos + 100 && my < topPos + 110)
            g.renderTooltip(font, Component.translatable(shown.powered() ? "gui.viridian_engineering.powered"
                    : shown.wireless() ? "gui.viridian_engineering.wireless_paused" : "gui.viridian_engineering.unpowered"), mx, my);
        int index = gridIndexAt(mx, my);
        VENetwork.TerminalPage page = menu.clientPage();
        if (index >= 0 && page != null && index < page.entries().size() && menu.getCarried().isEmpty()) {
            TerminalView.Entry e = page.entries().get(index);
            List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(e.template()));
            if (page.patterns())
                lines.add(Component.translatable("gui.viridian_engineering.pattern_help").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
            else
                lines.add(Component.literal(String.format("%,d", e.count())).withStyle(net.minecraft.ChatFormatting.AQUA));
            g.renderComponentTooltip(font, lines, mx, my);
        }
    }
}
