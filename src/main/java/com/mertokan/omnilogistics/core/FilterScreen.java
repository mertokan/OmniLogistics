package com.mertokan.omnilogistics.core;

import com.mertokan.omnilogistics.api.ComponentPredicateEngine;
import com.mertokan.omnilogistics.api.FilterSpec;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Filter GUI: ghost slot, side/mode buttons, 3x3 flag grid, and two pickers
 * (item tags / component keys) that open as an overlay listing what the reference item actually has.
 * Scroll moves the cursor, Enter or click adds/removes the entry; both lists are multi-select.
 */
public class FilterScreen extends DarkScreen<FilterMenu> {
    public static final int WIDTH = 256, HEIGHT = 242;
    private static final String[] SIDE = {"D", "U", "N", "S", "W", "E"};   // Direction ordinal order
    private static final String[] SIDE_KEY = {"down", "up", "north", "south", "west", "east"};
    private static final int[] FLAG = {
        ComponentPredicateEngine.MATCH_ITEM, ComponentPredicateEngine.MATCH_TAG, ComponentPredicateEngine.MATCH_ENCHANTS,
        ComponentPredicateEngine.MATCH_COMPONENTS, ComponentPredicateEngine.MATCH_SELECTED, ComponentPredicateEngine.HAS_ENCHANTS,
        ComponentPredicateEngine.HAS_MOD_COMPONENTS, ComponentPredicateEngine.IGNORE_DAMAGE, ComponentPredicateEngine.INVERT};
    private static final String[] FLAG_KEY = {"match_item", "match_tag", "match_enchants", "match_components", "match_selected",
        "has_enchants", "mod_components", "ignore_damage", "invert"};
    private static final int ON_PUSH = 0xFF9C5A10;
    private static final int KEY_ESC = 256, KEY_ENTER = 257, KEY_KP_ENTER = 335, KEY_UP = 265, KEY_DOWN = 264;

    private enum Picker { NONE, TAG, COMPONENT }
    private static final int PICK_X = 28, PICK_Y = 36, PICK_W = 200, PICK_H = 132, ROW_H = 11, ROWS = 10;

    private final int shift;
    private final ToggleButton[] modeButtons;
    private final ToggleButton[] flagButtons = new ToggleButton[FLAG.length];
    private ToggleButton tagButton, compButton;
    private Picker picker = Picker.NONE;
    private int cursor, scroll;
    private ComponentPredicateEngine.@Nullable ComponentInfo hoverValue;

    /** One panel sheet per grid height: 1 and 4 references fit the base panel, 16 adds a row, 64 adds seven. */
    private static String sheet(int refs) {
        return FilterMenu.rows(refs) <= 1 ? "filter" : FilterMenu.rows(refs) <= 2 ? "filter_tall" : "filter_huge";
    }

