package com.mertokan.omnilogistics.distributor;

import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.api.ComponentPredicateEngine;
import com.mertokan.omnilogistics.api.FilterSpec;
import com.mertokan.omnilogistics.core.InfoLines;
import com.mertokan.omnilogistics.core.MenuHost;
import com.mertokan.omnilogistics.core.OmniConfig;
import com.mertokan.omnilogistics.core.TickingBlockEntity;
import com.mertokan.omnilogistics.core.Transfer;
import com.mertokan.omnilogistics.core.Upgrades;
import com.mertokan.omnilogistics.core.Wrenchable;
import com.mertokan.omnilogistics.router.CardConfig;
import com.mertokan.omnilogistics.router.CardSlots;
import com.mertokan.omnilogistics.router.LogisticsCardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Batch Distributor: makes one AE2 Pattern Provider feed a machine whose inputs live in several places.
 * Six lanes, one Logistics Card each, one card = one destination (sneak + right-click the machine face to bind it).
 * <p>
 * There is no splitting algorithm: AE2 walks our slots in index order carrying the remainder
 * ({@code ExternalStorageFacade.insertExternal}), so each ingredient lands in the lowest-index lane whose filter matches,
 * and the lane delivers it to its own machine on the next tick. A lane with no filter accepts nothing, so lane 0 cannot
 * swallow the batch; give one lane an INVERT-only filter to make it the deliberate catch-all.
 * An EXTRACT card turns its lane around: it pulls from that machine and pushes into the return face (wrench a face to
 * pin it), i.e. finished products go back into the ME network.
 * <p>
 * Filters fill themselves. A bound INSERT card with no filter is a LEARNER: the first ingredient that arrives is written
 * onto it, so setting up a recipe is "drop the cards in, bind them, run the craft once". An unfiltered EXTRACT lane needs
 * no filter at all - it takes whatever the machine holds that is NOT an ingredient delivered to that same machine, which
 * is the product. Nothing here knows about AE2 patterns: it learns from what actually lands in the block.
 */
public class DistributorBlockEntity extends TickingBlockEntity implements MenuHost, CardSlots, InfoLines, Wrenchable {
    /** Lanes the block can ever have; {@link #active()} is how many are unlocked right now. */
    public static final int LANES = 12, BASE_LANES = 6, PER_UPGRADE = 3;
    /** How far the auto-bind button looks for more of the same machine. */
    public static final int BIND_RADIUS = 8;
    /** GUI status per lane. */
    public static final int NO_CARD = 0, UNSET = 1, IDLE = 2, HOLDING = 3, CLASH = 4, LOCKED = 5, LEARN = 6;

