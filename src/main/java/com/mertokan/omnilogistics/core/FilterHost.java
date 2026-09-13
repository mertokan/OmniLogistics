package com.mertokan.omnilogistics.core;

import com.mertokan.omnilogistics.api.FilterSpec;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Anything with a filter + N mode values: pipe, exposer, router card. Readable on both sides, writable on the server. */
public interface FilterHost {
    FilterLayout layout();
    /** length == layout().modeCount */
    byte[] modes();
    FilterSpec spec();
    /** How many reference slots this host offers: 1 for a block, the card item's capacity for a card. */
    default int filterSlots() { return 1; }
    void setFilter(int index, ItemStack stack);
    /** Server side; modes.length == layout().modeCount already checked. */
    void applyConfig(byte[] modes, int flags, List<String> tags, List<String> components);
}
