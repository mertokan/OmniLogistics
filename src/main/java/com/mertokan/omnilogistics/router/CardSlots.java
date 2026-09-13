package com.mertokan.omnilogistics.router;

import com.mertokan.omnilogistics.core.FilterLayout;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/** A block entity holding Logistics Cards in slots that can be configured in place (router, item pipe). */
public interface CardSlots {
    ItemStackHandler cards();

    /** Push the edited card to clients. */
    void sync();

    /** Screen variant for a card edited in place. Pipes hide EXTRACT / INSERT: they only use the filter. */
    default FilterLayout cardLayout(ItemStack card) {
        return CardConfig.layout(card);
    }
}
