package com.mertokan.omnilogistics.core;

import net.minecraft.network.chat.Component;

import java.util.List;

/** Server-side summary lines shown by Jade (and reusable anywhere else). */
public interface InfoLines {
    List<Component> infoLines();
}
