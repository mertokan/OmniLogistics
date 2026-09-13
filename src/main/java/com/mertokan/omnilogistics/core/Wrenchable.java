package com.mertokan.omnilogistics.core;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Block entities that react to a wrench click on a face (sneak + wrench = dismantle, handled by MachineBlock). */
public interface Wrenchable {
    /** @return message to show in the action bar */
    Component wrenchFace(Direction face, Player player);
}
