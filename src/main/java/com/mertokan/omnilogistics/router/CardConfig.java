package com.mertokan.omnilogistics.router;

import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.core.FilterLayout;
import com.mertokan.omnilogistics.api.FilterSpec;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Reads / writes a card's config components. Shared by the in-hand host and the in-router host. */
public final class CardConfig {
    private CardConfig() {}

    public static FilterLayout layout(ItemStack card) {
        return card.getItem() instanceof LogisticsCardItem c && c.kind.hasMode ? FilterLayout.CARD : FilterLayout.CARD_NOMODE;
    }

    public static byte[] modes(ItemStack card) {
        return layout(card) == FilterLayout.CARD ? new byte[]{(byte) (int) card.getOrDefault(OmniLogistics.CARD_MODE.get(), 0)} : new byte[0];
    }

    /** Set (or clear) one of the card's reference slots, keeping the others. */
    public static void setFilter(ItemStack card, int index, ItemStack ref) {
        java.util.List<ItemStack> refs = LogisticsCardItem.spec(card).withRef(index, ref).refs();
        if (refs.isEmpty()) card.remove(OmniLogistics.CARD_FILTER.get());
        else card.set(OmniLogistics.CARD_FILTER.get(), com.mertokan.omnilogistics.api.FilterRef.of(refs));
    }

    /** The whole filter at once (tests, the showcase). */
    public static void setFilter(ItemStack card, ItemStack ref) {
        setFilter(card, 0, ref);
    }

    /** Everything that says WHAT a card matches - references (as many as the destination can hold), flags, tags,
     *  components and the EXTRACT / INSERT mode. Never the binding: two cards silently pointing at the same block is a
     *  bug, not a feature. */
    public static void copyFilter(ItemStack src, ItemStack dst) {
        FilterSpec f = LogisticsCardItem.spec(src);
        apply(dst, modes(src), f.flags(), f.tags(), f.components());
        int cap = LogisticsCardItem.capacity(dst);
        for (int i = 0; i < cap; i++) setFilter(dst, i, i < f.refs().size() ? f.refs().get(i) : ItemStack.EMPTY);
    }

    public static void apply(ItemStack card, byte[] modes, int flags, List<String> tags, List<String> components) {
        FilterSpec s = LogisticsCardItem.spec(card).withConfig(flags, tags, components);
        if (modes.length > 0) card.set(OmniLogistics.CARD_MODE.get(), Math.floorMod(modes[0], 2));
        card.set(OmniLogistics.CARD_FLAGS.get(), s.flags());
        if (s.tags().isEmpty()) card.remove(OmniLogistics.CARD_TAGS.get()); else card.set(OmniLogistics.CARD_TAGS.get(), s.tags());
        if (s.components().isEmpty()) card.remove(OmniLogistics.CARD_COMPONENTS.get()); else card.set(OmniLogistics.CARD_COMPONENTS.get(), s.components());
    }
}
