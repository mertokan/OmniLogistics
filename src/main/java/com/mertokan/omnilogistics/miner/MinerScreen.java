package com.mertokan.omnilogistics.miner;

import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.core.DarkScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import static com.mertokan.omnilogistics.miner.MinerMenu.*;

/** Energy bar, card + speed upgrade slots (ghost icons when empty), the item on its way out of the void with a progress bar, 2x2 outputs, status line. */
public class MinerScreen extends DarkScreen<MinerMenu> {
    private static final int STATUS_X = 62, STATUS_Y = 58, ENERGY = 0xFF3FD3FF, VOID = 0xFFA060FF;
    private static final int TICK_X = 116, TICK_Y = 56;
    private ItemStack ghostCard = ItemStack.EMPTY, ghostUpgrade = ItemStack.EMPTY;

    public MinerScreen(MinerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, "miner", 176, HEIGHT);
        inventoryLabelY = INV_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        ghostCard = new ItemStack(OmniLogistics.CARD.get());
        ghostUpgrade = new ItemStack(OmniLogistics.SPEED_UPGRADE.get());
        addIntervalBox(TICK_X + 3, TICK_Y + 2, 26, menu.pos, menu::time);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int e = (int) Math.min(BAR_H, (long) BAR_H * menu.energy() / menu.capacity());
        g.fill(leftPos + BAR_X, topPos + BAR_Y + BAR_H - e, leftPos + BAR_X + BAR_W, topPos + BAR_Y + BAR_H, ENERGY);
        int p = menu.status() == VoidMinerBlockEntity.MINING || menu.status() == VoidMinerBlockEntity.FULL ? PROG_W * Math.min(menu.progress(), menu.time()) / menu.time() : 0;
        if (p > 0) g.fill(leftPos + PROG_X, topPos + PROG_Y, leftPos + PROG_X + p, topPos + PROG_Y + PROG_H, VOID);
        VoidMinerBlockEntity m = menu.miner();
        if (m != null && !m.pending.isEmpty()) g.item(m.pending, leftPos + ITEM_X, topPos + ITEM_Y);
        if (!menu.getSlot(CARD).hasItem()) ghost(g, ghostCard, leftPos + CARD_X, topPos + CARD_Y);
        if (!menu.getSlot(UPGRADE).hasItem()) ghost(g, ghostUpgrade, leftPos + UPG_X, topPos + UPG_Y);
    }

    private static void ghost(GuiGraphicsExtractor g, ItemStack s, int x, int y) {
        g.fakeItem(s, x, y);
        g.nextStratum();
        g.fill(x, y, x + 16, y + 16, 0xA0101317);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        int st = menu.status();
        g.text(font, font.plainSubstrByWidth(Component.translatable(VoidMinerBlockEntity.statusKey(st)).getString(), TICK_X - STATUS_X - 4), STATUS_X, STATUS_Y, st == VoidMinerBlockEntity.MINING ? ACCENT : MUTED, false);
        g.text(font, Component.translatable("gui.omnilogistics.ticks_short"), TICK_X + 32, TICK_Y + 2, MUTED, false);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        if (mx >= BAR_X && mx < BAR_X + BAR_W && my >= BAR_Y && my < BAR_Y + BAR_H)
            g.setTooltipForNextFrame(font, Component.translatable("gui.omnilogistics.energy", menu.energy(), menu.capacity(), menu.cost()), mx, my);
        else if (in(mx, my, CARD_X, CARD_Y) && !menu.getSlot(CARD).hasItem())
            g.setTooltipForNextFrame(font, font.split(Component.translatable("tooltip.omnilogistics.miner_card"), 200), mx, my);
        else if (in(mx, my, UPG_X, UPG_Y) && !menu.getSlot(UPGRADE).hasItem())
            g.setTooltipForNextFrame(font, font.split(Component.translatable("tooltip.omnilogistics.miner_upgrade"), 200), mx, my);
        else if (mx >= PROG_X && mx < PROG_X + PROG_W && my >= ITEM_Y - 2 && my < PROG_Y + PROG_H)
            g.setTooltipForNextFrame(font, Component.translatable("gui.omnilogistics.miner.cycle", menu.progress(), menu.time()), mx, my);
    }

    private static boolean in(int mx, int my, int x, int y) {
        return mx >= x && mx < x + 16 && my >= y && my < y + 16;
    }
}
