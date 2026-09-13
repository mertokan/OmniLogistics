package com.mertokan.omnilogistics.core;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.IntSupplier;

/**
 * The tick field: digits only, sends the machine its new interval on Enter or when it loses focus, and mirrors what the
 * server actually runs at whenever the player is not typing in it.
 */
public class IntervalBox extends EditBox {
    private final BlockPos pos;
    private final IntSupplier server;
    private long muteUntil;

    public IntervalBox(Font font, int x, int y, int w, BlockPos pos, IntSupplier server) {
        super(font, x, y, w, 12, Component.translatable("gui.omnilogistics.interval"));
        this.pos = pos;
        this.server = server;
        setBordered(false);          // the panel texture draws the recess
        setMaxLength(5);   // the machine default can be wider than the field lets you type; commit() clamps
        setFilter(s -> s.chars().allMatch(Character::isDigit));
        setTooltip(Tooltip.create(Component.translatable("tooltip.omnilogistics.interval", IntervalHost.MIN, IntervalHost.MAX)));
        setValue(Integer.toString(server.getAsInt()));
    }

    /** Called every frame by {@link DarkScreen}: the field follows the machine until the player takes it over. */
    public void refresh() {
        if (isFocused() || Util.getMillis() < muteUntil) return;   // do not flash the old value back while the packet flies
        String v = Integer.toString(server.getAsInt());
        if (!v.equals(getValue())) setValue(v);
    }

    @Override
    public void setFocused(boolean focused) {
        if (isFocused() && !focused) commit();
        super.setFocused(focused);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (isFocused() && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)) {
            commit();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** An empty or out-of-range field snaps back to something legal instead of dropping the edit. Nothing is sent unless the
     *  player really typed something, so opening a GUI never rewrites a machine default the field cannot represent. */
    private void commit() {
        String shown = Integer.toString(server.getAsInt());
        if (getValue().isEmpty() || getValue().equals(shown)) { setValue(shown); return; }
        int v = IntervalHost.clamp(Integer.parseInt(getValue()));
        setValue(Integer.toString(v));
        muteUntil = Util.getMillis() + 500;
        PacketDistributor.sendToServer(new IntervalPayload(pos, v));
    }
}
