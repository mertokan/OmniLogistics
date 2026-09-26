package com.mertokan.omnilogistics.router;

import com.mertokan.omnilogistics.core.DarkScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class RouterScreen extends DarkScreen<RouterMenu> {
    private static final int BAR_X = 96, BAR_Y = 68, BAR_W = 72, BAR_H = 8;
    private static final int TICK_X = 96, TICK_Y = 52;

    public RouterScreen(RouterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, "router", 176, 180);
        inventoryLabelY = 88;
    }

    @Override
    protected void init() {
        super.init();
        addIntervalBox(TICK_X + 3, TICK_Y + 2, 26, menu.pos, menu::interval);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int e = (int) Math.min(BAR_W, (long) BAR_W * menu.energy() / menu.capacity());
        g.fill(leftPos + BAR_X, topPos + BAR_Y, leftPos + BAR_X + e, topPos + BAR_Y + BAR_H, 0xFFFFD23F);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        g.text(font, Component.translatable("gui.omnilogistics.card_hint"), 8, 42, MUTED, false);
        g.text(font, Component.translatable("gui.omnilogistics.buffer"), 8, 52, MUTED, false);
        g.text(font, Component.translatable("gui.omnilogistics.upgrades"), 44, 52, MUTED, false);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        if (mx >= BAR_X && mx < BAR_X + BAR_W && my >= BAR_Y && my < BAR_Y + BAR_H)
            g.setTooltipForNextFrame(font, Component.translatable("gui.omnilogistics.router_energy", menu.energy(), menu.capacity()), mx, my);
        g.text(font, font.plainSubstrByWidth(Component.translatable("gui.omnilogistics.router_fluid", menu.fluidAmount()).getString(), 72), 96, 78, MUTED, false);
        g.text(font, Component.translatable("gui.omnilogistics.ticks_short"), TICK_X + 32, TICK_Y + 2, MUTED, false);
        g.text(font, Component.translatable("gui.omnilogistics.router_range", menu.range()), TICK_X + 40, TICK_Y + 2, MUTED, false);
    }
}
