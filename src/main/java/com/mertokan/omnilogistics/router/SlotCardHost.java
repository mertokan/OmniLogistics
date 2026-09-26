package com.mertokan.omnilogistics.router;

import com.mertokan.omnilogistics.core.FilterHost;
import com.mertokan.omnilogistics.core.FilterLayout;
import com.mertokan.omnilogistics.core.FilterMenu;
import com.mertokan.omnilogistics.api.FilterSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** FilterHost over a card sitting in a {@link CardSlots} slot: right-click the card in the block's GUI to edit it in place. */
public record SlotCardHost(CardSlots host, int slot) implements FilterHost {
    /** Encoded into FilterMenu's {@code hand} field: -2 - slot. */
    public static int handCode(int slot) { return -2 - slot; }
    public static int slotOf(int handCode) { return -2 - handCode; }

    public static @Nullable SlotCardHost of(CardSlots host, int slot) {
        if (slot < 0 || slot >= host.cards().getSlots()) return null;
        ItemStack card = host.cards().getStackInSlot(slot);
        return card.getItem() instanceof LogisticsCardItem c && c.kind.hasFilter ? new SlotCardHost(host, slot) : null;
    }

    /** Open the in-place filter screen for the card in {@code slot}; false when that slot holds no filter card. */
    public static boolean open(ServerPlayer player, CardSlots host, BlockPos pos, int slot) {
        SlotCardHost h = of(host, slot);
        if (h == null) return false;
        int code = handCode(slot);
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new FilterMenu(id, inv, h, pos, code), h.stack().getHoverName()),
            buf -> FilterMenu.writeData(buf, h, pos, code));
        return true;
    }

    private ItemStack stack() {
        return host.cards().getStackInSlot(slot);
    }

    @Override public FilterLayout layout() { return host.cardLayout(stack()); }
    @Override public byte[] modes() { return layout() == FilterLayout.CARD ? CardConfig.modes(stack()) : new byte[0]; }
    @Override public FilterSpec spec() { return LogisticsCardItem.spec(stack()); }

    @Override public int filterSlots() { return LogisticsCardItem.capacity(stack()); }

    @Override
    public void setFilter(int index, ItemStack s) {
        CardConfig.setFilter(stack(), index, s);
        host.sync();
    }

    @Override
    public void applyConfig(byte[] modes, int flags, List<String> tags, List<String> components,
                            List<com.mertokan.omnilogistics.api.NbtRule> rules) {
        CardConfig.apply(stack(), modes, flags, tags, components, rules);
        host.sync();
    }
}
