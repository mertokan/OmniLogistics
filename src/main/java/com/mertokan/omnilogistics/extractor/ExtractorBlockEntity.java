package com.mertokan.omnilogistics.extractor;

import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.core.Battery;
import com.mertokan.omnilogistics.api.ComponentPredicateEngine;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Slots: 0..2 gear lanes (1 + Parallel Upgrades active), 3 book / donor, 4..7 outputs, 8..11 upgrades.
 * Base machine only moves enchantments to books. Component Module: also pulls items out of modded components.
 * Fusion Module: FUSE mode merges the donor slot into the gear. Speed Upgrade halves the FE per copy (max 3);
 * the time per operation is a free 1..200 tick field in the GUI.
 * Gear stays in its lane and is transformed in place; finished gear and outputs auto-eject.
 */
public class ExtractorBlockEntity extends TickingBlockEntity implements MenuHost, IntervalHost, com.mertokan.omnilogistics.core.InfoLines {
    public static final int LANES = 3, BOOK = 3, EXTRA = 4, EXTRA_N = 4, UPGRADE = 8, UPGRADE_N = 4, SLOTS = 12;
    private static final int EJECT_INTERVAL = 8;

    public final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override public boolean isItemValid(int slot, ItemStack s) {
            if (slot < LANES) return fuseMode() || gearValid(s);
            if (slot == BOOK) return fuseMode() || s.is(Items.BOOK) || s.is(Items.BOOKSHELF);
            if (slot >= UPGRADE) return Upgrades.isMachineUpgrade(s);
            return true;
        }
        @Override public int getSlotLimit(int slot) { return slot >= UPGRADE ? 3 : 64; }
        @Override protected void onContentsChanged(int slot) {
            if (slot < LANES) { laneDone[slot] = false; progress[slot] = 0; }
            setChanged();
        }
    };
    public final Battery energy;
    final int[] progress = new int[LANES];
    private final boolean[] laneDone = new boolean[LANES];
    boolean fuse;
    private int bookCredit; // a bookshelf = 3 books

    /** World-facing view: insert into active lanes + book slot; extract outputs and finished gear. */
    public final IItemHandler sided = new IItemHandler() {
        @Override public int getSlots() { return UPGRADE; }
        @Override public ItemStack getStackInSlot(int i) { return items.getStackInSlot(i); }
        @Override public ItemStack insertItem(int i, ItemStack s, boolean sim) {
            return (i < lanes() || i == BOOK) ? items.insertItem(i, s, sim) : s;
        }
        @Override public ItemStack extractItem(int i, int n, boolean sim) {
            if (i >= EXTRA || (i < LANES && done(i))) return items.extractItem(i, n, sim);
            return ItemStack.EMPTY;
        }
        @Override public int getSlotLimit(int i) { return items.getSlotLimit(i); }
        @Override public boolean isItemValid(int i, ItemStack s) { return (i < lanes() || i == BOOK) && items.isItemValid(i, s); }
    };

    public ExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniLogistics.EXTRACTOR_BE.get(), pos, state);
        energy = new Battery(OmniConfig.EXTRACTOR_CAPACITY.get());
    }

    /** Ticks per operation as typed in the GUI; 0 = never touched, run at the configured default. */
    private int interval;

    public int speedUpgrades() { return Math.min(3, Upgrades.count(items, UPGRADE, SLOTS, OmniLogistics.SPEED_UPGRADE.get())); }

    @Override public int interval() { return interval > 0 ? interval : OmniConfig.EXTRACTOR_TIME.get(); }
    @Override public void setInterval(int ticks) { interval = IntervalHost.clamp(ticks); setChanged(); }

    // ---- upgrades ----------------------------------------------------------------------

    public int lanes() { return Math.min(LANES, 1 + Upgrades.count(items, UPGRADE, SLOTS, OmniLogistics.PARALLEL_UPGRADE.get())); }
    /** Speed Upgrades stopped touching the clock and landed here: each one halves the FE an operation costs. */
    public int cost() { return Math.max(1, OmniConfig.EXTRACTOR_COST.get() >> speedUpgrades()); }
    public boolean gemModule() { return Upgrades.has(items, UPGRADE, SLOTS, OmniLogistics.GEM_MODULE.get()); }
    public boolean fusionModule() { return Upgrades.has(items, UPGRADE, SLOTS, OmniLogistics.FUSION_MODULE.get()); }
    public boolean fuseMode() { return fuse && fusionModule(); }

    public void toggleMode() {
        fuse = !fuse;
        setChanged();
    }

    private static boolean gearValid(ItemStack s) {
        return ComponentPredicateEngine.hasEnchantments(s) || ComponentPredicateEngine.hasModComponents(s);
    }

    // ---- processing --------------------------------------------------------------------

    @Override
    public void serverTick() {
        for (int lane = 0; lane < lanes(); lane++) step(lane);
        if (level.getGameTime() % EJECT_INTERVAL == 0) eject();
        setChanged();
    }

    private void step(int lane) {
        if (items.getStackInSlot(lane).isEmpty()) { progress[lane] = 0; return; }
        if (laneDone[lane]) return;
        if (progress[lane] > 0) {
            if (++progress[lane] >= interval()) { finish(lane); progress[lane] = 0; }
        } else if (energy.getEnergyStored() >= cost() && canWork(lane)) {
            energy.consume(cost());
            progress[lane] = 1;
            if (progress[lane] >= interval()) { finish(lane); progress[lane] = 0; }   // a typed 1 is one tick, not two
        }
    }

    private boolean canWork(int lane) {
        return fuseMode() ? planFuse(lane) != null : planExtract(lane) != null;
    }

    /** Non-null when the lane produces something and every output fits. */
    private ComponentExtractor.@Nullable Result planExtract(int lane) {
        ItemStack gear = items.getStackInSlot(lane);
        if (gear.isEmpty()) return null;
        ComponentExtractor.Result r = ComponentExtractor.extract(gear, hasBook(), gemModule(), level.registryAccess());
        if (r.extracted().isEmpty()) return null;
        ItemStackHandler tmp = new ItemStackHandler(EXTRA_N);
        for (int i = 0; i < EXTRA_N; i++) tmp.setStackInSlot(i, items.getStackInSlot(EXTRA + i).copy());
        for (ItemStack s : r.extracted()) if (!ItemHandlerHelper.insertItem(tmp, s, false).isEmpty()) return null;
        return r;
    }

    private ComponentExtractor.@Nullable Fusion planFuse(int lane) {
        ItemStack gear = items.getStackInSlot(lane), donor = items.getStackInSlot(BOOK);
        if (gear.isEmpty() || donor.isEmpty()) return null;
        ComponentExtractor.Fusion f = ComponentExtractor.fuse(gear, donor);
        return f.changed() ? f : null;
    }

    private void finish(int lane) {
        if (fuseMode()) {
            ComponentExtractor.Fusion f = planFuse(lane);
            if (f == null) return;
            items.extractItem(BOOK, 1, false);
            items.setStackInSlot(lane, f.fused());
            laneDone[lane] = true;
        } else {
            ComponentExtractor.Result r = planExtract(lane);
            if (r == null) return;
            if (r.usedBook()) consumeBook();
            items.setStackInSlot(lane, r.cleaned()); // gear stays where the player put it
            for (ItemStack s : r.extracted())
                for (int i = EXTRA; i < EXTRA + EXTRA_N && !s.isEmpty(); i++) s = items.insertItem(i, s, false);
            laneDone[lane] = nothingLeft(r.cleaned());
        }
    }

    /** Nothing more this machine (with its current modules) could extract, even with a book available. */
    private boolean nothingLeft(ItemStack gear) {
        return ComponentExtractor.extract(gear, true, gemModule(), level.registryAccess()).extracted().isEmpty();
    }

    private boolean done(int lane) {
        ItemStack gear = items.getStackInSlot(lane);
        if (gear.isEmpty()) return false;
        return laneDone[lane] || (!fuseMode() && nothingLeft(gear));
    }

    private boolean hasBook() {
        return bookCredit > 0 || !items.getStackInSlot(BOOK).isEmpty();
    }

    private void consumeBook() {
        if (bookCredit > 0) { bookCredit--; return; }
        if (items.getStackInSlot(BOOK).is(Items.BOOKSHELF)) bookCredit = 2;
        items.extractItem(BOOK, 1, false);
    }

    private void eject() {
        for (Direction d : Direction.values()) {
            IItemHandler dst = neighbor(d);
            if (dst == null) continue;
            for (int slot = EXTRA; slot < EXTRA + EXTRA_N; slot++) Transfer.pushSlot(items, slot, dst);
            for (int lane = 0; lane < LANES; lane++) if (done(lane)) Transfer.pushSlot(items, lane, dst);
        }
    }

    @Override
    public java.util.List<Component> infoLines() {
        return java.util.List.of(
            Component.translatable("jade.omnilogistics.energy", energy.getEnergyStored(), energy.getMaxEnergyStored()),
            Component.translatable("jade.omnilogistics.extractor", lanes(), interval(), Component.translatable(fuseMode() ? "gui.omnilogistics.mode_fuse" : "gui.omnilogistics.mode_extract")));
    }

    @Override public Component getDisplayName() { return getBlockState().getBlock().getName(); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new ExtractorMenu(id, inv, this); }
    @Override public void writeMenuData(RegistryFriendlyByteBuf buf) { buf.writeBlockPos(worldPosition); }

    @Override
    public void collect(java.util.List<ItemStack> out) {
        take(items, out);
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput tag) {
        super.saveAdditional(tag);
        items.serialize(tag.child("Items"));
        energy.serialize(tag.child("Energy"));
        tag.putIntArray("Progress", progress);
        int[] done = new int[LANES];
        for (int i = 0; i < LANES; i++) done[i] = laneDone[i] ? 1 : 0;
        tag.putIntArray("Done", done);
        tag.putBoolean("Fuse", fuse);
        tag.putInt("Interval", interval);
        tag.putInt("BookCredit", bookCredit);
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput tag) {
        super.loadAdditional(tag);
        items.deserialize(tag.childOrEmpty("Items"));
        energy.deserialize(tag.childOrEmpty("Energy"));
        int[] p = tag.getIntArray("Progress").orElse(new int[0]);
        int[] d = tag.getIntArray("Done").orElse(new int[0]);
        for (int i = 0; i < LANES; i++) {
            progress[i] = p.length == LANES ? p[i] : 0;
            laneDone[i] = d.length == LANES && d[i] != 0;
        }
        fuse = tag.getBooleanOr("Fuse", false);
        interval = tag.getIntOr("Interval", 0);   // old worlds carry "Speed" instead: dropped, upgrades no longer clock the machine
        bookCredit = tag.getIntOr("BookCredit", 0);
    }
}
