package com.mertokan.omnilogistics.core;

import com.mertokan.omnilogistics.OmniLogistics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** Upgrade items live in dedicated slots; the machine reads counts every time it needs them (cheap, always right). */
public final class Upgrades {
    private Upgrades() {}

    public static int count(IItemHandler slots, int from, int to, Item upgrade) {
        int n = 0;
        for (int i = from; i < to; i++) {
            ItemStack s = slots.getStackInSlot(i);
            if (s.is(upgrade)) n += s.getCount();
        }
        return n;
    }

    public static boolean has(IItemHandler slots, int from, int to, Item upgrade) {
        return count(slots, from, to, upgrade) > 0;
    }

    public static boolean isMachineUpgrade(ItemStack s) {
        return s.is(OmniLogistics.SPEED_UPGRADE.get()) || s.is(OmniLogistics.PARALLEL_UPGRADE.get())
            || s.is(OmniLogistics.GEM_MODULE.get()) || s.is(OmniLogistics.FUSION_MODULE.get());
    }

    public static boolean isRouterUpgrade(ItemStack s) {
        return s.is(OmniLogistics.SPEED_UPGRADE.get()) || s.is(OmniLogistics.RANGE_UPGRADE.get())
            || s.is(OmniLogistics.CHUNK_UPGRADE.get());
    }
}
