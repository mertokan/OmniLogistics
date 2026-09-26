package com.mertokan.omnilogistics.monitor;

import com.mertokan.omnilogistics.core.Caps;
import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.core.InfoLines;
import com.mertokan.omnilogistics.core.OmniConfig;
import com.mertokan.omnilogistics.core.TickingBlockEntity;
import com.mertokan.omnilogistics.router.LogisticsCardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a bound block through plain capabilities every {@link #INTERVAL} ticks and shows what it found on its screen:
 * name, FE bar, fluid bar, the first few item stacks. Bind it by right-clicking with a Logistics Card that is already
 * bound to the machine (sneak + right-click the machine with the card first); right-click empty-handed to take the card
 * back. It knows nothing about the target's mod - anything with an item / energy / fluid capability shows up.
 */
public class MonitorBlockEntity extends TickingBlockEntity implements InfoLines {
    /** How often the target is read; the screen is a status display, not a meter. */
    public static final int INTERVAL = 10, ITEMS = 6, SCAN = 512;

    public final ItemStackHandler card = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int i, ItemStack s) { return LogisticsCardItem.isFilterCard(s); }
        @Override public int getSlotLimit(int i) { return 1; }
        @Override protected void onContentsChanged(int i) { if (level != null && !level.isClientSide()) sync(); else setChanged(); }
    };
    /** Everything the client draws; refreshed on the server, sent whenever it changes. */
    public String title = "";
    public int energy, energyMax, fluid, fluidMax;
    public String fluidName = "";
    public List<ItemStack> items = List.of();
    /** Kinds the screen could not fit, so a full chest does not silently look like a four-item chest. */
    public int moreKinds;

    public MonitorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniLogistics.MONITOR_BE.get(), pos, state);
    }

    public boolean bound() {
        return LogisticsCardItem.isFilterCard(card.getStackInSlot(0)) && LogisticsCardItem.targetPos(card.getStackInSlot(0)) != null;
    }

    @Override
    public void serverTick() {
        if (level.getGameTime() % INTERVAL != 0) return;
        String oldTitle = title, oldFluid = fluidName;
        int oe = energy, oem = energyMax, of = fluid, ofm = fluidMax, om = moreKinds;
        List<ItemStack> old = items;
        read();
        boolean same = title.equals(oldTitle) && fluidName.equals(oldFluid) && oe == energy && oem == energyMax
            && of == fluid && ofm == fluidMax && om == moreKinds && sameItems(old, items);
        if (!same) sync();
    }

    private static boolean sameItems(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++)
            if (a.get(i).getCount() != b.get(i).getCount() || !ItemStack.isSameItemSameComponents(a.get(i), b.get(i))) return false;
        return true;
    }

    /** One pass over the bound block's capabilities. Everything is optional: a chest has items and nothing else. */
    private void read() {
        title = "";
        energy = energyMax = fluid = fluidMax = 0;
        fluidName = "";
        items = List.of();
        moreKinds = 0;
        ItemStack c = card.getStackInSlot(0);
        if (!bound()) return;
        BlockPos target = LogisticsCardItem.targetPos(c);
        ServerLevel tl = LogisticsCardItem.targetLevel(c, (ServerLevel) level, worldPosition, OmniConfig.ROUTER_RANGE.get());
        if (tl == null || !tl.isLoaded(target)) {
            title = Component.translatable("gui.omnilogistics.monitor_out_of_range").getString();
            return;
        }
        title = tl.getBlockState(target).getBlock().getName().getString();
        IEnergyStorage e = Caps.energy(tl, target, null);
        if (e != null) {
            energy = e.getEnergyStored();
            energyMax = Math.max(1, e.getMaxEnergyStored());
        }
        IFluidHandler f = Caps.fluids(tl, target, null);
        if (f != null && f.getTanks() > 0) {
            FluidStack fs = f.getFluidInTank(0);
            fluid = fs.getAmount();
            fluidMax = Math.max(1, f.getTankCapacity(0));
            fluidName = fs.isEmpty() ? "" : fs.getHoverName().getString();
        }
        IItemHandler h = Caps.items(tl, target, null);
        if (h != null) {
            // one line per KIND, not per slot: a chest of 27 stacks of cobble is one line saying 1728
            List<ItemStack> kinds = new ArrayList<>();
            for (int i = 0; i < h.getSlots() && i < SCAN; i++) {
                ItemStack s = h.getStackInSlot(i);
                if (s.isEmpty()) continue;
                ItemStack same = null;
                for (ItemStack k : kinds) if (ItemStack.isSameItemSameComponents(k, s)) { same = k; break; }
                if (same != null) same.setCount(same.getCount() + s.getCount());
                else kinds.add(s.copyWithCount(s.getCount()));
            }
            kinds.sort((a, b) -> b.getCount() - a.getCount());        // the big piles are what a glance is for
            moreKinds = Math.max(0, kinds.size() - ITEMS);
            items = List.copyOf(kinds.subList(0, Math.min(ITEMS, kinds.size())));
        }
    }

    @Override
    public List<Component> infoLines() {
        List<Component> out = new ArrayList<>();
        out.add(title.isEmpty() ? Component.translatable("gui.omnilogistics.monitor_no_card") : Component.literal(title));
        if (energyMax > 0) out.add(Component.translatable("jade.omnilogistics.energy", energy, energyMax));
        if (fluidMax > 0 && !fluidName.isEmpty()) out.add(Component.translatable("jade.omnilogistics.buffer_fluid", fluid, fluidMax, fluidName));
        for (ItemStack s : items) out.add(Component.translatable("jade.omnilogistics.buffer_item", s.getCount(), s.getHoverName()));
        if (moreKinds > 0) out.add(Component.translatable("gui.omnilogistics.monitor_more", moreKinds));
        return out;
    }

    @Override
    public void collect(List<ItemStack> out) {
        take(card, out);
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput tag) {
        super.saveAdditional(tag);
        card.serialize(tag.child("Card"));
        tag.putString("Title", title);
        tag.putInt("E", energy);
        tag.putInt("EMax", energyMax);
        tag.putInt("F", fluid);
        tag.putInt("FMax", fluidMax);
        tag.putString("FName", fluidName);
        tag.store("Shown", ItemStack.OPTIONAL_CODEC.listOf(), items);
        tag.putInt("More", moreKinds);
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput tag) {
        super.loadAdditional(tag);
        card.deserialize(tag.childOrEmpty("Card"));
        title = tag.getStringOr("Title", "");
        energy = tag.getIntOr("E", 0);
        energyMax = tag.getIntOr("EMax", 0);
        fluid = tag.getIntOr("F", 0);
        fluidMax = tag.getIntOr("FMax", 0);
        fluidName = tag.getStringOr("FName", "");
        items = List.copyOf(tag.read("Shown", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));
        moreKinds = tag.getIntOr("More", 0);
    }
}
