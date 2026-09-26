package com.mertokan.omnilogistics.pipe;

import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.router.LogisticsCardItem;
import com.mertokan.omnilogistics.router.SlotCardHost;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Conduit GUI: side modes + redstone (read from the block entity on both sides, written through {@link PipeConfigPayload})
 * and, for item pipes, one card slot: the Logistics Card in it is the filter. Right-click the card to edit it in place.
 */
public class PipeMenu extends AbstractContainerMenu {
    public static final int CARD_SLOT = 0, SLOT_X = 8, SLOT_Y = 132, INV_X = 8, INV_Y = 164;
    public final BlockPos pos;
    public final boolean hasCard;
    private final Player player;
    private final int playerStart;

    public static void writeData(RegistryFriendlyByteBuf buf, ConduitBlockEntity be) {
        buf.writeBlockPos(be.getBlockPos());
        buf.writeVarInt(be instanceof PipeBlockEntity ? 1 : be instanceof FluidPipeBlockEntity ? 2 : 0);
    }

    /** Client. */
    public PipeMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), slotFor(buf.readVarInt()));
    }

    /** Server. */
    public PipeMenu(int id, Inventory inv, ConduitBlockEntity be) {
        this(id, inv, be.getBlockPos(), be instanceof PipeBlockEntity p ? p.cards
            : be instanceof FluidPipeBlockEntity f ? f.cards : null);
    }

    /** The client side of the card slot: the same validator the block entity uses, picked by what the server sent. */
    private static @Nullable IItemHandler slotFor(int kind) {
        if (kind == 0) return null;
        return new ItemStackHandler(1) {
            @Override public boolean isItemValid(int slot, ItemStack s) {
                return kind == 1 ? LogisticsCardItem.isFilterCard(s) : LogisticsCardItem.isFluidCard(s);
            }
            @Override public int getSlotLimit(int slot) { return 1; }
        };
    }

    private PipeMenu(int id, Inventory inv, BlockPos pos, @Nullable IItemHandler cards) {
        super(OmniLogistics.PIPE_MENU.get(), id);
        this.pos = pos;
        this.player = inv.player;
        this.hasCard = cards != null;
        if (cards != null) addSlot(new SlotItemHandler(cards, 0, SLOT_X, SLOT_Y) {
            @Override public boolean mayPlace(ItemStack s) { return getItemHandler().isItemValid(0, s); }
            @Override public int getMaxStackSize() { return 1; }
        });
        playerStart = slots.size();
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, INV_X + c * 18, INV_Y + 58));
    }

    public @Nullable ConduitBlockEntity conduit() {
        return player.level().getBlockEntity(pos) instanceof ConduitBlockEntity c ? c : null;
    }

    /** 6 side modes + redstone mode; zeros when the block is gone. */
    public byte[] config() {
        ConduitBlockEntity c = conduit();
        return c == null ? new byte[ConduitBlockEntity.CONFIG_LEN] : c.config();
    }

    /** Right-click the card in its slot: edit its filter in place. */
    @Override
    public void clicked(int slotId, int button, ContainerInput type, Player player) {
        if (hasCard && slotId == CARD_SLOT && button == 1 && type == ContainerInput.PICKUP && getCarried().isEmpty()
            && LogisticsCardItem.isFilterCard(slots.get(CARD_SLOT).getItem())) {
            if (player instanceof ServerPlayer sp && conduit() instanceof PipeBlockEntity p) SlotCardHost.open(sp, p, pos, CARD_SLOT);
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        boolean ok = index < playerStart
            ? moveItemStackTo(stack, playerStart, slots.size(), true)
            : hasCard && moveItemStackTo(stack, CARD_SLOT, CARD_SLOT + 1, false);
        if (!ok) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    /** Closes (and rejects clicks) once the conduit is gone or swapped for another type, like vanilla's stillValidBlockEntity. */
    @Override
    public boolean stillValid(Player player) {
        ConduitBlockEntity c = conduit();
        return c != null && (c instanceof PipeBlockEntity) == hasCard && player.isWithinBlockInteractionRange(pos, 4.0);
    }
}