    public final ItemStackHandler upgrades = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int i, ItemStack s) { return s.is(OmniLogistics.PARALLEL_UPGRADE.get()); }
        @Override public int getSlotLimit(int i) { return 2; }
        @Override protected void onContentsChanged(int i) { if (level != null && !level.isClientSide()) sync(); else setChanged(); }
    };
    /** SPLIT: one ingredient per lane, each to its own machine. CLUSTER: the whole batch to one machine, next batch to the next. */
    private boolean cluster;
    private int rr;

    public final ItemStackHandler cards = new ItemStackHandler(LANES) {
        @Override public boolean isItemValid(int i, ItemStack s) { return LogisticsCardItem.isFilterCard(s); }
        @Override public int getSlotLimit(int i) { return 1; }
        @Override protected void onContentsChanged(int i) {
            ItemStack c = getStackInSlot(i);
            // a fresh card defaults to EXTRACT; in a lane the useful default is INSERT (deliver to the machine)
            if (!c.isEmpty() && !c.has(OmniLogistics.CARD_MODE.get())) c.set(OmniLogistics.CARD_MODE.get(), LogisticsCardItem.INSERT);
            if (level != null && !level.isClientSide()) sync(); else setChanged();
        }
    };
    /** Lane i buffer. Unfiltered on purpose: the filter lives on {@link #view}, so a return lane can pull into its own slot. */
    public final ItemStackHandler lanes = new ItemStackHandler(LANES) {
        @Override protected void onContentsChanged(int i) { setChanged(); }
    };
    private long lastInsert = -1;
    private @Nullable Direction home;   // wrenched return face; null = the first neighbour no lane targets
    /** Which face last pushed a batch into us. The return lane must never hand the product back to its own feeder - the
     *  hopper or the AE2 Pattern Provider sitting on top is an inventory like any other, and used to win the auto-pick. */
    private @Nullable Direction fedFrom;
    private final IItemHandler[] sidedViews = new IItemHandler[6];

    /** What AE2 (or a pipe, or a hopper) sees on every face: insert only, each slot filtered by that lane's card. */
    public final IItemHandler view = new IItemHandler() {
        @Override public int getSlots() { return active(); }
        @Override public ItemStack getStackInSlot(int i) { return lanes.getStackInSlot(i); }   // AE2 blocking mode reads this
        @Override public boolean isItemValid(int i, ItemStack s) { return accepts(i, s); }
        @Override public int getSlotLimit(int i) { return lanes.getSlotLimit(i); }
        @Override public ItemStack extractItem(int i, int n, boolean sim) { return ItemStack.EMPTY; }
        @Override public ItemStack insertItem(int i, ItemStack s, boolean sim) {
            if (!accepts(i, s)) return s;
            ItemStack rest = lanes.insertItem(i, s, sim);
            if (!sim && rest.getCount() < s.getCount()) {
                lastInsert = level.getGameTime();
                if (!cluster && learner(i)) learn(i, s);   // the first ingredient that lands here becomes this lane's filter
            }
            return rest;
        }
    };

    /** The same view, per face, so the block knows who is feeding it. */
    public IItemHandler view(@Nullable Direction side) {
        if (side == null) return view;
        if (sidedViews[side.ordinal()] == null) sidedViews[side.ordinal()] = new SidedView(side);
        return sidedViews[side.ordinal()];
    }

    private final class SidedView implements IItemHandler {
        private final Direction side;

        private SidedView(Direction side) {
            this.side = side;
        }

        @Override public int getSlots() { return view.getSlots(); }
        @Override public ItemStack getStackInSlot(int i) { return view.getStackInSlot(i); }
        @Override public boolean isItemValid(int i, ItemStack s) { return view.isItemValid(i, s); }
        @Override public int getSlotLimit(int i) { return view.getSlotLimit(i); }
        @Override public ItemStack extractItem(int i, int n, boolean sim) { return ItemStack.EMPTY; }

        @Override
        public ItemStack insertItem(int i, ItemStack s, boolean sim) {
            ItemStack rest = view.insertItem(i, s, sim);
            if (!sim && rest.getCount() < s.getCount() && fedFrom != side) {
                fedFrom = side;
                setChanged();
            }
            return rest;
        }
    }

    public DistributorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniLogistics.DISTRIBUTOR_BE.get(), pos, state);
    }

    /** 6 lanes, +3 per Parallel Upgrade, up to 12. */
    public int active() {
        return Math.min(LANES, BASE_LANES + PER_UPGRADE * Upgrades.count(upgrades, 0, 1, OmniLogistics.PARALLEL_UPGRADE.get()));
    }

    public boolean cluster() { return cluster; }

    public void toggleMode() {
        cluster = !cluster;
        sync();
    }

    /** SPLIT: lane i takes s when its card is a bound ITEM card in INSERT mode carrying a filter the player actually set.
     *  CLUSTER: the lanes are just a batch buffer, so anything is taken as long as a machine is bound - but only while the
     *  buffer is empty or this same tick is still delivering the batch that started it, so two patterns never mix. */
    private boolean accepts(int i, ItemStack s) {
        if (i >= active()) return false;
        if (cluster) return hasTarget() && (allEmpty() || lastInsert == level.getGameTime());
        ItemStack card = cards.getStackInSlot(i);
        if (!LogisticsCardItem.isFilterCard(card) || LogisticsCardItem.extractMode(card)) return false;
        if (LogisticsCardItem.targetPos(card) == null) return false;
        FilterSpec f = LogisticsCardItem.spec(card);
        // a bound lane with no filter yet is a LEARNER: it takes the first ingredient nobody else asked for and keeps it
        if (!configured(f)) return !claimedElsewhere(i, s);
        return f.test(s);
    }

    /** A bound INSERT card with no filter yet. Place the cards, bind them, let one craft through: the filters fill themselves. */
    private boolean learner(int i) {
        ItemStack card = cards.getStackInSlot(i);
        return LogisticsCardItem.isFilterCard(card) && !LogisticsCardItem.extractMode(card)
            && LogisticsCardItem.targetPos(card) != null && !configured(LogisticsCardItem.spec(card));
    }

    /** Another lane already filters for this item, so a learner must not steal it - an explicit lane always wins. */
    private boolean claimedElsewhere(int i, ItemStack s) {
        for (int k = 0; k < active(); k++) {
            if (k == i) continue;
            ItemStack c = cards.getStackInSlot(k);
            if (!LogisticsCardItem.isFilterCard(c) || LogisticsCardItem.extractMode(c)) continue;
            FilterSpec f = LogisticsCardItem.spec(c);
            if (configured(f) && f.test(s)) return true;
        }
        return false;
    }

    /** Write what actually arrived onto the lane's card. One craft through the block is the whole setup. */
    private void learn(int i, ItemStack s) {
        ItemStack card = cards.getStackInSlot(i);
        CardConfig.setFilter(card, 0, s.copyWithCount(1));
        card.set(OmniLogistics.CARD_FLAGS.get(), ComponentPredicateEngine.MATCH_ITEM);
        cards.setStackInSlot(i, card);
    }

    /** What an unfiltered EXTRACT lane may take out of that machine: anything that is NOT an ingredient a lane delivers to
     *  the same machine. That is the craft's product, without the player filling in a filter - and it is why an unfiltered
     *  return lane no longer sucks the ingredients straight back out. Null = nothing is known yet, so take nothing. */
    private @Nullable FilterSpec returnSpec(ItemStack card, @Nullable BlockPos target) {
        FilterSpec own = LogisticsCardItem.spec(card);
        if (configured(own)) return own;
        if (target == null) return null;
        List<ItemStack> ingredients = new ArrayList<>();
        for (int k = 0; k < active(); k++) {
            ItemStack c = cards.getStackInSlot(k);
            if (!LogisticsCardItem.isFilterCard(c) || LogisticsCardItem.extractMode(c)) continue;
            if (!target.equals(LogisticsCardItem.targetPos(c))) continue;
            for (ItemStack r : LogisticsCardItem.spec(c).refs()) if (!r.isEmpty()) ingredients.add(r);
        }
        if (ingredients.isEmpty()) return null;
        return new FilterSpec(List.copyOf(ingredients),
            ComponentPredicateEngine.MATCH_ITEM | ComponentPredicateEngine.INVERT, List.of(), List.of());
    }

    private boolean hasTarget() {
        for (int i = 0; i < active(); i++)
            if (LogisticsCardItem.isFilterCard(cards.getStackInSlot(i)) && LogisticsCardItem.targetPos(cards.getStackInSlot(i)) != null) return true;
        return false;
    }

    private boolean allEmpty() {
        for (int i = 0; i < LANES; i++) if (!lanes.getStackInSlot(i).isEmpty()) return false;
        return true;
    }

    /** An untouched card passes everything, which would make lane 0 eat the whole batch, so "not configured" = accepts nothing.
     *  Any flag beyond the default counts, which makes an INVERT-only card the deliberate catch-all lane. */
    private static boolean configured(FilterSpec f) {
        return !f.ref().isEmpty() || !f.tags().isEmpty() || (f.flags() & ~ComponentPredicateEngine.MATCH_ITEM) != 0;
    }

    @Override
    public void serverTick() {
        if (level.getGameTime() == lastInsert) return;   // let the whole batch land before anything leaves
        ServerLevel sl = (ServerLevel) level;
        if (cluster) { dispatch(sl); return; }
        for (int i = 0; i < active(); i++) {
            ItemStack card = cards.getStackInSlot(i);
            if (!LogisticsCardItem.isFilterCard(card)) continue;
            boolean ret = LogisticsCardItem.extractMode(card);
            if (!ret && lanes.getStackInSlot(i).isEmpty()) continue;
            IItemHandler dst = ret ? LogisticsCardItem.resolveTarget(card, sl, worldPosition, OmniConfig.ROUTER_RANGE.get())
                : LogisticsCardItem.insertTarget(card, sl, worldPosition, OmniConfig.ROUTER_RANGE.get(), lanes.getStackInSlot(i));
            if (dst == null) continue;
            if (!ret) { Transfer.pushSlot(lanes, i, dst); continue; }
            IItemHandler back = home();                  // return lane: machine -> provider, same tick, never buffers
            FilterSpec pullSpec = returnSpec(card, LogisticsCardItem.targetPos(card));
            if (back == null || pullSpec == null) continue;
            Transfer.pull(dst, lanes, i, pullSpec, OmniConfig.ROUTER_ITEMS.get() << LogisticsCardItem.speed(card));
            Transfer.pushSlot(lanes, i, back);
        }
    }

    /** CLUSTER: hand the whole buffered batch to one machine - the next one, in turn, that can take all of it. A machine is
     *  a BLOCK, not a face: every lane card bound to the same position belongs to the same machine, so a machine whose
     *  inputs sit on different faces (Mekanism's Metallurgic Infuser wants the item on one face and the infusion on
     *  another) still counts as one destination. Machines that are still busy refuse the batch, so it goes to a free one
     *  and AE2 keeps pushing the next pattern. */
    private void dispatch(ServerLevel sl) {
        if (allEmpty()) return;
        List<BlockPos> machines = new ArrayList<>();
        for (int i = 0; i < active(); i++) {
            BlockPos t = LogisticsCardItem.targetPos(cards.getStackInSlot(i));
            if (LogisticsCardItem.isFilterCard(cards.getStackInSlot(i)) && t != null && !machines.contains(t)) machines.add(t);
        }
        for (int k = 0; k < machines.size(); k++) {
            int idx = (rr + k) % machines.size();
            List<IItemHandler> faces = faces(sl, machines.get(idx));
            if (faces.isEmpty() || !takesAll(faces)) continue;
            for (int i = 0; i < LANES; i++)
                for (IItemHandler dst : faces) {
                    if (lanes.getStackInSlot(i).isEmpty()) break;
                    Transfer.pushSlot(lanes, i, dst);
                }
            rr = (idx + 1) % machines.size();
            return;
        }
    }

    /** Every way into one machine: the faces its lane cards point at first, then the rest of the block, then its
     *  unsided view. Auto input is ours to solve - a machine with its item on one face and its infusion on another is
     *  one destination, and the player never had to know that. */
    private List<IItemHandler> faces(ServerLevel sl, BlockPos machine) {
        List<IItemHandler> out = new ArrayList<>();
        java.util.Set<Direction> seen = new java.util.HashSet<>();
        for (int i = 0; i < active(); i++) {
            ItemStack card = cards.getStackInSlot(i);
            if (!LogisticsCardItem.isFilterCard(card) || !machine.equals(LogisticsCardItem.targetPos(card))) continue;
            seen.add(LogisticsCardItem.targetSide(card));
        }
        if (seen.isEmpty()) return out;
        for (Direction d : seen) add(sl, machine, d, out);
        for (Direction d : Direction.values()) if (!seen.contains(d)) add(sl, machine, d, out);
        add(sl, machine, null, out);
        return out;
    }

    private void add(ServerLevel sl, BlockPos machine, @Nullable Direction side, List<IItemHandler> out) {
        IItemHandler h = com.mertokan.omnilogistics.core.Caps.items(sl, machine, side);
        if (h != null && !out.contains(h)) out.add(h);
    }

    /** Only hand a batch to a machine that can swallow all of it, across all of its faces: half a recipe in a machine is
     *  exactly what jams AE2. */
    private boolean takesAll(List<IItemHandler> faces) {
        for (int i = 0; i < LANES; i++) {
            ItemStack s = lanes.getStackInSlot(i);
            if (s.isEmpty()) continue;
            boolean fits = false;
            for (IItemHandler dst : faces)
                if (net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(dst, s.copy(), true).isEmpty()) { fits = true; break; }
            if (!fits) return false;
        }
        return true;
    }

    /**
     * One click instead of five. Takes the machine the first bound lane points at, finds every other block of the same
     * kind within {@link #BIND_RADIUS}, and hands each of them a copy of that machine's lanes - same faces, same mode,
     * same filter - written onto the blank cards the player already dropped into the free lanes. A machine whose inputs
     * need two faces therefore costs two lanes per machine, exactly as if it had been bound by hand.
     * Returns how many machines were added.
     */
    public int autoBind(ServerLevel sl) {
        BlockPos ref = null;
        List<Integer> refLanes = new ArrayList<>(), free = new ArrayList<>();
        for (int i = 0; i < active(); i++) {
            ItemStack c = cards.getStackInSlot(i);
            if (!LogisticsCardItem.isFilterCard(c)) continue;
            BlockPos t = LogisticsCardItem.targetPos(c);
            if (t == null) free.add(i);
            else if (ref == null || ref.equals(t)) {
                if (ref == null) ref = t;
                refLanes.add(i);
            }
        }
        if (ref == null || refLanes.isEmpty() || free.size() < refLanes.size()) return 0;

        var kind = sl.getBlockState(ref).getBlock();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-BIND_RADIUS, -BIND_RADIUS, -BIND_RADIUS),
                                                 worldPosition.offset(BIND_RADIUS, BIND_RADIUS, BIND_RADIUS))) {
            if (sl.getBlockState(p).getBlock() != kind || isLaneTarget(p)) continue;
            found.add(p.immutable());
        }
        found.sort((a, b) -> Double.compare(a.distSqr(worldPosition), b.distSqr(worldPosition)));

        int added = 0, next = 0;
        for (BlockPos machine : found) {
            if (next + refLanes.size() > free.size()) break;
            for (int k = 0; k < refLanes.size(); k++) {
                ItemStack src = cards.getStackInSlot(refLanes.get(k));
                ItemStack dst = cards.getStackInSlot(free.get(next++));
                copyConfig(src, dst);
                com.mertokan.omnilogistics.router.LogisticsCardItem.bind(dst, sl, machine, null);
            }
            added++;
        }
        if (added > 0) sync();
        return added;
    }

    /** Everything about a lane card except which block it points at; the destination keeps its own capacity. */
    private static void copyConfig(ItemStack src, ItemStack dst) {
        CardConfig.copyFilter(src, dst);
        dst.set(OmniLogistics.CARD_SIDE.get(), LogisticsCardItem.targetSide(src));
    }

    /** Where a return lane pushes: the wrenched face, else the first neighbour with an item handler that no lane targets
     *  and that is not the block feeding us. */
    private @Nullable IItemHandler home() {
        if (home != null) return neighbor(home);
        for (Direction d : Direction.values()) {
            if (d == fedFrom || isLaneTarget(worldPosition.relative(d))) continue;   // never back into the feeder
            IItemHandler h = neighbor(d);
            if (h != null) return h;
        }
        return null;
    }

    private boolean isLaneTarget(BlockPos pos) {
        for (int i = 0; i < LANES; i++) {
            BlockPos t = LogisticsCardItem.targetPos(cards.getStackInSlot(i));
            if (pos.equals(t)) return true;
        }
        return false;
    }

    /** 0 no card, 1 unbound or unfiltered, 2 idle, 3 holding, 4 its filter is already covered by an earlier lane. */
    public int status(int i) {
        if (i >= active()) return LOCKED;
        ItemStack card = cards.getStackInSlot(i);
        if (!LogisticsCardItem.isFilterCard(card)) return NO_CARD;
        if (LogisticsCardItem.targetPos(card) == null) return UNSET;
        if (cluster) return lanes.getStackInSlot(i).isEmpty() ? IDLE : HOLDING;
        boolean ret = LogisticsCardItem.extractMode(card);
        if (!ret && !configured(LogisticsCardItem.spec(card))) return LEARN;
        if (!ret) for (int k = 0; k < i; k++) {
            ItemStack e = cards.getStackInSlot(k);
            if (LogisticsCardItem.isFilterCard(e) && !LogisticsCardItem.extractMode(e)
                && !LogisticsCardItem.spec(e).ref().isEmpty() && LogisticsCardItem.spec(e).test(LogisticsCardItem.spec(card).ref())) return CLASH;
        }
        return lanes.getStackInSlot(i).isEmpty() ? IDLE : HOLDING;
    }

    @Override
    public Component wrenchFace(Direction face, Player p) {
        home = face == home ? null : face;
        sync();
        return Component.translatable("msg.omnilogistics.distributor_return",
            home == null ? Component.translatable("gui.omnilogistics.distributor_auto") : Component.literal(home.getName()));
    }

    @Override
    public List<Component> infoLines() {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable(cluster ? "gui.omnilogistics.distributor_cluster" : "gui.omnilogistics.distributor_split"));
        for (int i = 0; i < active(); i++) {
            ItemStack card = cards.getStackInSlot(i);
            if (!LogisticsCardItem.isFilterCard(card)) continue;
            BlockPos t = LogisticsCardItem.targetPos(card);
            ItemStack held = lanes.getStackInSlot(i);
            out.add(Component.translatable("jade.omnilogistics.distributor_lane", i + 1,
                t == null ? Component.translatable("tooltip.omnilogistics.card_unbound") : Component.literal(t.toShortString()),
                held.isEmpty() ? Component.translatable("jade.omnilogistics.buffer_empty")
                    : Component.translatable("jade.omnilogistics.buffer_item", held.getCount(), held.getHoverName())));
        }
        if (out.isEmpty()) out.add(Component.translatable("jade.omnilogistics.distributor_empty"));
        return out;
    }

    @Override public ItemStackHandler cards() { return cards; }
    @Override public Component getDisplayName() { return getBlockState().getBlock().getName(); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new DistributorMenu(id, inv, this); }
    @Override public void writeMenuData(RegistryFriendlyByteBuf buf) { buf.writeBlockPos(worldPosition); }

    @Override
    public void collect(List<ItemStack> out) {
        take(cards, out);
        take(lanes, out);
        take(upgrades, out);
    }

    /** ItemStackHandler.deserializeNBT resizes to whatever the tag says, so a distributor saved when the block had six
     *  lanes would come back six wide and the upgrade slots would index past it. Grow it back, keeping the contents. */
    private static void grow(ItemStackHandler h) {
        if (h.getSlots() >= LANES) return;
        List<ItemStack> keep = new ArrayList<>();
        for (int i = 0; i < h.getSlots(); i++) keep.add(h.getStackInSlot(i));
        h.setSize(LANES);
        for (int i = 0; i < keep.size(); i++) h.setStackInSlot(i, keep.get(i));
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput tag) {
        super.saveAdditional(tag);
        cards.serialize(tag.child("Cards"));
        lanes.serialize(tag.child("Lanes"));
        upgrades.serialize(tag.child("Upgrades"));
        tag.putBoolean("Cluster", cluster);
        if (home != null) tag.putByte("Home", (byte) home.ordinal());
        if (fedFrom != null) tag.putByte("Fed", (byte) fedFrom.ordinal());
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput tag) {
        super.loadAdditional(tag);
        { cards.deserialize(tag.childOrEmpty("Cards")); grow(cards); }
        { lanes.deserialize(tag.childOrEmpty("Lanes")); grow(lanes); }
        upgrades.deserialize(tag.childOrEmpty("Upgrades"));
        cluster = tag.getBooleanOr("Cluster", false);
        fedFrom = tag.getInt("Fed").map(i -> Direction.values()[Math.floorMod(i, 6)]).orElse(null);
        home = tag.getInt("Home").map(i -> Direction.values()[Math.floorMod(i, 6)]).orElse(null);
    }
}
