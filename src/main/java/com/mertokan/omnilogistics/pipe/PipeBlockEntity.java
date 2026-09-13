package com.mertokan.omnilogistics.pipe;

import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.core.FilterLayout;
import com.mertokan.omnilogistics.api.FilterSpec;
import com.mertokan.omnilogistics.core.Transfer;
import com.mertokan.omnilogistics.router.CardSlots;
import com.mertokan.omnilogistics.router.LogisticsCardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Item conduit: 1-stack buffer + one card slot. The Logistics Card in the slot is the filter (applies when pulling and
 * when accepting from a neighbour); an empty slot means everything passes, i.e. a plain pipe.
 */
public class PipeBlockEntity extends ConduitBlockEntity implements CardSlots {
    public final ItemStackHandler cards = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return LogisticsCardItem.isFilterCard(stack); }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override protected void onContentsChanged(int slot) { if (level != null && !level.isClientSide) sync(); else setChanged(); } // clients edit the card in place
    };
    private final ItemStackHandler buffer = new ItemStackHandler(1) {
        @Override protected void onContentsChanged(int slot) { markDirty(); }
    };
    /** Server game time when the current stack entered: it moves on exactly one interval later, so a chain flows one block per
     *  interval whatever the block entity tick order. Synced to clients in {@link #getUpdateTag} under "Entered";
     *  {@link ConduitRenderer} times the slide off it, so the motion never depends on when a packet happens to land. */
    public long enteredAt;

    public PipeBlockEntity(BlockPos pos, BlockState state) {
        super(OmniLogistics.ITEM_PIPE_BE.get(), pos, state);
    }

    /** A pipe holding a card always has something to configure, even in the middle of a run. */
    @Override
    public boolean isEnd() {
        return super.isEnd() || !cards.getStackInSlot(0).isEmpty();
    }

    /** The card's filter, or pass-everything when the slot is empty. */
    public FilterSpec spec() {
        ItemStack card = cards.getStackInSlot(0);
        return LogisticsCardItem.isFilterCard(card) ? LogisticsCardItem.spec(card) : FilterSpec.EMPTY;
    }

    /** Capability view for neighbours: insert-only, filtered; null on OFF sides so nothing connects there. */
    public @Nullable IItemHandler handlerFor(@Nullable Direction side) {
        if (side != null && mode(side) == Mode.NONE) return null;
        return new IItemHandler() {
            @Override public int getSlots() { return 1; }
            @Override public ItemStack getStackInSlot(int slot) { return buffer.getStackInSlot(0); }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (!accepts(side) || !spec().test(stack)) return stack;
                boolean wasEmpty = buffer.getStackInSlot(0).isEmpty();
                // insertItem fires onContentsChanged -> markDirty -> sync() before the two lines below run; that is fine, the
                // block entity is serialized later, at broadcast time (ChunkHolder.broadcastBlockEntity), so do not reorder.
                ItemStack rest = buffer.insertItem(0, stack, simulate);
                if (!simulate && rest.getCount() < stack.getCount()) {
                    lastFrom = side;
                    if (wasEmpty) enteredAt = level.getGameTime();   // a top-up merges into the stack in flight; re-stamping would jerk it back
                }
                return rest;
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
            @Override public int getSlotLimit(int slot) { return 64; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return accepts(side) && spec().test(stack); }
        };
    }

    @Override protected boolean bufferEmpty() { return buffer.getStackInSlot(0).isEmpty(); }
    @Override protected int syncInterval() { return 1; }

    /** Item pipes sync in the tick the buffer changed. The base class only flags {@code dirty} and syncs at the TOP of the next
     *  serverTick, one tick after the push at the bottom, which put the pipe that emptied a tick behind the pipe that filled:
     *  the client drew the same stack twice at the seam. Energy / fluid keep the throttle. */
    @Override
    protected void markDirty() {
        if (level != null && !level.isClientSide) sync(); else super.markDirty();
    }
    public ItemStack bufferStack() { return buffer.getStackInSlot(0); }

    @Override
    public Component bufferInfo() {
        ItemStack s = buffer.getStackInSlot(0);
        return s.isEmpty() ? Component.translatable("jade.omnilogistics.buffer_empty")
            : Component.translatable("jade.omnilogistics.buffer_item", s.getCount(), s.getHoverName());
    }

    @Override
    public List<Component> infoLines() {
        List<Component> out = super.infoLines();
        ItemStack card = cards.getStackInSlot(0);
        if (!card.isEmpty()) {
            out.add(Component.translatable("jade.omnilogistics.filter_card", card.getHoverName()));
            LogisticsCardItem.describeFilter(card, out);
        }
        return out;
    }

    @Override
    protected boolean pull(Direction from) {
        IItemHandler src = neighbor(from);
        if (src == null || !Transfer.pull(src, buffer, spec(), tier().items)) return false;
        lastFrom = from;
        enteredAt = level.getGameTime();
        return true;
    }

    @Override
    protected boolean readyToPush(boolean phase) {
        return level.getGameTime() - enteredAt >= interval();
    }

    @Override
    protected boolean push(Direction to) {
        IItemHandler dst = neighbor(to);
        return dst != null && Transfer.pushSlot(buffer, 0, dst);
    }

    // ---- CardSlots ----------------------------------------------------------------------

    @Override public ItemStackHandler cards() { return cards; }
    @Override public FilterLayout cardLayout(ItemStack card) { return FilterLayout.CARD_NOMODE; }

    @Override
    public void collect(List<ItemStack> out) {
        take(buffer, out);
        take(cards, out);
    }

    // ---- NBT --------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.saveAdditional(tag, regs);
        tag.put("Buffer", buffer.serializeNBT(regs));
        tag.put("Card", cards.serializeNBT(regs));
    }

    /** Clients need the exact server tick the stack entered. Deliberately NOT in saveAdditional: a timestamp coming back off
     *  disk (or out of a picked-up block) could sit in the future of a fresh world and readyToPush() would never fire again. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider regs) {
        CompoundTag tag = super.getUpdateTag(regs);
        tag.putLong("Entered", enteredAt);
        return tag;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.loadAdditional(tag, regs);
        if (tag.contains("Buffer")) buffer.deserializeNBT(regs, tag.getCompound("Buffer"));
        if (tag.contains("Card")) cards.deserializeNBT(regs, tag.getCompound("Card"));
        if (tag.contains("Entered")) enteredAt = tag.getLong("Entered");   // update-tag only, so this only ever runs on the client
    }
}
