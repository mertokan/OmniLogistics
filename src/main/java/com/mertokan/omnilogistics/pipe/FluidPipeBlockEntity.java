package com.mertokan.omnilogistics.pipe;

import com.mertokan.omnilogistics.core.Caps;
import com.mertokan.omnilogistics.OmniLogistics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/** Fluid conduit. Buffer = 4 ops of throughput, plus one Fluid Card slot: with a card in it only the fluids its
 *  reference buckets name may pass, and an empty slot is a plain pipe. */
public class FluidPipeBlockEntity extends ConduitBlockEntity implements com.mertokan.omnilogistics.router.CardSlots {
    public final net.neoforged.neoforge.items.ItemStackHandler cards = new net.neoforged.neoforge.items.ItemStackHandler(1) {
        @Override public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack s) { return com.mertokan.omnilogistics.router.LogisticsCardItem.isFluidCard(s); }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override protected void onContentsChanged(int slot) { if (level != null && !level.isClientSide()) sync(); else setChanged(); }
    };
    private final int rate;
    private final FluidTank buffer;

    public FluidPipeBlockEntity(BlockPos pos, BlockState state) {
        super(OmniLogistics.FLUID_PIPE_BE.get(), pos, state);
        rate = ((SmartPipeBlock) state.getBlock()).tier.fluid();
        buffer = new FluidTank(rate * 4) {
            @Override protected void onContentsChanged() { markDirty(); }
        };
    }

    @Override public net.neoforged.neoforge.items.ItemStackHandler cards() { return cards; }

    /** A conduit only uses the filter, so the card GUI hides its EXTRACT / INSERT buttons here, like the item pipe. */
    @Override public com.mertokan.omnilogistics.core.FilterLayout cardLayout(net.minecraft.world.item.ItemStack card) {
        return com.mertokan.omnilogistics.core.FilterLayout.CARD_NOMODE;
    }

    /** A conduit holding a card always has something to configure, even in the middle of a run. */
    @Override
    public boolean isEnd() {
        return super.isEnd() || !cards.getStackInSlot(0).isEmpty();
    }

    /** The card's filter, or pass-everything when the slot is empty. */
    public com.mertokan.omnilogistics.api.FilterSpec spec() {
        net.minecraft.world.item.ItemStack card = cards.getStackInSlot(0);
        return com.mertokan.omnilogistics.router.LogisticsCardItem.isFluidCard(card)
            ? com.mertokan.omnilogistics.router.LogisticsCardItem.spec(card) : com.mertokan.omnilogistics.api.FilterSpec.EMPTY;
    }

    public @Nullable IFluidHandler handlerFor(@Nullable Direction side) {
        if (side != null && mode(side) == Mode.NONE) return null;
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int tank) { return buffer.getFluid(); }
            @Override public int getTankCapacity(int tank) { return buffer.getCapacity(); }
            @Override public boolean isFluidValid(int tank, FluidStack stack) { return accepts(side) && spec().testFluid(stack); }
            @Override public int fill(FluidStack resource, FluidAction action) {
                if (!accepts(side) || !spec().testFluid(resource)) return 0;
                int got = buffer.fill(resource, action);
                if (action.execute() && got > 0) lastFrom = side;
                return got;
            }
            @Override public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
            @Override public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
        };
    }

    @Override protected boolean bufferEmpty() { return buffer.isEmpty(); }
    public FluidStack fluid() { return buffer.getFluid(); }
    public int capacity() { return buffer.getCapacity(); }
    @Override public net.minecraft.network.chat.Component bufferInfo() {
        return buffer.isEmpty() ? net.minecraft.network.chat.Component.translatable("jade.omnilogistics.buffer_empty")
            : net.minecraft.network.chat.Component.translatable("jade.omnilogistics.buffer_fluid", buffer.getFluidAmount(), buffer.getCapacity(), buffer.getFluid().getHoverName());
    }
    @Override protected boolean bufferEmptyOrPartial() { return buffer.getSpace() > 0; }

    private @Nullable IFluidHandler cap(Direction d) {
        return Caps.fluids(level, worldPosition.relative(d), d.getOpposite());
    }

    @Override
    protected boolean pull(Direction from) {
        IFluidHandler src = cap(from);
        if (src == null) return false;
        FluidStack there = src.getFluidInTank(0);
        if (!there.isEmpty() && !spec().testFluid(there)) return false;   // the card decides what this pipe carries
        if (FluidUtil.tryFluidTransfer(buffer, src, rate, true).isEmpty()) return false;
        lastFrom = from;
        return true;
    }

    @Override
    protected boolean push(Direction to) {
        IFluidHandler dst = cap(to);
        return dst != null && !FluidUtil.tryFluidTransfer(dst, buffer, rate, true).isEmpty();
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput tag) {
        super.saveAdditional(tag);
        buffer.serialize(tag.child("Fluid"));
        cards.serialize(tag.child("Card"));
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput tag) {
        super.loadAdditional(tag);
        buffer.deserialize(tag.childOrEmpty("Fluid"));
        cards.deserialize(tag.childOrEmpty("Card"));
    }
}
