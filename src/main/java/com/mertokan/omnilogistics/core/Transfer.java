package com.mertokan.omnilogistics.core;

import com.mertokan.omnilogistics.api.FilterSpec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

/** The simulate -> extract -> insert dance, shared by pipe, router and extractor. */
public final class Transfer {
    private Transfer() {}

    /** Move up to {@code rate} matching items from any slot of src into buffer slot 0. */
    public static boolean pull(IItemHandler src, ItemStackHandler buffer, FilterSpec filter, int rate) {
        return pull(src, buffer, 0, filter, rate);
    }

    /** Same, into a chosen buffer slot (the distributor gives every lane its own). */
    public static boolean pull(IItemHandler src, ItemStackHandler buffer, int into, FilterSpec filter, int rate) {
        for (int slot = 0; slot < src.getSlots(); slot++) {
            ItemStack sim = src.extractItem(slot, rate, true);
            if (sim.isEmpty() || !filter.test(sim)) continue;
            int accepted = sim.getCount() - buffer.insertItem(into, sim, true).getCount();
            if (accepted <= 0) continue;
            buffer.insertItem(into, src.extractItem(slot, accepted, false), false);
            return true;
        }
        return false;
    }

    /** Move as much of src[slot] as dst accepts. Returns true if anything moved. */
    public static boolean pushSlot(IItemHandler src, int slot, IItemHandler dst) {
        ItemStack stack = src.getStackInSlot(slot);
        if (stack.isEmpty()) return false;
        int accepted = stack.getCount() - ItemHandlerHelper.insertItem(dst, stack.copy(), true).getCount();
        if (accepted <= 0) return false;
        ItemStack moved = src.extractItem(slot, accepted, false);
        ItemStack left = ItemHandlerHelper.insertItem(dst, moved, false);
        if (!left.isEmpty()) ItemHandlerHelper.insertItem(src, left, false); // dst lied on simulate; put it back
        return true;
    }
}
