package com.mertokan.omnilogistics.router;

import com.mojang.authlib.GameProfile;
import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.api.FilterSpec;
import com.mertokan.omnilogistics.core.IntervalHost;
import com.mertokan.omnilogistics.core.MenuHost;
import com.mertokan.omnilogistics.core.OmniConfig;
import com.mertokan.omnilogistics.core.TickingBlockEntity;
import com.mertokan.omnilogistics.core.Transfer;
import com.mertokan.omnilogistics.core.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Wireless hub: 8 card slots, 1-stack item buffer, FE buffer, fluid tank, 2 upgrade slots
 * (Speed: x2 card actions per cycle each, Range: +base each). The clock is a free 1..200 tick field in the GUI, nothing
 * gates it. Every interval one card acts (round-robin, more with Speed Upgrades); detector cards refresh the redstone output.
 * See {@link CardKind} for what each card does.
 */
public class RouterBlockEntity extends TickingBlockEntity implements MenuHost, CardSlots, IntervalHost, com.mertokan.omnilogistics.core.InfoLines {
    public static final int CARDS = 8, UPGRADES = 2, DEFAULT_INTERVAL = 8;
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("7b0a2a3e-6e1f-4b6a-9c53-d3f2c1b0a9e8"), "[OmniLogistics]");

    public final ItemStackHandler cards = new ItemStackHandler(CARDS) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.getItem() instanceof LogisticsCardItem; }
        @Override protected void onContentsChanged(int slot) { if (level != null && !level.isClientSide) sync(); else setChanged(); } // clients edit cards in place, keep them fresh
    };
    public final ItemStackHandler buffer = new ItemStackHandler(1) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    public final ItemStackHandler upgrades = new ItemStackHandler(UPGRADES) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return Upgrades.isRouterUpgrade(stack); }
        @Override public int getSlotLimit(int slot) { return 3; }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    public final EnergyStorage energy;
    public final FluidTank fluid;
    private int rr;
    private int signal;
    /** Chunks this router currently keeps loaded, as ChunkPos longs in its own level. */
    private final java.util.Set<Long> forced = new java.util.HashSet<>();

    public RouterBlockEntity(BlockPos pos, BlockState state) {
        super(OmniLogistics.ROUTER_BE.get(), pos, state);
        energy = new EnergyStorage(OmniConfig.ROUTER_ENERGY_CAPACITY.get(), Integer.MAX_VALUE, Integer.MAX_VALUE);
        fluid = new FluidTank(OmniConfig.ROUTER_FLUID_CAPACITY.get()) {
            @Override protected void onContentsChanged() { setChanged(); }
        };
    }

    /** Ticks between actions as typed in the GUI; 0 = never touched, run at {@link #DEFAULT_INTERVAL}. */
    private int interval;

    public int speedUpgrades() { return Math.min(3, Upgrades.count(upgrades, 0, UPGRADES, OmniLogistics.SPEED_UPGRADE.get())); }
    public boolean chunkLoading() { return Upgrades.has(upgrades, 0, UPGRADES, OmniLogistics.CHUNK_UPGRADE.get()); }

    @Override public int interval() { return interval > 0 ? interval : DEFAULT_INTERVAL; }
    @Override public void setInterval(int ticks) { interval = IntervalHost.clamp(ticks); setChanged(); }

    public int range() { return OmniConfig.ROUTER_RANGE.get() * (1 + Upgrades.count(upgrades, 0, UPGRADES, OmniLogistics.RANGE_UPGRADE.get())); }
    /** Speed Upgrades stopped touching the clock and landed here: each one doubles the card actions one cycle gets.
     *  (Not the amount per action - the item buffer is one stack, so a bigger number would move nothing.) */
    public int mult() { return 1 << speedUpgrades(); }
    public int signal() { return signal; }

    @Override
    public void serverTick() {
        if (level.getGameTime() % 100 == 0) refreshChunks((ServerLevel) level);
        if (level.getGameTime() % interval() != 0) return;
        ServerLevel sl = (ServerLevel) level;
        int sig = 0;
        for (int i = 0; i < CARDS; i++) {
            ItemStack card = cards.getStackInSlot(i);
            if (card.getItem() instanceof LogisticsCardItem c && c.kind == CardKind.DETECTOR && detect(card, sl)) sig = 15;
        }
        if (sig != signal) {
            signal = sig;
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
        for (int n = 0; n < mult(); n++) if (!step(sl)) break;
    }

    /** One card acts, round-robin; false when no card could do anything this pass. */
    private boolean step(ServerLevel sl) {
        for (int k = 0; k < CARDS; k++) {
            int i = (rr + k) % CARDS;
            ItemStack card = cards.getStackInSlot(i);
            if (!(card.getItem() instanceof LogisticsCardItem c) || c.kind == CardKind.DETECTOR) continue;
            if (!payCrossDim(card)) continue;
            if (act(c.kind, card, sl)) { rr = (i + 1) % CARDS; return true; }
        }
        rr = (rr + 1) % CARDS;
        return false;
    }

    /** Chunk Loader Upgrade: keep this router's chunk, and the chunks of the blocks its cards point at, ticking while
     *  nobody is nearby. Only in this level - a ticket in another dimension is a different kind of promise, and the
     *  cross-dimension cards pay FE instead. */
    private void refreshChunks(ServerLevel sl) {
        java.util.Set<Long> want = new java.util.HashSet<>();
        if (chunkLoading()) {
            int cap = OmniConfig.ROUTER_CHUNKS.get();
            want.add(net.minecraft.world.level.ChunkPos.asLong(worldPosition));
            for (int i = 0; i < CARDS && want.size() < cap; i++) {
                net.minecraft.core.GlobalPos gp = cards.getStackInSlot(i).get(OmniLogistics.CARD_TARGET.get());
                if (gp != null && gp.dimension() == sl.dimension()) want.add(net.minecraft.world.level.ChunkPos.asLong(gp.pos()));
            }
        }
        if (want.equals(forced)) return;
        for (long c : forced) if (!want.contains(c)) ticket(sl, c, false);
        for (long c : want) if (!forced.contains(c)) ticket(sl, c, true);
        forced.clear();
        forced.addAll(want);
    }

    private void ticket(ServerLevel sl, long chunk, boolean add) {
        OmniLogistics.CHUNK_TICKETS.forceChunk(sl, worldPosition,
            net.minecraft.world.level.ChunkPos.getX(chunk), net.minecraft.world.level.ChunkPos.getZ(chunk), add, true);
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) {
            for (long c : forced) ticket(sl, c, false);
            forced.clear();
        }
        super.setRemoved();
    }

    /** A card bound in another dimension keeps working - the range only ever applied inside one level - but reaching
     *  across costs the router FE, so a Nether link is a deliberate build and not a free side effect. */
    private boolean payCrossDim(ItemStack card) {
        net.minecraft.core.GlobalPos gp = card.get(OmniLogistics.CARD_TARGET.get());
        if (gp == null || gp.dimension() == level.dimension()) return true;
        int cost = OmniConfig.ROUTER_CROSSDIM_FE.get();
        if (cost == 0) return true;
        if (energy.getEnergyStored() < cost) return false;
        energy.extractEnergy(cost, false);
        return true;
    }

    private boolean act(CardKind kind, ItemStack card, ServerLevel sl) {
        return switch (kind) {
            case ITEM -> moveItems(card, sl);
            case ENERGY -> moveEnergy(card, sl);
            case FLUID -> moveFluid(card, sl);
            case VOID -> voidBuffer(card);
            case VACUUM -> vacuum(card, sl);
            case ACTIVATOR -> activate(card, sl);
            case BREAKER -> breakBlock(card, sl);
            case PLACER -> placeBlock(card, sl);
            case STOCK -> stock(card, sl);
            case DETECTOR -> false;
        };
    }

    // ---- item / energy / fluid ------------------------------------------------------------

    private boolean moveItems(ItemStack card, ServerLevel sl) {
        FilterSpec spec = LogisticsCardItem.spec(card);
        ItemStack held = buffer.getStackInSlot(0);
        if (LogisticsCardItem.extractMode(card)) {
            if (!held.isEmpty()) return false;
            IItemHandler h = LogisticsCardItem.extractTarget(card, sl, worldPosition, range(), spec::test);
            return h != null && Transfer.pull(h, buffer, spec, OmniConfig.ROUTER_ITEMS.get() << LogisticsCardItem.speed(card));
        }
        if (held.isEmpty() || !spec.test(held)) return false;
        IItemHandler h = LogisticsCardItem.insertTarget(card, sl, worldPosition, range(), held);
        return h != null && Transfer.pushSlot(buffer, 0, h);
    }

    private boolean moveEnergy(ItemStack card, ServerLevel sl) {
        IEnergyStorage h = LogisticsCardItem.energyTarget(card, sl, worldPosition, range(), LogisticsCardItem.extractMode(card));
        if (h == null) return false;
        int rate = OmniConfig.ROUTER_ENERGY_RATE.get() << LogisticsCardItem.speed(card);
        if (LogisticsCardItem.extractMode(card)) {
            int e = h.extractEnergy(Math.min(rate, energy.getMaxEnergyStored() - energy.getEnergyStored()), true);
            if (e <= 0) return false;
            energy.receiveEnergy(h.extractEnergy(e, false), false);
        } else {
            int e = h.receiveEnergy(energy.extractEnergy(rate, true), true);
            if (e <= 0) return false;
            h.receiveEnergy(energy.extractEnergy(e, false), false);
        }
        return true;
    }

    private boolean moveFluid(ItemStack card, ServerLevel sl) {
        IFluidHandler h = LogisticsCardItem.fluidTarget(card, sl, worldPosition, range(), LogisticsCardItem.extractMode(card));
        if (h == null) return false;
        int rate = OmniConfig.ROUTER_FLUID_RATE.get() << LogisticsCardItem.speed(card);
        FilterSpec spec = LogisticsCardItem.spec(card);
        // the card names the fluid with a bucket in its reference slot; an empty filter still means "anything"
        net.neoforged.neoforge.fluids.FluidStack want = LogisticsCardItem.extractMode(card) ? h.getFluidInTank(0) : fluid.getFluid();
        if (!want.isEmpty() && !spec.testFluid(want)) return false;
        return LogisticsCardItem.extractMode(card)
            ? !FluidUtil.tryFluidTransfer(fluid, h, rate, true).isEmpty()
            : !FluidUtil.tryFluidTransfer(h, fluid, rate, true).isEmpty();
    }

    // ---- Modular Routers style modules -----------------------------------------------------

    private boolean voidBuffer(ItemStack card) {
        ItemStack s = buffer.getStackInSlot(0);
        if (s.isEmpty() || !LogisticsCardItem.spec(card).test(s)) return false;
        buffer.setStackInSlot(0, ItemStack.EMPTY);
        return true;
    }

    private boolean vacuum(ItemStack card, ServerLevel sl) {
        BlockPos center = LogisticsCardItem.targetPos(card);
        ServerLevel lvl = sl;
        if (center != null) {
            lvl = LogisticsCardItem.targetLevel(card, sl, worldPosition, range());
            if (lvl == null) return false;
        } else {
            center = worldPosition;
        }
        FilterSpec spec = LogisticsCardItem.spec(card);
        double r = OmniConfig.VACUUM_RADIUS.get();
        boolean moved = false;
        for (ItemEntity e : lvl.getEntitiesOfClass(ItemEntity.class, AABB.ofSize(Vec3.atCenterOf(center), r * 2, r * 2, r * 2))) {
            ItemStack s = e.getItem();
            if (!e.isAlive() || s.isEmpty() || !spec.test(s)) continue;
            ItemStack rest = buffer.insertItem(0, s.copy(), false);
            if (rest.getCount() == s.getCount()) continue;
            if (rest.isEmpty()) e.discard(); else e.setItem(rest);
            moved = true;
            if (buffer.getStackInSlot(0).getCount() >= buffer.getSlotLimit(0)) break;
        }
        return moved;
    }

    private @Nullable FakePlayer fake(ServerLevel lvl, BlockPos at) {
        FakePlayer p = FakePlayerFactory.get(lvl, PROFILE);
        p.moveTo(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 0, 0);
        return p;
    }

    private static BlockHitResult hitOn(BlockPos target, Direction side) {
        return new BlockHitResult(Vec3.atCenterOf(target).relative(side, 0.5), side, target, false);
    }

    private boolean activate(ItemStack card, ServerLevel sl) {
        ServerLevel lvl = LogisticsCardItem.targetLevel(card, sl, worldPosition, range());
        if (lvl == null) return false;
        BlockPos target = LogisticsCardItem.targetPos(card);
        Direction side = LogisticsCardItem.targetSide(card);
        FakePlayer p = fake(lvl, target.relative(side));
        ItemStack held = buffer.getStackInSlot(0).copy();
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        var result = p.gameMode.useItemOn(p, lvl, held, InteractionHand.MAIN_HAND, hitOn(target, side));
        buffer.setStackInSlot(0, p.getMainHandItem().copy());
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return result.consumesAction();
    }

    private boolean breakBlock(ItemStack card, ServerLevel sl) {
        if (!buffer.getStackInSlot(0).isEmpty()) return false;
        ServerLevel lvl = LogisticsCardItem.targetLevel(card, sl, worldPosition, range());
        if (lvl == null) return false;
        BlockPos target = LogisticsCardItem.targetPos(card);
        BlockState state = lvl.getBlockState(target);
        if (state.isAir() || state.getDestroySpeed(lvl, target) < 0 || !state.getFluidState().isEmpty()) return false;
        FakePlayer p = fake(lvl, target.relative(LogisticsCardItem.targetSide(card)));
        boolean first = true;
        for (ItemStack drop : Block.getDrops(state, lvl, target, lvl.getBlockEntity(target), p, ItemStack.EMPTY)) {
            ItemStack rest = first ? buffer.insertItem(0, drop, false) : drop;
            first = false;
            if (!rest.isEmpty()) Block.popResource(lvl, target, rest);
        }
        lvl.destroyBlock(target, false, p);
        return true;
    }

    private boolean placeBlock(ItemStack card, ServerLevel sl) {
        ItemStack s = buffer.getStackInSlot(0);
        if (!(s.getItem() instanceof BlockItem bi)) return false;
        ServerLevel lvl = LogisticsCardItem.targetLevel(card, sl, worldPosition, range());
        if (lvl == null) return false;
        BlockPos target = LogisticsCardItem.targetPos(card);
        if (!lvl.getBlockState(target).canBeReplaced()) return false;
        Direction side = LogisticsCardItem.targetSide(card);
        FakePlayer p = fake(lvl, target.relative(side));
        ItemStack held = s.copy();
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        boolean ok = bi.place(new BlockPlaceContext(p, InteractionHand.MAIN_HAND, held, hitOn(target, side.getOpposite()))).consumesAction();
        buffer.setStackInSlot(0, p.getMainHandItem().copy());
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return ok;
    }

    /** Stock Keeper: top the bound inventory up to one stack of whatever the buffer is carrying, x2 per overclock step.
     *  Pair it with an EXTRACT card on a chest and the machine it watches never runs dry. */
    private boolean stock(ItemStack card, ServerLevel sl) {
        ItemStack s = buffer.getStackInSlot(0);
        if (s.isEmpty() || !LogisticsCardItem.spec(card).test(s)) return false;
        IItemHandler h = LogisticsCardItem.insertTarget(card, sl, worldPosition, range(), s);
        if (h == null) return false;
        int want = s.getMaxStackSize() << LogisticsCardItem.speed(card), have = 0;
        for (int i = 0; i < h.getSlots() && have < want; i++) {
            ItemStack t = h.getStackInSlot(i);
            if (ItemStack.isSameItemSameComponents(t, s)) have += t.getCount();
        }
        if (have >= want) return false;
        ItemStack move = buffer.extractItem(0, Math.min(want - have, s.getCount()), false);
        ItemStack rest = ItemHandlerHelper.insertItem(h, move, false);
        if (!rest.isEmpty()) buffer.insertItem(0, rest, false);
        return rest.getCount() < move.getCount();
    }

    private boolean detect(ItemStack card, ServerLevel sl) {
        FilterSpec spec = LogisticsCardItem.spec(card);
        return LogisticsCardItem.extractTarget(card, sl, worldPosition, range(), spec::test) != null;
    }

    // ---- menu / drops / NBT -----------------------------------------------------------------

    @Override
    public java.util.List<Component> infoLines() {
        int n = 0;
        for (int i = 0; i < CARDS; i++) if (!cards.getStackInSlot(i).isEmpty()) n++;
        java.util.List<Component> out = new java.util.ArrayList<>();
        out.add(Component.translatable("jade.omnilogistics.router", n, range(), interval()));
        int far = 0;
        for (int i = 0; i < CARDS; i++) {
            net.minecraft.core.GlobalPos gp = cards.getStackInSlot(i).get(OmniLogistics.CARD_TARGET.get());
            if (gp != null && gp.dimension() != level.dimension()) far++;
        }
        if (far > 0) out.add(Component.translatable("jade.omnilogistics.router_crossdim", far, OmniConfig.ROUTER_CROSSDIM_FE.get()));
        if (chunkLoading()) out.add(Component.translatable("jade.omnilogistics.router_chunks", forced.size()));
        out.add(Component.translatable("jade.omnilogistics.energy", energy.getEnergyStored(), energy.getMaxEnergyStored()));
        if (!fluid.isEmpty()) out.add(Component.translatable("jade.omnilogistics.buffer_fluid", fluid.getFluidAmount(), fluid.getCapacity(), fluid.getFluid().getHoverName()));
        ItemStack b = buffer.getStackInSlot(0);
        if (!b.isEmpty()) out.add(Component.translatable("jade.omnilogistics.buffer_item", b.getCount(), b.getHoverName()));
        if (signal > 0) out.add(Component.translatable("jade.omnilogistics.signal", signal));
        return out;
    }

    @Override public ItemStackHandler cards() { return cards; }
    @Override public Component getDisplayName() { return getBlockState().getBlock().getName(); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new RouterMenu(id, inv, this); }
    @Override public void writeMenuData(RegistryFriendlyByteBuf buf) { buf.writeBlockPos(worldPosition); }

    @Override
    public void collect(java.util.List<ItemStack> out) {
        take(cards, out);
        take(buffer, out);
        take(upgrades, out);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.saveAdditional(tag, regs);
        tag.put("Cards", cards.serializeNBT(regs));
        tag.put("Buffer", buffer.serializeNBT(regs));
        tag.put("Upgrades", upgrades.serializeNBT(regs));
        tag.put("Energy", energy.serializeNBT(regs));
        tag.put("Fluid", fluid.writeToNBT(regs, new CompoundTag()));
        tag.putInt("Signal", signal);
        tag.putInt("Interval", interval);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.loadAdditional(tag, regs);
        if (tag.contains("Cards")) cards.deserializeNBT(regs, tag.getCompound("Cards"));
        if (tag.contains("Buffer")) buffer.deserializeNBT(regs, tag.getCompound("Buffer"));
        if (tag.contains("Upgrades")) upgrades.deserializeNBT(regs, tag.getCompound("Upgrades"));
        if (tag.contains("Energy")) energy.deserializeNBT(regs, tag.get("Energy"));
        if (tag.contains("Fluid")) fluid.readFromNBT(regs, tag.getCompound("Fluid"));
        signal = tag.getInt("Signal");
        interval = tag.getInt("Interval");   // old worlds carry "Speed" instead: dropped, upgrades no longer clock the router
    }
}
