package com.mertokan.omnilogistics.core;

import com.mertokan.omnilogistics.api.FilterSpec;
import com.mertokan.omnilogistics.OmniLogistics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * One menu for every {@link FilterHost}. Slots 0..n-1 are ghost reference slots (they never consume the carried stack);
 * n is the host's capacity, 1 for a block or a plain card and 4 / 16 / 64 for the tiered cards, laid out as a grid of
 * up to 8 columns. Both sides resolve the host themselves (block entity in the level, or the card in hand), so modes,
 * flags, tag and component picks are read straight from it. Changes go through {@link FilterConfigPayload}.
 */
public class FilterMenu extends AbstractContainerMenu {
    public static final int GRID_Y = 20, INV_X = 47, INV_Y = 162, BASE_HEIGHT = 242;
    public final FilterLayout layout;
    public final BlockPos pos;
    /** -1 = block at pos, else InteractionHand ordinal of the card being configured. */
    public final int hand;
    /** Reference slots this host offers. */
    public final int refs;
    private final Player player;
    private final ItemStackHandler ghost;

    public static int cols(int refs) { return Math.min(8, Math.max(1, refs)); }
    public static int rows(int refs) { return (Math.max(1, refs) + 7) / 8; }
    public static int gridX(int refs) { return 128 - cols(refs) * 9; }
    /** Everything under the grid moves down by this much, and so does the panel. */
    public static int shift(int refs) { return (rows(refs) - 1) * 18; }
    public static int height(int refs) { return BASE_HEIGHT + shift(refs); }

    public static void writeData(RegistryFriendlyByteBuf buf, FilterHost host, BlockPos pos, int hand) {
        buf.writeEnum(host.layout());
        buf.writeBlockPos(pos);
        buf.writeVarInt(hand);
        buf.writeVarInt(host.filterSlots());
    }

    /** Client. */
    public FilterMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readEnum(FilterLayout.class), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt());
    }

    /** Server. */
    public FilterMenu(int id, Inventory inv, FilterHost host, BlockPos pos, int hand) {
        this(id, inv, host.layout(), pos, hand, host.filterSlots());
        for (int i = 0; i < refs; i++) ghost.setStackInSlot(i, host.spec().ref(i).copy());
    }

    private FilterMenu(int id, Inventory inv, FilterLayout layout, BlockPos pos, int hand, int refs) {
        super(OmniLogistics.FILTER_MENU.get(), id);
        this.layout = layout;
        this.pos = pos;
        this.hand = hand;
        this.refs = Math.max(1, Math.min(FilterSpec.MAX_REFS, refs));
        this.player = inv.player;
        this.ghost = new ItemStackHandler(this.refs);
        int gx = gridX(this.refs), invY = INV_Y + shift(this.refs);
        for (int i = 0; i < this.refs; i++)
            addSlot(new SlotItemHandler(ghost, i, gx + (i % 8) * 18, GRID_Y + (i / 8) * 18));
        int locked = hand == 0 ? inv.selected : -1; // the card being edited must stay in hand
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, INV_X + c * 18, invY + r * 18));
        for (int c = 0; c < 9; c++) {
            int idx = c;
            addSlot(new Slot(inv, c, INV_X + c * 18, invY + 58) {
                @Override public boolean mayPickup(Player p) { return idx != locked; }
            });
        }
    }

    public @Nullable FilterHost host() {
        return FilterConfigPayload.resolve(player, pos, hand);
    }

    public int mode(int i) {
        FilterHost h = host();
        return h == null ? 0 : h.modes()[i];
    }

    public byte[] modes() {
        FilterHost h = host();
        return h == null ? new byte[layout.modeCount()] : h.modes();
    }

    public FilterSpec spec() {
        FilterHost h = host();
        return h == null ? FilterSpec.EMPTY : h.spec();
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < refs) {
            setFilter(slotId, getCarried().copyWithCount(1)); // empty carried = clear this slot
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    /** Shift-click in the inventory copies that stack into the first free reference slot instead of moving it. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < refs) return ItemStack.EMPTY;
        ItemStack stack = slots.get(index).getItem().copyWithCount(1);
        int free = 0;
        while (free < refs && !ghost.getStackInSlot(free).isEmpty()) free++;
        setFilter(free < refs ? free : refs - 1, stack);
        return ItemStack.EMPTY;
    }

    private void setFilter(int index, ItemStack stack) {
        ghost.setStackInSlot(index, stack);
        FilterHost h = host();
        if (h != null && !player.level().isClientSide) h.setFilter(index, stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return host() != null;
    }
}
