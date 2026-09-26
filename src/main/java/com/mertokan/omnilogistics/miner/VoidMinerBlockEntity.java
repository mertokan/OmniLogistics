package com.mertokan.omnilogistics.miner;

import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.core.Battery;
import com.mertokan.omnilogistics.core.FilterLayout;
import com.mertokan.omnilogistics.api.FilterSpec;
import com.mertokan.omnilogistics.core.InfoLines;
import com.mertokan.omnilogistics.core.IntervalHost;
import com.mertokan.omnilogistics.core.MenuHost;
import com.mertokan.omnilogistics.core.TickingBlockEntity;
import com.mertokan.omnilogistics.core.Transfer;
import com.mertokan.omnilogistics.core.Upgrades;
import com.mertokan.omnilogistics.router.CardSlots;
import com.mertokan.omnilogistics.router.LogisticsCardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 1x1 void miner. A cycle costs the tier's FE up front, rolls the tier's loot table (a Logistics Card in the card slot filters what is
 * kept; a miss still spends the cycle), and after the tier's time the item lands in 4 output slots that auto-eject to every neighbour.
 * Slots: 0 Speed Upgrade (x3, each doubles the items - and the FE - per cycle), 1..4 outputs. The cycle length is
 * a free 1..200 tick field in the GUI. A redstone signal pauses it.
 * Clients get {@link #mining}, {@link #cycleStart}, {@link #cycleTime} and {@link #pending} so the renderer can play the cycle.
 */
public class VoidMinerBlockEntity extends TickingBlockEntity implements MenuHost, InfoLines, CardSlots, IntervalHost {
    public static final int UPGRADE = 0, OUT = 1, OUT_N = 4, SLOTS = 5;
    public static final int IDLE = 0, MINING = 1, FULL = 2, REDSTONE = 3;
    private static final int TRIES = 8, EJECT_INTERVAL = 8;

    public final ItemStackHandler cards = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int slot, ItemStack s) { return LogisticsCardItem.isFilterCard(s); }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override protected void onContentsChanged(int slot) { if (level != null && !level.isClientSide()) sync(); else setChanged(); }
    };
    public final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override public boolean isItemValid(int slot, ItemStack s) { return slot != UPGRADE || s.is(OmniLogistics.SPEED_UPGRADE.get()); }
        @Override public int getSlotLimit(int slot) { return slot == UPGRADE ? 3 : 64; }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    /** World-facing view: the outputs, extract only. */
    public final IItemHandler sided = new IItemHandler() {
        @Override public int getSlots() { return OUT_N; }
        @Override public ItemStack getStackInSlot(int i) { return items.getStackInSlot(OUT + i); }
        @Override public ItemStack insertItem(int i, ItemStack s, boolean sim) { return s; }
        @Override public ItemStack extractItem(int i, int n, boolean sim) { return items.extractItem(OUT + i, n, sim); }
        @Override public int getSlotLimit(int i) { return 64; }
        @Override public boolean isItemValid(int i, ItemStack s) { return false; }
    };
    public final Battery energy;
    public int progress, cycleTime;
    /** Ticks per cycle as typed in the GUI; 0 = never touched, run at the speed of the tier. */
    private int interval;
    public long cycleStart;
    public boolean mining;
    public ItemStack pending = ItemStack.EMPTY;
    private int status = IDLE;

    public VoidMinerBlockEntity(BlockPos pos, BlockState state) {
        super(OmniLogistics.MINER_BE.get(), pos, state);
        energy = new Battery(tier().capacity());
    }

    public MinerTier tier() {
        return getBlockState().getBlock() instanceof VoidMinerBlock b ? b.tier : MinerTier.BASIC;
    }

    public int speedUpgrades() { return Math.min(3, Upgrades.count(items, UPGRADE, UPGRADE + 1, OmniLogistics.SPEED_UPGRADE.get())); }

    @Override public int interval() { return interval > 0 ? interval : tier().time(); }
    @Override public void setInterval(int ticks) { interval = IntervalHost.clamp(ticks); setChanged(); }

    /** Speed Upgrades stopped touching the clock and landed here: each one doubles the yield, and the FE, per cycle. */
    public int mult() { return 1 << speedUpgrades(); }
    public int cost() { return (int) Math.min(Integer.MAX_VALUE, (long) tier().cost() * mult()); }
    public int status() { return status; }

    public FilterSpec spec() {
        ItemStack card = cards.getStackInSlot(0);
        return LogisticsCardItem.isFilterCard(card) ? LogisticsCardItem.spec(card) : FilterSpec.EMPTY;
    }

    // ---- processing --------------------------------------------------------------------

    @Override
    public void serverTick() {
        if (mining) {
            if (progress < interval()) progress++;
            if (cycleTime != interval()) { cycleTime = interval(); sync(); }   // retyped mid-cycle: keep the beam honest
            if (progress >= interval()) {
                if (pending.isEmpty() || deliver()) {
                    mining = false;
                    progress = 0;
                    status = IDLE;
                    sync();
                } else status = FULL;
            }
        }
        if (!mining) {   // a finished cycle starts the next one in the same tick, so the period is exactly interval()
            if (level.hasNeighborSignal(worldPosition)) {
                status = REDSTONE;
            } else if (energy.getEnergyStored() >= cost()) {
                energy.consume(cost());
                pending = roll();
                progress = 0;
                mining = true;
                cycleStart = level.getGameTime();
                cycleTime = interval();
                status = MINING;
                sync();
            } else status = IDLE;
        }
        if (level.getGameTime() % EJECT_INTERVAL == 0) eject();
        setChanged();
    }

    /** First loot roll that passes the card filter, scaled by the upgrades; empty after TRIES misses (the cycle is spent anyway). */
    private ItemStack roll() {
        ServerLevel sl = (ServerLevel) level;
        LootTable table = sl.getServer().reloadableRegistries().getLootTable(tier().loot());
        LootParams params = new LootParams.Builder(sl).create(LootContextParamSets.EMPTY);
        FilterSpec spec = spec();
        for (int attempt = 0; attempt < TRIES; attempt++)
            for (ItemStack s : table.getRandomItems(params))
                if (spec.test(s)) return s.copyWithCount(Math.min(s.getMaxStackSize(), s.getCount() * mult()));   // ponytail: one item type per cycle; roll mult() times if variety ever matters
        return ItemStack.EMPTY;
    }

    /** Puts the pending item into the outputs; what does not fit stays pending. */
    private boolean deliver() {
        ItemStack s = pending;
        for (int i = OUT; i < OUT + OUT_N && !s.isEmpty(); i++) s = items.insertItem(i, s, false);
        pending = s;
        return s.isEmpty();
    }

    private void eject() {
        for (Direction d : Direction.values()) {
            IItemHandler dst = neighbor(d);
            if (dst == null) continue;
            for (int slot = OUT; slot < OUT + OUT_N; slot++) Transfer.pushSlot(items, slot, dst);
        }
    }

    /** Portal particles converge on the top of the beam while a cycle runs. */
    public void clientTick() {
        if (!mining || level == null) return;
        RandomSource r = level.getRandom();
        double a = r.nextDouble() * Math.PI * 2, d = 0.9 + r.nextDouble() * 0.6;
        level.addParticle(ParticleTypes.PORTAL, worldPosition.getX() + 0.5, worldPosition.getY() + 1.3, worldPosition.getZ() + 0.5,
            Math.cos(a) * d, r.nextDouble() * 0.8 - 1.0, Math.sin(a) * d);
    }

    // ---- interfaces --------------------------------------------------------------------

    public static String statusKey(int status) {
        return "gui.omnilogistics.miner." + switch (status) {
            case MINING -> "mining";
            case FULL -> "full";
            case REDSTONE -> "redstone";
            default -> "idle";
        };
    }

    @Override
    public List<Component> infoLines() {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("jade.omnilogistics.energy", energy.getEnergyStored(), energy.getMaxEnergyStored()));
        out.add(Component.translatable("jade.omnilogistics.miner", Component.translatable(statusKey(status)), interval(), cost()));
        ItemStack card = cards.getStackInSlot(0);
        if (!card.isEmpty()) {
            out.add(Component.translatable("jade.omnilogistics.filter_card", card.getHoverName()));
            LogisticsCardItem.describeFilter(card, out);
        }
        return out;
    }

    @Override public ItemStackHandler cards() { return cards; }
    @Override public FilterLayout cardLayout(ItemStack card) { return FilterLayout.CARD_NOMODE; }

    @Override public Component getDisplayName() { return getBlockState().getBlock().getName(); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new MinerMenu(id, inv, this); }
    @Override public void writeMenuData(RegistryFriendlyByteBuf buf) { buf.writeBlockPos(worldPosition); }

    @Override
    public void collect(List<ItemStack> out) {
        take(items, out);
        take(cards, out);
        if (!pending.isEmpty()) { out.add(pending); pending = ItemStack.EMPTY; }
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput tag) {
        super.saveAdditional(tag);
        items.serialize(tag.child("Items"));
        cards.serialize(tag.child("Card"));
        energy.serialize(tag.child("Energy"));
        tag.putInt("Progress", progress);
        tag.putBoolean("Mining", mining);
        tag.putLong("CycleStart", cycleStart);
        tag.putInt("CycleTime", cycleTime);
        tag.putInt("Interval", interval);
        if (!pending.isEmpty()) tag.store("Pending", ItemStack.CODEC, pending);
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput tag) {
        super.loadAdditional(tag);
        items.deserialize(tag.childOrEmpty("Items"));
        cards.deserialize(tag.childOrEmpty("Card"));
        energy.deserialize(tag.childOrEmpty("Energy"));
        progress = tag.getIntOr("Progress", 0);
        mining = tag.getBooleanOr("Mining", false);
        cycleStart = tag.getLongOr("CycleStart", 0L);
        cycleTime = tag.getIntOr("CycleTime", 0);
        interval = tag.getIntOr("Interval", 0);
        pending = tag.read("Pending", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }
}
