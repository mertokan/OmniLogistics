package com.mertokan.omnilogistics.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Flat dark-theme toggle with rounded corners, top highlight and bottom shadow. Caller sets {@link #on} and the message each frame. */
public class ToggleButton extends AbstractButton {
    public static final int DEFAULT_ON = 0xFF1F7A8C;
    private final Runnable action;
    public boolean on;
    public int onColor = DEFAULT_ON;

    public ToggleButton(int x, int y, int w, int h, Runnable action) {
        super(x, y, w, h, Component.empty());
        this.action = action;
    }

    @Override
    public void onPress(net.minecraft.client.input.InputWithModifiers input) {
        action.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        boolean hover = isHoveredOrFocused();
        int fill = on ? (hover ? lighten(onColor, 30) : onColor) : (hover ? 0xFF3A414B : 0xFF2A2F37);
        int x = getX(), y = getY(), r = x + width, b = y + height;
        g.fill(x + 1, y, r - 1, b, fill);           // body, 1px rounded corners
        g.fill(x, y + 1, r, b - 1, fill);
        g.fill(x + 1, y, r - 1, y + 1, lighten(fill, 22));  // top highlight
        g.fill(x + 1, b - 1, r - 1, b, darken(fill, 22));   // bottom shadow
        if (on) g.fill(x + 1, b, r - 1, b + 1, lighten(onColor, 60)); // accent underline
        var font = Minecraft.getInstance().font;
        String label = font.plainSubstrByWidth(getMessage().getString(), width - 4);
        g.centeredText(font, label, x + width / 2, y + (height - 8) / 2, on ? 0xFFFFFFFF : 0xFFE6EDF3);
    }

    private static int lighten(int argb, int d) {
        int r = Math.min(255, ((argb >> 16) & 255) + d), g = Math.min(255, ((argb >> 8) & 255) + d), b = Math.min(255, (argb & 255) + d);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int darken(int argb, int d) {
        int r = Math.max(0, ((argb >> 16) & 255) - d), g = Math.max(0, ((argb >> 8) & 255) - d), b = Math.max(0, (argb & 255) - d);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
