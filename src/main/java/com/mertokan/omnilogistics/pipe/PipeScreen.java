package com.mertokan.omnilogistics.pipe;

import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.core.DarkScreen;
import com.mertokan.omnilogistics.core.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * One row per side: the neighbour's icon and name (so "the chest on the west" is what you configure, not a letter),
 * a NORM / EXTRACT / INSERT / OFF button (read from the neighbour: EXTRACT = the pipe takes out of it, INSERT = the pipe only puts into it); then the filter card slot (item pipes), what is inside the conduit right now, and the redstone mode.
 */
public class PipeScreen extends DarkScreen<PipeMenu> {
    public static final int WIDTH = 176, HEIGHT = 246;
    private static final Direction[] ORDER = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN};
    private static final String[] RS_KEY = {"always", "on_signal", "without_signal"};
    private static final int ROW_Y = 20, ROW_H = 18, ICON_X = 8, NAME_X = 28, NAME_W = 68, BTN_X = 100, BTN_W = 68;
    private static final int BUF_X = 28, BUF_W = 64, RS_X = 96, RS_W = 72;
    private static final int ON_EXTRACT = 0xFF1F8C4A, ON_INSERT = 0xFF9C5A10, ON_RS = 0xFF8C1F1F, ROW_LINE = 0x22FFFFFF;
    private static final int RS = 6;

    private final ToggleButton[] side = new ToggleButton[6];
    private ToggleButton rs;
    private ItemStack ghost = ItemStack.EMPTY;

    public PipeScreen(PipeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, "pipe", WIDTH, HEIGHT);
        inventoryLabelY = PipeMenu.INV_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < 6; i++) {
            int idx = ORDER[i].ordinal();
            side[i] = addRenderableWidget(new ToggleButton(leftPos + BTN_X, topPos + ROW_Y + i * ROW_H - 1, BTN_W, 16, () -> cycle(idx)));
            side[i].setTooltip(Tooltip.create(Component.translatable("tooltip.omnilogistics.mode.pipe")));
        }
        rs = addRenderableWidget(new ToggleButton(leftPos + RS_X, topPos + PipeMenu.SLOT_Y - 1, RS_W, 16, () -> cycle(RS)));
        rs.setTooltip(Tooltip.create(Component.translatable("tooltip.omnilogistics.redstone")));
        ghost = new ItemStack(OmniLogistics.CARD.get());
    }

    private void cycle(int i) {
        PacketDistributor.sendToServer(new PipeConfigPayload(menu.pos, i));
    }

    private BlockState neighbour(Direction d) {
        Level level = Minecraft.getInstance().level;
        return level == null ? null : level.getBlockState(menu.pos.relative(d));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        byte[] c = menu.config();
        for (int i = 0; i < 6; i++) {
            int m = c[ORDER[i].ordinal()];
            side[i].on = m != ConduitBlockEntity.Mode.NONE.ordinal();
            side[i].onColor = m == ConduitBlockEntity.Mode.PULL.ordinal() ? ON_EXTRACT : m == ConduitBlockEntity.Mode.PUSH.ordinal() ? ON_INSERT : ToggleButton.DEFAULT_ON;
            side[i].setMessage(ConduitBlockEntity.modeName(ConduitBlockEntity.Mode.values()[m]));
        }
        rs.on = c[RS] != 0;
        rs.onColor = ON_RS;
        rs.setMessage(Component.translatable("gui.omnilogistics.redstone." + RS_KEY[c[RS]]));
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        for (int i = 0; i < 6; i++) {
            int y = topPos + ROW_Y + i * ROW_H;
            if (i < 5) g.fill(leftPos + 8, y + ROW_H - 2, leftPos + 168, y + ROW_H - 1, ROW_LINE);
            BlockState n = neighbour(ORDER[i]);
            if (n == null || n.isAir()) continue;
            ItemStack icon = new ItemStack(n.getBlock());
            if (!icon.isEmpty()) g.renderItem(icon, leftPos + ICON_X, y);
        }
        int x = leftPos + BUF_X, y = topPos + PipeMenu.SLOT_Y;
        if (menu.hasCard) {
            slotFrame(g, leftPos + PipeMenu.SLOT_X, y);
            if (!menu.getSlot(PipeMenu.CARD_SLOT).hasItem()) {   // ghost card: what goes here
                g.renderFakeItem(ghost, leftPos + PipeMenu.SLOT_X, y);
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.fill(leftPos + PipeMenu.SLOT_X, y, leftPos + PipeMenu.SLOT_X + 16, y + 16, 0xA0101317);
                g.pose().popPose();
            }
        } else {
            x = leftPos + 8;
        }
        if (menu.conduit() instanceof PipeBlockEntity p && !p.bufferStack().isEmpty()) {   // the item in transit, with its count
            g.renderItem(p.bufferStack(), x, y);
            g.renderItemDecorations(font, p.bufferStack(), x, y);
        }
    }

    private static void slotFrame(GuiGraphics g, int x, int y) {   // inset slot, same look as the generated sheets
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF0E1115);
        g.fill(x, y, x + 16, y + 16, 0xFF0A0C0F);
        g.fill(x, y, x + 16, y + 1, 0xFF050709);
        g.fill(x, y, x + 1, y + 16, 0xFF050709);
        g.fill(x - 1, y + 16, x + 17, y + 17, 0xFF3F4650);
        g.fill(x + 16, y - 1, x + 17, y + 17, 0xFF3F4650);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        for (int i = 0; i < 6; i++) {
            int y = ROW_Y + i * ROW_H;
            g.drawString(font, Component.translatable("gui.omnilogistics.side." + ORDER[i].getName()), NAME_X, y, TEXT, false);
            BlockState n = neighbour(ORDER[i]);
            Component name = n == null || n.isAir() ? Component.translatable("gui.omnilogistics.side_empty") : n.getBlock().getName();
            g.drawString(font, font.plainSubstrByWidth(name.getString(), NAME_W), NAME_X, y + 9, MUTED, false);
        }
        ConduitBlockEntity c = menu.conduit();
        int bx = menu.hasCard ? BUF_X : 8;
        String info = c instanceof FluidPipeBlockEntity f ? compact(f.fluid().getAmount()) + " mB"
            : c instanceof EnergyCableBlockEntity e ? compact(e.stored()) + " FE" : null;
        if (info != null) g.drawString(font, info, bx, PipeMenu.SLOT_Y + 4, MUTED, false);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        boolean row = my >= PipeMenu.SLOT_Y && my < PipeMenu.SLOT_Y + 16;
        if (row && menu.hasCard && mx >= PipeMenu.SLOT_X && mx < PipeMenu.SLOT_X + 16 && !menu.getSlot(PipeMenu.CARD_SLOT).hasItem())
            g.renderTooltip(font, font.split(Component.translatable("tooltip.omnilogistics.filter_card"), 200), mx, my);
        else if (row && c != null && mx >= bx && mx < bx + BUF_W)
            g.renderTooltip(font, c.bufferInfo(), mx, my);
    }

    private static String compact(int n) {
        return n >= 1_000_000 ? String.format(Locale.ROOT, "%.1fM", n / 1e6) : n >= 1000 ? String.format(Locale.ROOT, "%.1fk", n / 1e3) : Integer.toString(n);
    }
}