    public FilterScreen(FilterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, sheet(menu.refs), WIDTH, FilterMenu.height(menu.refs));
        modeButtons = new ToggleButton[menu.layout.modeCount()];
        shift = FilterMenu.shift(menu.refs);
        inventoryLabelX = FilterMenu.INV_X;
        inventoryLabelY = FilterMenu.INV_Y + shift - 10;
    }

    @Override
    protected void init() {
        super.init();
        FilterLayout layout = menu.layout;
        int n = layout.sides;
        for (int i = 0; i < n; i++) {
            int idx = i;
            modeButtons[i] = n == 6
                ? new ToggleButton(leftPos + 8 + i * 40, topPos + 42 + shift, 38, 18, () -> cycle(idx))
                : new ToggleButton(leftPos + 8, topPos + 42 + shift, 240, 18, () -> cycle(idx));
            Component tip = n == 6
                ? Component.translatable("tooltip.omnilogistics.side." + SIDE_KEY[i]).append("\n")
                    .append(Component.translatable("tooltip.omnilogistics.mode." + layout.name().toLowerCase(java.util.Locale.ROOT)))
                : Component.translatable("tooltip.omnilogistics.mode.card");
            modeButtons[i].setTooltip(Tooltip.create(tip));
            addRenderableWidget(modeButtons[i]);
        }
        for (int i = 0; i < FLAG.length; i++) {
            int flag = FLAG[i];
            flagButtons[i] = new ToggleButton(leftPos + 8 + (i % 3) * 82, topPos + 64 + shift + (i / 3) * 22, 78, 18, () -> toggle(flag));
            flagButtons[i].setTooltip(Tooltip.create(Component.translatable("tooltip.omnilogistics.flag." + FLAG_KEY[i])));
            addRenderableWidget(flagButtons[i]);
        }
        tagButton = addRenderableWidget(new ToggleButton(leftPos + 8, topPos + 130 + shift, 120, 18, () -> open(Picker.TAG)));
        tagButton.setTooltip(Tooltip.create(Component.translatable("tooltip.omnilogistics.pick_tag")));
        compButton = addRenderableWidget(new ToggleButton(leftPos + 130, topPos + 130 + shift, 118, 18, () -> open(Picker.COMPONENT)));
        compButton.setTooltip(Tooltip.create(Component.translatable("tooltip.omnilogistics.pick_components")));
    }

    // ---- sending ----------------------------------------------------------------

    private void send(byte[] modes, int flags, List<String> tags, List<String> comps) {
        PacketDistributor.sendToServer(FilterConfigPayload.of(menu, modes, flags, tags, comps));
    }

    private void cycle(int i) {
        byte[] m = menu.modes();
        m[i] = (byte) ((m[i] + 1) % menu.layout.modeNames.length);
        FilterSpec s = menu.spec();
        send(m, s.flags(), s.tags(), s.components());
    }

    private void toggle(int flag) {
        FilterSpec s = menu.spec();
        send(menu.modes(), s.flags() ^ flag, s.tags(), s.components());
    }

    /** Add or remove the entry; the matching flag follows whether the list is empty. */
    private void togglePick(String key) {
        FilterSpec s = menu.spec();
        boolean tags = picker == Picker.TAG;
        List<String> list = new ArrayList<>(tags ? s.tags() : s.components());
        if (!list.remove(key)) list.add(key);
        int flag = tags ? ComponentPredicateEngine.MATCH_TAG : ComponentPredicateEngine.MATCH_SELECTED;
        int flags = list.isEmpty() ? s.flags() & ~flag : s.flags() | flag;
        send(menu.modes(), flags, tags ? list : s.tags(), tags ? s.components() : list);
    }

    // ---- picker overlay ---------------------------------------------------------

    private void open(Picker p) {
        picker = picker == p ? Picker.NONE : p;
        cursor = scroll = 0;
    }

    private List<String> options() {
        FilterSpec s = menu.spec();
        return switch (picker) {
            case TAG -> ComponentPredicateEngine.tagKeys(s.ref());
            case COMPONENT -> ComponentPredicateEngine.componentKeys(s.ref());
            default -> List.of();
        };
    }

    private boolean selected(String opt) {
        FilterSpec s = menu.spec();
        return (picker == Picker.TAG ? s.tags() : s.components()).contains(opt);
    }

    private void moveCursor(int delta, int count) {
        if (count == 0) return;
        cursor = Math.floorMod(cursor + delta, count);
        if (cursor < scroll) scroll = cursor;
        if (cursor >= scroll + ROWS) scroll = cursor - ROWS + 1;
    }

    private void renderPicker(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos + PICK_X, y = topPos + PICK_Y + shift;
        g.fill(x - 2, y - 2, x + PICK_W + 2, y + PICK_H + 2, 0xF00C0E11);
        g.fill(x, y, x + PICK_W, y + PICK_H, 0xFF1A1E25);
        g.renderOutline(x, y, PICK_W, PICK_H, 0xFF3FD3FF);
        g.fill(x, y, x + PICK_W, y + 13, 0xFF232830);
        Component title = Component.translatable(picker == Picker.TAG ? "gui.omnilogistics.pick_tag" : "gui.omnilogistics.pick_components");
        g.drawString(font, font.plainSubstrByWidth(title.getString(), PICK_W - 10), x + 5, y + 3, TEXT, false);
        List<String> opts = options();
        List<ComponentPredicateEngine.ComponentInfo> infos = picker == Picker.COMPONENT
            ? ComponentPredicateEngine.componentInfos(menu.spec().ref()) : List.of();
        hoverValue = null;
        if (opts.isEmpty()) {
            g.drawString(font, Component.translatable("gui.omnilogistics.pick_empty"), x + 5, y + 20, MUTED, false);
            return;
        }
        cursor = Math.min(cursor, opts.size() - 1);
        scroll = Math.max(0, Math.min(scroll, opts.size() - ROWS));
        for (int i = 0; i < ROWS && i + scroll < opts.size(); i++) {
            int idx = i + scroll;
            String opt = opts.get(idx);
            int ry = y + 15 + i * ROW_H;
            boolean hover = mouseX >= x && mouseX < x + PICK_W && mouseY >= ry && mouseY < ry + ROW_H;
            boolean on = selected(opt);
            if (on) g.fill(x + 2, ry, x + PICK_W - 7, ry + ROW_H, 0xFF1F7A8C);
            else if (hover) g.fill(x + 2, ry, x + PICK_W - 7, ry + ROW_H, 0xFF2A2F37);
            if (idx == cursor) g.renderOutline(x + 2, ry, PICK_W - 9, ROW_H, 0xFF3FD3FF);
            ComponentPredicateEngine.ComponentInfo info = picker == Picker.COMPONENT && idx < infos.size() ? infos.get(idx) : null;
            String label = (on ? "✓ " : "  ") + (info != null && info.patched() ? "* " : "") + opt;
            int labelW = Math.min(font.width(label), PICK_W - 22);
            g.drawString(font, font.plainSubstrByWidth(label, labelW), x + 5, ry + 2, on ? 0xFFFFFF : TEXT, false);
            if (info != null) {
                int vx = x + 5 + labelW + 6, vw = x + PICK_W - 8 - vx;
                if (vw > 20) g.drawString(font, font.plainSubstrByWidth(info.value(), vw), vx, ry + 2, MUTED, false);
                if (hover) hoverValue = info;
            }
        }
        int max = opts.size() - ROWS;
        if (max > 0) {
            int trackH = ROWS * ROW_H, thumb = Math.max(8, trackH * ROWS / opts.size());
            int ty = y + 15 + (trackH - thumb) * scroll / max;
            g.fill(x + PICK_W - 5, y + 15, x + PICK_W - 2, y + 15 + trackH, 0xFF0C0E11);
            g.fill(x + PICK_W - 5, ty, x + PICK_W - 2, ty + thumb, 0xFF3FD3FF);
        }
        g.drawString(font, Component.translatable("gui.omnilogistics.pick_hint"), x + 5, y + PICK_H - 10, MUTED, false);
        if (hoverValue != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(hoverValue.key()).withStyle(net.minecraft.ChatFormatting.AQUA));
            if (hoverValue.patched()) lines.add(Component.translatable("gui.omnilogistics.component_patched").withStyle(net.minecraft.ChatFormatting.GRAY));
            String v = hoverValue.value();
            for (int i = 0; i < v.length() && i < 600; i += 60) lines.add(Component.literal(v.substring(i, Math.min(v.length(), i + 60))));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (picker == Picker.NONE) return super.mouseClicked(mx, my, button);
        int x = leftPos + PICK_X, y = topPos + PICK_Y + shift;
        if (mx < x || mx >= x + PICK_W || my < y || my >= y + PICK_H) {
            picker = Picker.NONE;
            return true;
        }
        int row = (int) ((my - y - 15) / ROW_H);
        List<String> opts = options();
        if (my >= y + 15 && row >= 0 && row < ROWS && row + scroll < opts.size()) {
            cursor = row + scroll;
            togglePick(opts.get(cursor));
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (picker == Picker.NONE) return super.mouseScrolled(mx, my, dx, dy);
        moveCursor(dy > 0 ? -1 : 1, options().size());
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (picker == Picker.NONE) return super.keyPressed(key, scan, mods);
        List<String> opts = options();
        switch (key) {
            case KEY_ESC -> picker = Picker.NONE;
            case KEY_UP -> moveCursor(-1, opts.size());
            case KEY_DOWN -> moveCursor(1, opts.size());
            case KEY_ENTER, KEY_KP_ENTER -> { if (!opts.isEmpty()) togglePick(opts.get(Math.min(cursor, opts.size() - 1))); }
            default -> { return true; } // swallow inventory keys while the picker is open
        }
        return true;
    }

    // ---- rendering --------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        FilterSpec s = menu.spec();
        FilterLayout layout = menu.layout;
        for (int i = 0; i < modeButtons.length; i++) {
            int m = menu.mode(i);
            // exposer: TARGET cyan; card: INSERT orange
            modeButtons[i].on = m != 0;
            modeButtons[i].onColor = layout == FilterLayout.CARD && m == 1 ? ON_PUSH : ToggleButton.DEFAULT_ON;
            Component name = Component.translatable(layout.modeNames[m]);
            modeButtons[i].setMessage(layout.sides == 6 ? Component.literal(SIDE[i] + " ").append(name) : name);
        }
        for (int i = 0; i < FLAG.length; i++) {
            flagButtons[i].on = (s.flags() & FLAG[i]) != 0;
            flagButtons[i].setMessage(Component.translatable("gui.omnilogistics.flag." + FLAG_KEY[i]));
        }
        tagButton.on = picker == Picker.TAG;
        tagButton.setMessage(Component.translatable("gui.omnilogistics.tags_n", s.tags().size()));
        compButton.on = picker == Picker.COMPONENT;
        compButton.setMessage(Component.translatable("gui.omnilogistics.components_n", s.components().size()));
        super.render(g, mouseX, mouseY, partialTick);
        if (picker != Picker.NONE) {   // above slot items (z 150) and their count text (z 200), like a tooltip
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            renderPicker(g, mouseX, mouseY);
            g.pose().popPose();
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (picker == Picker.NONE) super.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        int gx = leftPos + FilterMenu.gridX(menu.refs), gy = topPos + FilterMenu.GRID_Y;
        for (int i = 0; i < menu.refs; i++) slotFrame(g, gx + (i % 8) * 18, gy + (i / 8) * 18);
    }

    /** Inset reference slot, same look as the generated sheets; drawn in code so one panel fits every capacity. */
    private static void slotFrame(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF0A0D11);
        g.fill(x, y, x + 16, y + 16, 0xFF12161B);
        g.fill(x, y, x + 16, y + 1, 0xFF05070A);
        g.fill(x, y, x + 1, y + 16, 0xFF05070A);
        g.fill(x - 1, y + 16, x + 17, y + 17, 0xFF3A424C);
        g.fill(x + 16, y - 1, x + 17, y + 17, 0xFF3A424C);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        g.drawString(font, Component.translatable("gui.omnilogistics.filter"), 8, 26, MUTED, false);   // short: the grid starts at x=56
    }
}
