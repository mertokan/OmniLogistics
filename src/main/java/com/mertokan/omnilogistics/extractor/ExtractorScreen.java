package com.mertokan.omnilogistics.extractor;

import com.mertokan.omnilogistics.core.DarkScreen;
import com.mertokan.omnilogistics.core.ToggleButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.mertokan.omnilogistics.extractor.ExtractorBlockEntity.*;

public class ExtractorScreen extends DarkScreen<ExtractorMenu> {
    private static final int BAR_X = 8, BAR_Y = 20, BAR_W = 8, BAR_H = 86;
    private static final int TICK_X = 62, TICK_Y = 66;
    private ToggleButton modeButton;

    public ExtractorScreen(ExtractorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, "extractor", 176, ExtractorMenu.INV_Y + 82);
        inventoryLabelY = ExtractorMenu.INV_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        modeButton = addRenderableWidget(new ToggleButton(leftPos + 98, topPos + 66, 52, 16,
            () -> net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new ExtractorModePayload(menu.pos))));
        modeButton.setTooltip(Tooltip.create(Component.translatable("tooltip.omnilogistics.extractor_mode")));
        addIntervalBox(TICK_X + 3, TICK_Y + 2, 22, menu.pos, menu::time);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        modeButton.on = menu.fuseMode();
        modeButton.onColor = 0xFF9C5A10;
        modeButton.setMessage(Component.translatable(menu.fuseMode() ? "gui.omnilogistics.mode_fuse" : "gui.omnilogistics.mode_extract"));
        modeButton.active = menu.fusionModule();
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int cap = menu.capacity();
        int e = (int) Math.min(BAR_H, (long) BAR_H * menu.energy() / cap);
        g.fill(leftPos + BAR_X, topPos + BAR_Y + BAR_H - e, leftPos + BAR_X + BAR_W, topPos + BAR_Y + BAR_H, 0xFF3FD3FF);
        for (int lane = 0; lane < LANES; lane++) {
            int[] p = ExtractorMenu.POS[lane];
            if (lane >= menu.lanes()) {
                g.fill(leftPos + p[0] - 1, topPos + p[1] - 1, leftPos + p[0] + 17, topPos + p[1] + 17, 0xB0101317);
                continue;
            }
            int h = 16 * Math.min(menu.progress(lane), menu.time()) / menu.time();   // the clock can be retyped mid-operation
            g.fill(leftPos + p[0] + 19, topPos + p[1] + 16 - h, leftPos + p[0] + 22, topPos + p[1] + 16, menu.fuseMode() ? 0xFFFF9F1C : 0xFF5CFF7A);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        g.text(font, Component.translatable("gui.omnilogistics.upgrades"), 62, 86, MUTED, false);
        g.text(font, Component.translatable("gui.omnilogistics.ticks_short"), TICK_X + 27, TICK_Y + 2, MUTED, false);
        g.text(font, Component.translatable(menu.fuseMode() ? "gui.omnilogistics.donor" : "gui.omnilogistics.books"), 56, 31, MUTED, false);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        if (mx >= BAR_X && mx < BAR_X + BAR_W && my >= BAR_Y && my < BAR_Y + BAR_H)
            g.setTooltipForNextFrame(font, Component.translatable("gui.omnilogistics.energy", menu.energy(), menu.capacity(), menu.cost()), mx, my);
        for (int lane = menu.lanes(); lane < LANES; lane++) {
            int[] p = ExtractorMenu.POS[lane];
            if (mx >= p[0] && mx < p[0] + 16 && my >= p[1] && my < p[1] + 16)
                g.setTooltipForNextFrame(font, Component.translatable("gui.omnilogistics.lane_locked"), mx, my);
        }
    }
}
