package com.mertokan.omnilogistics.core;

import com.mertokan.omnilogistics.api.ComponentPredicateEngine;
import com.mertokan.omnilogistics.api.FilterSpec;
import com.mertokan.omnilogistics.api.NbtRule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Filter GUI: ghost slots, side/mode buttons, 3x3 flag grid, and three overlays opened from the bottom row: item tags
 * and component keys (multi-select lists of what the reference item actually has) and the NBT rules editor.
 * The NBT editor lists the rules; "+ Pick" opens a browser of every value inside the reference item, and clicking one
 * turns it into a rule that can then be changed to >=, !=, contains and so on.
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

    private enum Picker { NONE, TAG, COMPONENT, NBT, NBT_PICK }
    private static final int PICK_X = 28, PICK_Y = 36, PICK_W = 200, PICK_H = 132, ROW_H = 11, ROWS = 10;
    /** The NBT overlays are wider: a rule row holds a path, an operator and a value side by side. */
    private static final int NBT_X = 6, NBT_W = 244, NBT_H = 150, RULE_H = 12, RULE_ROWS = 9, PICK_ROWS = 11;
    private static final int COL_PATH = 16, COL_OP = 122, COL_VALUE = 166, COL_DEL = 231;

    private final int shift;
    private final ToggleButton[] modeButtons;
    private final ToggleButton[] flagButtons = new ToggleButton[FLAG.length];
    private ToggleButton tagButton, compButton, nbtButton;
    private Picker picker = Picker.NONE;
    private int cursor, scroll;
    private ComponentPredicateEngine.@Nullable ComponentInfo hoverValue;
    private @Nullable Component hoverTip;
    private EditBox valueBox;
    private int editing = -1;
    private List<NbtRule.Leaf> leaves = List.of();
    private ItemStack leavesOf = ItemStack.EMPTY;

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
        // the bottom row lines up with the 3x3 grid above it
        tagButton = addRenderableWidget(new ToggleButton(leftPos + 8, topPos + 130 + shift, 78, 18, () -> open(Picker.TAG)));
        tagButton.setTooltip(Tooltip.create(Component.translatable("tooltip.omnilogistics.pick_tag")));
        compButton = addRenderableWidget(new ToggleButton(leftPos + 90, topPos + 130 + shift, 78, 18, () -> open(Picker.COMPONENT)));
        compButton.setTooltip(Tooltip.create(Component.translatable("tooltip.omnilogistics.pick_components")));
        nbtButton = addRenderableWidget(new ToggleButton(leftPos + 172, topPos + 130 + shift, 78, 18, () -> open(Picker.NBT)));
        nbtButton.setTooltip(Tooltip.create(Component.translatable("tooltip.omnilogistics.pick_nbt")));
        valueBox = new EditBox(font, 0, 0, COL_DEL - COL_VALUE - 4, 10, Component.empty());
        valueBox.setBordered(false);
        valueBox.setMaxLength(NbtRule.MAX_VALUE);
        valueBox.setTextColor(0xFFFFFF);
    }

    // ---- sending ----------------------------------------------------------------

    private void send(byte[] modes, int flags, List<String> tags, List<String> comps, List<NbtRule> rules) {
        PacketDistributor.sendToServer(FilterConfigPayload.of(menu, modes, flags, tags, comps, rules));
    }

    private void cycle(int i) {
        byte[] m = menu.modes();
        m[i] = (byte) ((m[i] + 1) % menu.layout.modeNames.length);
        FilterSpec s = menu.spec();
        send(m, s.flags(), s.tags(), s.components(), s.rules());
    }

    private void toggle(int flag) {
        FilterSpec s = menu.spec();
        send(menu.modes(), s.flags() ^ flag, s.tags(), s.components(), s.rules());
    }

    /** Add or remove the entry; the matching flag follows whether the list is empty. */
    private void togglePick(String key) {
        FilterSpec s = menu.spec();
        boolean tags = picker == Picker.TAG;
        List<String> list = new ArrayList<>(tags ? s.tags() : s.components());
        if (!list.remove(key)) list.add(key);
        int flag = tags ? ComponentPredicateEngine.MATCH_TAG : ComponentPredicateEngine.MATCH_SELECTED;
        int flags = list.isEmpty() ? s.flags() & ~flag : s.flags() | flag;
        send(menu.modes(), flags, tags ? list : s.tags(), tags ? s.components() : list, s.rules());
    }

    /** New rule list; MATCH_NBT simply follows whether there are any rules, the way the tag / component flags do. */
    private void sendRules(List<NbtRule> rules, boolean any) {
        FilterSpec s = menu.spec();
        int flags = s.flags() & ~(ComponentPredicateEngine.MATCH_NBT | ComponentPredicateEngine.NBT_ANY);
        if (!rules.isEmpty()) flags |= ComponentPredicateEngine.MATCH_NBT;
        if (any) flags |= ComponentPredicateEngine.NBT_ANY;
        send(menu.modes(), flags, s.tags(), s.components(), rules);
    }

    private boolean nbtAny() {
        return (menu.spec().flags() & ComponentPredicateEngine.NBT_ANY) != 0;
    }

    private void editRule(int i, java.util.function.UnaryOperator<NbtRule> change) {
        List<NbtRule> rules = new ArrayList<>(menu.spec().rules());
        if (i < 0 || i >= rules.size()) return;
        if (change == null) rules.remove(i); else rules.set(i, change.apply(rules.get(i)));
        sendRules(rules, nbtAny());
    }

    // ---- overlays ---------------------------------------------------------------

    private void open(Picker p) {
        commitValue();
        picker = picker == p || (p == Picker.NBT && picker == Picker.NBT_PICK) ? Picker.NONE : p;
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

    /** Every value inside the first reference item, recomputed only when that item changes. */
    private List<NbtRule.Leaf> leaves() {
        ItemStack ref = menu.spec().ref();
        if (!ItemStack.matches(ref, leavesOf)) {
            leavesOf = ref.copy();
            leaves = ref.isEmpty() || minecraft == null || minecraft.level == null
                ? List.of() : NbtRule.leaves(ref, minecraft.level.registryAccess());
        }
        return leaves;
    }

    private int count() {
        return picker == Picker.NBT_PICK ? leaves().size() : options().size();
    }

    private int visibleRows() {
        return picker == Picker.NBT_PICK ? PICK_ROWS : ROWS;
    }

    private boolean selected(String opt) {
        FilterSpec s = menu.spec();
        return (picker == Picker.TAG ? s.tags() : s.components()).contains(opt);
    }

    private void moveCursor(int delta, int count) {
        if (count == 0) return;
        int rows = visibleRows();
        cursor = Math.floorMod(cursor + delta, count);
        if (cursor < scroll) scroll = cursor;
        if (cursor >= scroll + rows) scroll = cursor - rows + 1;
    }

    private void choose(int index) {
        if (picker == Picker.NBT_PICK) {
            List<NbtRule.Leaf> all = leaves();
            if (index < 0 || index >= all.size()) return;
            NbtRule.Leaf leaf = all.get(index);
            List<NbtRule> rules = new ArrayList<>(menu.spec().rules());
            if (rules.size() < NbtRule.MAX_RULES) rules.add(new NbtRule(leaf.path(), NbtRule.Op.EQ, leaf.value(), true));
            sendRules(rules, nbtAny());
            picker = Picker.NBT;
            cursor = scroll = 0;
        } else {
            List<String> opts = options();
            if (index >= 0 && index < opts.size()) togglePick(opts.get(index));
        }
    }

    private int ox() { return leftPos + (isNbt() ? NBT_X : PICK_X); }
    private int oy() { return topPos + PICK_Y + shift; }
    private int ow() { return isNbt() ? NBT_W : PICK_W; }
    private int oh() { return isNbt() ? NBT_H : PICK_H; }
    private boolean isNbt() { return picker == Picker.NBT || picker == Picker.NBT_PICK; }

    private void frame(GuiGraphics g, Component title) {
        frame(g, title, isNbt() && picker == Picker.NBT ? 170 : 24);
    }

    /** Overlay box with a title bar; {@code room} is what the header chips on the right take. */
    private void frame(GuiGraphics g, Component title, int room) {
        int x = ox(), y = oy(), w = ow(), h = oh();
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xF00C0E11);
        g.fill(x, y, x + w, y + h, 0xFF1A1E25);
        g.renderOutline(x, y, w, h, 0xFF3FD3FF);
        g.fill(x, y, x + w, y + 13, 0xFF232830);
        g.drawString(font, font.plainSubstrByWidth(title.getString(), w - room), x + 5, y + 3, TEXT, false);
    }

    private void scrollbar(GuiGraphics g, int x, int y, int rows, int rowH, int total) {
        int max = total - rows;
        if (max <= 0) return;
        int trackH = rows * rowH, thumb = Math.max(8, trackH * rows / total);
        int ty = y + (trackH - thumb) * scroll / max;
        g.fill(x, y, x + 3, y + trackH, 0xFF0C0E11);
        g.fill(x, ty, x + 3, ty + thumb, 0xFF3FD3FF);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** A flat text button inside an overlay header. */
    private void chip(GuiGraphics g, int x, int y, int w, Component label, boolean on, int mx, int my) {
        boolean hover = in(mx, my, x, y, w, 11);
        g.fill(x, y, x + w, y + 11, on ? 0xFF1F7A8C : hover ? 0xFF2F3640 : 0xFF2A2F37);
        g.renderOutline(x, y, w, 11, 0xFF3A424C);
        g.drawCenteredString(font, label, x + w / 2, y + 2, TEXT);
    }

    // ---- tag / component picker ---------------------------------------------------

    private void renderPicker(GuiGraphics g, int mouseX, int mouseY) {
        int x = ox(), y = oy();
        frame(g, Component.translatable(picker == Picker.TAG ? "gui.omnilogistics.pick_tag" : "gui.omnilogistics.pick_components"));
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
            boolean hover = in(mouseX, mouseY, x, ry, PICK_W, ROW_H);
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
        scrollbar(g, x + PICK_W - 5, y + 15, ROWS, ROW_H, opts.size());
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

    // ---- NBT rules ------------------------------------------------------------------

    private int rowY(int i) { return oy() + 16 + i * RULE_H; }
    private int headerChip(int slot) { return ox() + NBT_W - 16 - slot * 52; }   // 0 = close, 1 = +Pick, 2 = ALL/ANY

    private void renderRules(GuiGraphics g, int mx, int my) {
        int x = ox(), y = oy();
        frame(g, Component.translatable("gui.omnilogistics.nbt.title"));
        chip(g, headerChip(2) + 2, y + 1, 48, Component.translatable(nbtAny() ? "gui.omnilogistics.nbt.any" : "gui.omnilogistics.nbt.all"), false, mx, my);
        chip(g, headerChip(1) + 2, y + 1, 48, Component.translatable("gui.omnilogistics.nbt.add"), false, mx, my);
        chip(g, headerChip(0) + 2, y + 1, 11, Component.literal("×"), false, mx, my);
        if (in(mx, my, headerChip(2) + 2, y + 1, 48, 11)) hoverTip = Component.translatable("tooltip.omnilogistics.nbt.all_any");

        List<NbtRule> rules = menu.spec().rules();
        if (rules.isEmpty()) {
            int ty = y + 22;
            for (var line : font.split(Component.translatable("gui.omnilogistics.nbt.empty"), NBT_W - 12)) {
                g.drawString(font, line, x + 6, ty, MUTED, false);
                ty += 10;
            }
        }
        scroll = Math.max(0, Math.min(scroll, rules.size() - RULE_ROWS));
        for (int i = 0; i < RULE_ROWS && i + scroll < rules.size(); i++) {
            int idx = i + scroll;
            NbtRule r = rules.get(idx);
            int ry = rowY(i);
            if (in(mx, my, x, ry, NBT_W, RULE_H)) g.fill(x + 2, ry, x + NBT_W - 2, ry + RULE_H, 0xFF232830);
            // enabled box
            g.fill(x + 4, ry + 1, x + 13, ry + 10, 0xFF0C0E11);
            g.renderOutline(x + 4, ry + 1, 9, 9, 0xFF3A424C);
            if (r.enabled()) g.fill(x + 6, ry + 3, x + 11, ry + 8, 0xFF3FD3FF);
            int color = r.enabled() ? TEXT : MUTED;
            // path
            String path = NbtRule.pretty(r.path());
            int pw = COL_OP - COL_PATH - 4;
            String shown = font.width(path) <= pw ? path : "…" + tail(path, pw - font.width("…"));
            g.drawString(font, shown, x + COL_PATH, ry + 2, r.hasListStep() ? 0xFFB7E3F0 : color, false);
            if (in(mx, my, x + COL_PATH, ry, pw, RULE_H))
                hoverTip = Component.literal(String.join(" / ", r.path())).append(r.hasListStep()
                    ? Component.literal("\n").append(Component.translatable("tooltip.omnilogistics.nbt.any_index")) : Component.empty());
            // operator
            boolean opHover = in(mx, my, x + COL_OP, ry, COL_VALUE - COL_OP - 2, RULE_H);
            g.fill(x + COL_OP, ry + 1, x + COL_VALUE - 2, ry + RULE_H - 1, opHover ? 0xFF2F3640 : 0xFF12161B);
            g.drawCenteredString(font, Component.translatable("gui.omnilogistics.nbt.op." + r.op().key()),
                x + (COL_OP + COL_VALUE - 2) / 2, ry + 2, r.enabled() ? 0xFFFFC04A : MUTED);
            if (opHover) hoverTip = Component.translatable("tooltip.omnilogistics.nbt.op");
            // value
            if (editing != idx) {
                boolean vHover = in(mx, my, x + COL_VALUE, ry, COL_DEL - COL_VALUE - 3, RULE_H);
                g.fill(x + COL_VALUE, ry + 1, x + COL_DEL - 3, ry + RULE_H - 1, vHover && r.op().takesValue() ? 0xFF2F3640 : 0xFF12161B);
                if (r.op().takesValue())
                    g.drawString(font, font.plainSubstrByWidth(r.value(), COL_DEL - COL_VALUE - 7), x + COL_VALUE + 2, ry + 2, color, false);
                if (vHover && r.op().takesValue()) hoverTip = Component.literal(r.value());
            }
            // delete
            boolean delHover = in(mx, my, x + COL_DEL, ry, 10, RULE_H);
            g.drawString(font, "×", x + COL_DEL + 2, ry + 2, delHover ? 0xFFFF6B6B : MUTED, false);
        }
        scrollbar(g, x + NBT_W - 4, y + 16, RULE_ROWS, RULE_H, rules.size());
        if (editing >= scroll && editing < scroll + RULE_ROWS) {
            int ry = rowY(editing - scroll);
            g.fill(x + COL_VALUE, ry + 1, x + COL_DEL - 3, ry + RULE_H - 1, 0xFF0C0E11);
            g.renderOutline(x + COL_VALUE, ry + 1, COL_DEL - 3 - COL_VALUE, RULE_H - 2, 0xFF3FD3FF);
            valueBox.setX(x + COL_VALUE + 2);
            valueBox.setY(ry + 2);
            valueBox.render(g, mx, my, 0);
        }
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("gui.omnilogistics.nbt.hint").getString(), NBT_W - 10),
            x + 5, y + NBT_H - 10, MUTED, false);
    }

    private String tail(String s, int width) {
        int i = s.length();
        while (i > 0 && font.width(s.substring(i - 1)) <= width) i--;
        return s.substring(i);
    }

    private void renderNbtPick(GuiGraphics g, int mx, int my) {
        int x = ox(), y = oy();
        frame(g, Component.translatable("gui.omnilogistics.nbt.pick_title"));
        chip(g, headerChip(0) + 2, y + 1, 11, Component.literal("‹"), false, mx, my);
        List<NbtRule.Leaf> all = leaves();
        if (all.isEmpty()) {
            g.drawString(font, Component.translatable("gui.omnilogistics.pick_empty"), x + 5, y + 20, MUTED, false);
            return;
        }
        cursor = Math.min(cursor, all.size() - 1);
        scroll = Math.max(0, Math.min(scroll, all.size() - PICK_ROWS));
        for (int i = 0; i < PICK_ROWS && i + scroll < all.size(); i++) {
            int idx = i + scroll;
            NbtRule.Leaf leaf = all.get(idx);
            int ry = y + 15 + i * ROW_H;
            boolean hover = in(mx, my, x, ry, NBT_W, ROW_H);
            if (hover) g.fill(x + 2, ry, x + NBT_W - 7, ry + ROW_H, 0xFF2A2F37);
            if (idx == cursor) g.renderOutline(x + 2, ry, NBT_W - 9, ROW_H, 0xFF3FD3FF);
            // the end of a path is the part that says what it is, so a long one loses its beginning
            String path = NbtRule.pretty(leaf.path());
            int room = 150;
            if (font.width(path) > room) path = "…" + tail(path, room - font.width("…"));
            int pw = font.width(path);
            if (leaf.patched()) g.drawString(font, "*", x + 4, ry + 2, 0xFF3FD3FF, false);
            g.drawString(font, path, x + 10, ry + 2, leaf.patched() ? TEXT : MUTED, false);
            pw += 5;
            g.drawString(font, "=", x + 8 + pw, ry + 2, MUTED, false);
            int vx = x + 16 + pw, vw = x + NBT_W - 10 - vx;
            if (vw > 12) g.drawString(font, font.plainSubstrByWidth(leaf.value(), vw), vx, ry + 2, 0xFFFFC04A, false);
            if (hover) hoverTip = Component.literal(String.join(" / ", leaf.path())).append("\n").append(leaf.value());
        }
        scrollbar(g, x + NBT_W - 5, y + 15, PICK_ROWS, ROW_H, all.size());
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("gui.omnilogistics.nbt.pick_hint").getString(), NBT_W - 10),
            x + 5, y + NBT_H - 10, MUTED, false);
    }

    private void startEdit(int index) {
        commitValue();
        NbtRule r = menu.spec().rules().get(index);
        if (!r.op().takesValue()) return;
        editing = index;
        valueBox.setValue(r.value());
        valueBox.setFocused(true);
        valueBox.moveCursorToEnd(false);
    }

    private void commitValue() {
        if (editing < 0) return;
        int i = editing;
        editing = -1;
        valueBox.setFocused(false);
        String v = valueBox.getValue();
        List<NbtRule> rules = menu.spec().rules();
        if (i < rules.size() && !rules.get(i).value().equals(v)) editRule(i, r -> r.with(v));
    }

    private boolean clickRules(double mx, double my, int button) {
        int x = ox(), y = oy();
        if (in(mx, my, headerChip(0) + 2, y + 1, 11, 11)) { open(Picker.NBT); return true; }
        if (in(mx, my, headerChip(1) + 2, y + 1, 48, 11)) { commitValue(); picker = Picker.NBT_PICK; cursor = scroll = 0; return true; }
        if (in(mx, my, headerChip(2) + 2, y + 1, 48, 11)) { commitValue(); sendRules(menu.spec().rules(), !nbtAny()); return true; }
        List<NbtRule> rules = menu.spec().rules();
        int row = (int) ((my - y - 16) / RULE_H);
        if (my < y + 16 || row < 0 || row >= RULE_ROWS || row + scroll >= rules.size()) { commitValue(); return true; }
        int idx = row + scroll;
        double cx = mx - x;
        if (cx >= COL_VALUE && cx < COL_DEL - 3) { startEdit(idx); return true; }
        commitValue();
        if (cx >= 2 && cx < COL_PATH - 1) editRule(idx, NbtRule::toggled);
        else if (cx >= COL_PATH && cx < COL_OP - 2) { if (rules.get(idx).hasListStep()) editRule(idx, NbtRule::anyIndex); }
        else if (cx >= COL_OP && cx < COL_VALUE) editRule(idx, r -> r.with(r.op().next(button == 1 ? -1 : 1)));
        else if (cx >= COL_DEL) editRule(idx, null);
        return true;
    }

    // ---- input ----------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (picker == Picker.NONE) return super.mouseClicked(mx, my, button);
        int x = ox(), y = oy();
        if (!in(mx, my, x, y, ow(), oh())) {
            commitValue();
            picker = Picker.NONE;
            return true;
        }
        if (picker == Picker.NBT) return clickRules(mx, my, button);
        if (picker == Picker.NBT_PICK && in(mx, my, headerChip(0) + 2, y + 1, 11, 11)) {
            picker = Picker.NBT;
            cursor = scroll = 0;
            return true;
        }
        int row = (int) ((my - y - 15) / ROW_H);
        if (my >= y + 15 && row >= 0 && row < visibleRows() && row + scroll < count()) {
            cursor = row + scroll;
            choose(cursor);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (picker == Picker.NONE) return super.mouseScrolled(mx, my, dx, dy);
        if (picker == Picker.NBT) {
            commitValue();
            scroll = Math.max(0, Math.min(scroll + (dy > 0 ? -1 : 1), menu.spec().rules().size() - RULE_ROWS));
            return true;
        }
        moveCursor(dy > 0 ? -1 : 1, count());
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (picker == Picker.NONE) return super.keyPressed(key, scan, mods);
        if (editing >= 0) {
            if (key == KEY_ENTER || key == KEY_KP_ENTER) commitValue();
            else if (key == KEY_ESC) { editing = -1; valueBox.setFocused(false); }
            else valueBox.keyPressed(key, scan, mods);
            return true;   // nothing typed into a value may reach the inventory keys
        }
        switch (key) {
            case KEY_ESC -> picker = picker == Picker.NBT_PICK ? Picker.NBT : Picker.NONE;
            case KEY_UP -> { if (picker != Picker.NBT) moveCursor(-1, count()); }
            case KEY_DOWN -> { if (picker != Picker.NBT) moveCursor(1, count()); }
            case KEY_ENTER, KEY_KP_ENTER -> { if (picker != Picker.NBT && count() > 0) choose(Math.min(cursor, count() - 1)); }
            default -> { return true; } // swallow inventory keys while an overlay is open
        }
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (editing >= 0) return valueBox.charTyped(c, mods);
        return picker == Picker.NONE ? super.charTyped(c, mods) : true;
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
        nbtButton.on = isNbt() || !s.rules().isEmpty();
        nbtButton.setMessage(Component.translatable("gui.omnilogistics.nbt_n", s.rules().size()));
        // with an overlay open nothing underneath is hovered, so no button or slot tooltip bleeds through it
        super.render(g, picker == Picker.NONE ? mouseX : -1000, picker == Picker.NONE ? mouseY : -1000, partialTick);
        if (picker != Picker.NONE) {   // above slot items (z 150) and their count text (z 200), like a tooltip
            hoverTip = null;
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            switch (picker) {
                case NBT -> renderRules(g, mouseX, mouseY);
                case NBT_PICK -> renderNbtPick(g, mouseX, mouseY);
                default -> renderPicker(g, mouseX, mouseY);
            }
            if (hoverTip != null) g.renderTooltip(font, font.split(hoverTip, 220), mouseX, mouseY);
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

    @Override
    public void removed() {
        commitValue();
        super.removed();
    }
}
