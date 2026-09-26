package com.mertokan.omnilogistics.router;

import com.mertokan.omnilogistics.core.FilterHost;
import com.mertokan.omnilogistics.core.FilterLayout;
import com.mertokan.omnilogistics.api.FilterSpec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** FilterHost view over the card currently in the player's hand; every call reads/writes the stack's components. */
public record CardHost(Player player, InteractionHand hand) implements FilterHost {
    private ItemStack stack() {
        return player.getItemInHand(hand);
    }

    @Override public FilterLayout layout() { return CardConfig.layout(stack()); }
    @Override public byte[] modes() { return CardConfig.modes(stack()); }
    @Override public FilterSpec spec() { return LogisticsCardItem.spec(stack()); }
    @Override public int filterSlots() { return LogisticsCardItem.capacity(stack()); }
    @Override public void setFilter(int index, ItemStack s) { CardConfig.setFilter(stack(), index, s); }

    @Override
    public void applyConfig(byte[] modes, int flags, List<String> tags, List<String> components,
                            List<com.mertokan.omnilogistics.api.NbtRule> rules) {
        CardConfig.apply(stack(), modes, flags, tags, components, rules);
    }
}
