package com.mertokan.omnilogistics.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The one seam between the mod and NeoForge's transfer API. Since 1.21.9 the block capabilities hand out
 * transaction-based {@link ResourceHandler}s and {@link EnergyHandler}s; everything inside the mod still moves things
 * with the older slot interfaces, which NeoForge keeps as adapters in 26.1.
 *
 * <p>Looking something up: {@link #items}, {@link #fluids}, {@link #energy} return the old view of whatever the block
 * offers (NeoForge's own adapters). Offering something: {@link #items(ICapabilityProvider)} and friends wrap our handlers
 * so that a move inside a transaction happens at once and is undone if the transaction aborts. A handler of ours met
 * again through a lookup is unwrapped instead of wrapped twice.
 *
 * Our own lookups use views of our own ({@code Legacy*}) that JOIN an open transaction instead of opening a root one.
 *
 * ponytail: eager apply + undo log, like Fabric's legacy inventory wrappers. Undo of an insert is an extract, so a
 * handler that refuses extraction cannot roll back an aborted insert; the mod itself always commits.
 */
public final class Caps {
    private Caps() {}

    // ---- looking things up --------------------------------------------------------------------------------------

    public static @Nullable IItemHandler items(Level level, BlockPos pos, @Nullable Direction side) {
        ResourceHandler<ItemResource> h = level.getCapability(Capabilities.Item.BLOCK, pos, side);
        return h == null ? null : h instanceof Items own ? own.legacy : new LegacyItems(h);
    }

    public static @Nullable IFluidHandler fluids(Level level, BlockPos pos, @Nullable Direction side) {
        ResourceHandler<FluidResource> h = level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
        return h == null ? null : h instanceof Fluids own ? own.legacy : new LegacyFluids(h);
    }

    public static @Nullable IEnergyStorage energy(Level level, BlockPos pos, @Nullable Direction side) {
        EnergyHandler h = level.getCapability(Capabilities.Energy.BLOCK, pos, side);
        return h == null ? null : h instanceof Energy own ? own.legacy : new LegacyEnergy(h);
    }

    /**
     * The transaction a legacy call should join. NeoForge's own legacy adapters always open a ROOT transaction, which
     * crashes the moment one of our handlers is asked to move something while a caller's transaction is already open
     * - a vanilla hopper feeding a Distributor that forwards into a chest does exactly that. Joining the open one as a
     * nested transaction keeps every move inside the caller's commit or rollback.
     */
    @SuppressWarnings("deprecation")
    private static net.neoforged.neoforge.transfer.transaction.@Nullable TransactionContext outer() {
        return net.neoforged.neoforge.transfer.transaction.Transaction.getLifecycle()
            == net.neoforged.neoforge.transfer.transaction.Transaction.Lifecycle.OPEN
            ? net.neoforged.neoforge.transfer.transaction.Transaction.getCurrentOpenedTransaction() : null;
    }

    private static net.neoforged.neoforge.transfer.transaction.Transaction tx() {
        return net.neoforged.neoforge.transfer.transaction.Transaction.open(outer());
    }

    private record LegacyItems(ResourceHandler<ItemResource> h) implements IItemHandler {
        @Override public int getSlots() { return h.size(); }
        @Override public ItemStack getStackInSlot(int i) { return h.getResource(i).toStack(h.getAmountAsInt(i)); }
        @Override public int getSlotLimit(int i) { return h.getCapacityAsInt(i, ItemResource.EMPTY); }
        @Override public boolean isItemValid(int i, ItemStack stack) { return h.isValid(i, ItemResource.of(stack)); }

        @Override
        public ItemStack insertItem(int i, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;
            try (var t = tx()) {
                int n = h.insert(i, ItemResource.of(stack), stack.getCount(), t);
                if (!simulate) t.commit();
                return n >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - n);
            }
        }

        @Override
        public ItemStack extractItem(int i, int amount, boolean simulate) {
            ItemResource r = h.getResource(i);
            if (amount <= 0 || r.isEmpty()) return ItemStack.EMPTY;
            try (var t = tx()) {
                int n = h.extract(i, r, Math.min(amount, r.getMaxStackSize()), t);
                if (!simulate) t.commit();
                return r.toStack(n);
            }
        }
    }

    private record LegacyFluids(ResourceHandler<FluidResource> h) implements IFluidHandler {
        @Override public int getTanks() { return h.size(); }
        @Override public FluidStack getFluidInTank(int i) { return h.getResource(i).toStack(h.getAmountAsInt(i)); }
        @Override public int getTankCapacity(int i) { return h.getCapacityAsInt(i, FluidResource.EMPTY); }
        @Override public boolean isFluidValid(int i, FluidStack stack) { return h.isValid(i, FluidResource.of(stack)); }

        @Override
        public int fill(FluidStack stack, FluidAction action) {
            if (stack.isEmpty()) return 0;
            try (var t = tx()) {
                int n = h.insert(FluidResource.of(stack), stack.getAmount(), t);
                if (action.execute()) t.commit();
                return n;
            }
        }

        @Override
        public FluidStack drain(FluidStack stack, FluidAction action) {
            if (stack.isEmpty()) return FluidStack.EMPTY;
            FluidResource r = FluidResource.of(stack);
            try (var t = tx()) {
                int n = h.extract(r, stack.getAmount(), t);
                if (action.execute()) t.commit();
                return r.toStack(n);
            }
        }

        @Override
        public FluidStack drain(int max, FluidAction action) {
            for (int i = 0; i < h.size(); i++) {
                FluidResource r = h.getResource(i);
                if (!r.isEmpty() && h.getAmountAsLong(i) > 0) return drain(r.toStack(max), action);
            }
            return FluidStack.EMPTY;
        }
    }

    private record LegacyEnergy(EnergyHandler h) implements IEnergyStorage {
        @Override public int getEnergyStored() { return h.getAmountAsInt(); }
        @Override public int getMaxEnergyStored() { return h.getCapacityAsInt(); }
        @Override public boolean canExtract() { return true; }    // the handler answers with 0 when it will not
        @Override public boolean canReceive() { return true; }

        @Override
        public int receiveEnergy(int max, boolean simulate) {
            if (max <= 0) return 0;
            try (var t = tx()) {
                int n = h.insert(max, t);
                if (!simulate) t.commit();
                return n;
            }
        }

        @Override
        public int extractEnergy(int max, boolean simulate) {
            if (max <= 0) return 0;
            try (var t = tx()) {
                int n = h.extract(max, t);
                if (!simulate) t.commit();
                return n;
            }
        }
    }

    // ---- offering ours ----------------------------------------------------------------------------------------------

    public static <B> ICapabilityProvider<B, @Nullable Direction, ResourceHandler<ItemResource>> items(
        ICapabilityProvider<B, @Nullable Direction, @Nullable IItemHandler> p) {
        return (be, side) -> { IItemHandler h = p.getCapability(be, side); return h == null ? null : new Items(h); };
    }

    public static <B> ICapabilityProvider<B, @Nullable Direction, ResourceHandler<FluidResource>> fluids(
        ICapabilityProvider<B, @Nullable Direction, @Nullable IFluidHandler> p) {
        return (be, side) -> { IFluidHandler h = p.getCapability(be, side); return h == null ? null : new Fluids(h); };
    }

    public static <B> ICapabilityProvider<B, @Nullable Direction, EnergyHandler> energy(
        ICapabilityProvider<B, @Nullable Direction, @Nullable IEnergyStorage> p) {
        return (be, side) -> { IEnergyStorage h = p.getCapability(be, side); return h == null ? null : new Energy(h); };
    }

    /** Remembers how to take back each change, and forgets it once the outermost transaction commits. */
    private abstract static class Undo extends SnapshotJournal<Integer> {
        private final List<Runnable> undo = new ArrayList<>();

        void record(TransactionContext tx, Runnable back) {
            undo.add(back);
        }

        void before(TransactionContext tx) {
            updateSnapshots(tx);
        }

        @Override protected Integer createSnapshot() { return undo.size(); }

        @Override protected void revertToSnapshot(Integer size) {
            while (undo.size() > size) undo.remove(undo.size() - 1).run();
        }

        @Override protected void onRootCommit(Integer original) { undo.clear(); }
    }

    private static final class Items extends Undo implements ResourceHandler<ItemResource> {
        final IItemHandler legacy;

        Items(IItemHandler legacy) { this.legacy = legacy; }

        @Override public int size() { return legacy.getSlots(); }
        @Override public ItemResource getResource(int i) { return ItemResource.of(legacy.getStackInSlot(i)); }
        @Override public long getAmountAsLong(int i) { return legacy.getStackInSlot(i).getCount(); }

        @Override public long getCapacityAsLong(int i, ItemResource r) {
            return r.isEmpty() ? legacy.getSlotLimit(i) : Math.min(legacy.getSlotLimit(i), r.getMaxStackSize());
        }

        @Override public boolean isValid(int i, ItemResource r) { return !r.isEmpty() && legacy.isItemValid(i, r.toStack(1)); }

        @Override
        public int insert(int i, ItemResource r, int amount, TransactionContext tx) {
            if (r.isEmpty() || amount <= 0) return 0;
            int fits = amount - legacy.insertItem(i, r.toStack(amount), true).getCount();
            if (fits <= 0) return 0;
            before(tx);
            int done = fits - legacy.insertItem(i, r.toStack(fits), false).getCount();
            if (done > 0) record(tx, () -> legacy.extractItem(i, done, false));
            return done;
        }

        @Override
        public int extract(int i, ItemResource r, int amount, TransactionContext tx) {
            if (r.isEmpty() || amount <= 0 || !r.matches(legacy.getStackInSlot(i))) return 0;
            if (legacy.extractItem(i, amount, true).isEmpty()) return 0;
            before(tx);
            ItemStack got = legacy.extractItem(i, amount, false);
            if (!got.isEmpty()) record(tx, () -> legacy.insertItem(i, got, false));
            return got.getCount();
        }
    }

    /** The old fluid interface has no per-tank fill or drain, so the whole handler answers through tank 0. */
    private static final class Fluids extends Undo implements ResourceHandler<FluidResource> {
        final IFluidHandler legacy;

        Fluids(IFluidHandler legacy) { this.legacy = legacy; }

        @Override public int size() { return legacy.getTanks(); }
        @Override public FluidResource getResource(int i) { return FluidResource.of(legacy.getFluidInTank(i)); }
        @Override public long getAmountAsLong(int i) { return legacy.getFluidInTank(i).getAmount(); }
        @Override public long getCapacityAsLong(int i, FluidResource r) { return legacy.getTankCapacity(i); }
        @Override public boolean isValid(int i, FluidResource r) { return !r.isEmpty() && legacy.isFluidValid(i, r.toStack(1)); }

        @Override
        public int insert(int i, FluidResource r, int amount, TransactionContext tx) {
            if (i != 0 || r.isEmpty() || amount <= 0) return 0;
            if (legacy.fill(r.toStack(amount), IFluidHandler.FluidAction.SIMULATE) <= 0) return 0;
            before(tx);
            int done = legacy.fill(r.toStack(amount), IFluidHandler.FluidAction.EXECUTE);
            if (done > 0) record(tx, () -> legacy.drain(r.toStack(done), IFluidHandler.FluidAction.EXECUTE));
            return done;
        }

        @Override
        public int extract(int i, FluidResource r, int amount, TransactionContext tx) {
            if (i != 0 || r.isEmpty() || amount <= 0) return 0;
            if (legacy.drain(r.toStack(amount), IFluidHandler.FluidAction.SIMULATE).isEmpty()) return 0;
            before(tx);
            FluidStack got = legacy.drain(r.toStack(amount), IFluidHandler.FluidAction.EXECUTE);
            if (!got.isEmpty()) record(tx, () -> legacy.fill(got, IFluidHandler.FluidAction.EXECUTE));
            return got.getAmount();
        }
    }

    private static final class Energy extends Undo implements EnergyHandler {
        final IEnergyStorage legacy;

        Energy(IEnergyStorage legacy) { this.legacy = legacy; }

        @Override public long getAmountAsLong() { return legacy.getEnergyStored(); }
        @Override public long getCapacityAsLong() { return legacy.getMaxEnergyStored(); }

        @Override
        public int insert(int amount, TransactionContext tx) {
            if (amount <= 0 || legacy.receiveEnergy(amount, true) <= 0) return 0;
            before(tx);
            int done = legacy.receiveEnergy(amount, false);
            if (done > 0) record(tx, () -> legacy.extractEnergy(done, false));
            return done;
        }

        @Override
        public int extract(int amount, TransactionContext tx) {
            if (amount <= 0 || legacy.extractEnergy(amount, true) <= 0) return 0;
            before(tx);
            int done = legacy.extractEnergy(amount, false);
            if (done > 0) record(tx, () -> legacy.receiveEnergy(done, false));
            return done;
        }
    }
}
