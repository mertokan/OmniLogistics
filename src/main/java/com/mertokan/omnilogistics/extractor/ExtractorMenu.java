package com.mertokan.omnilogistics.extractor;

import com.mertokan.omnilogistics.OmniLogistics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import static com.mertokan.omnilogistics.extractor.ExtractorBlockEntity.*;

/** data: 0 energy/1000, 1..3 lane progress, 4 lanes, 5 fuse mode, 6 interval, 7 fusion module, 8 capacity/1000, 9 cost/100. */
public class ExtractorMenu extends AbstractContainerMenu {
    public static final int PLAYER = SLOTS;
    public static final int[][] POS = {
        {26, 20}, {26, 42}, {26, 64},                   // lanes (progress bar right of each)
        {62, 42},                                       // book / donor
        {98, 20}, {116, 20}, {98, 42}, {116, 42},       // outputs
        {62, 96}, {80, 96}, {98, 96}, {116, 96},        // upgrades
    };
    public static final int INV_Y = 126;
    public final BlockPos pos;
    private final ContainerData data;

    public ExtractorMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), new ItemStackHandler(SLOTS), new SimpleContainerData(10));
    }

    public ExtractorMenu(int id, Inventory inv, ExtractorBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.items, new ContainerData() {
            @Override public int get(int i) {
                return switch (i) {
                    case 0 -> be.energy.getEnergyStored() / 1000;
                    case 1, 2, 3 -> be.progress[i - 1];
                    case 4 -> be.lanes();
                    case 5 -> be.fuseMode() ? 1 : 0;
                    case 6 -> be.interval();
                    case 7 -> be.fusionModule() ? 1 : 0;
                    case 8 -> be.energy.getMaxEnergyStored() / 1000;
                    default -> be.cost() / 100;
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return 10; }
        });
    }

    private ExtractorMenu(int id, Inventory inv, BlockPos pos, IItemHandler items, ContainerData data) {
        super(OmniLogistics.EXTRACTOR_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        for (int i = 0; i < SLOTS; i++) {
            int slot = i;
            addSlot(new SlotItemHandler(items, i, POS[i][0], POS[i][1]) {
                @Override public boolean mayPlace(ItemStack s) {
                    if (slot < LANES) return slot < lanes() && items.isItemValid(slot, s);
                    if (slot >= EXTRA && slot < UPGRADE) return false;
                    return items.isItemValid(slot, s);
                }
                @Override public int getMaxStackSize() { return slot >= UPGRADE ? 3 : super.getMaxStackSize(); }
            });
        }
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, INV_Y + 58));
        addDataSlots(data);
    }

    public int energy() { return data.get(0) * 1000; }
    public int progress(int lane) { return data.get(1 + lane); }
    public int lanes() { return Math.max(1, data.get(4)); }
    public boolean fuseMode() { return data.get(5) == 1; }
    public int time() { return Math.max(1, data.get(6)); }
    public boolean fusionModule() { return data.get(7) == 1; }
    public int capacity() { return Math.max(1, data.get(8)) * 1000; }
    public int cost() { return data.get(9) * 100; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        boolean ok = index < PLAYER
            ? moveItemStackTo(stack, PLAYER, slots.size(), true)
            : moveItemStackTo(stack, UPGRADE, SLOTS, false)
            || moveItemStackTo(stack, 0, lanes(), false)
            || moveItemStackTo(stack, BOOK, BOOK + 1, false);
        if (!ok) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.canInteractWithBlock(pos, 4.0) && player.level().getBlockEntity(pos) instanceof ExtractorBlockEntity;
    }
}
