package com.mertokan.omnilogistics.miner;

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
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/** Slots: 0 card, 1 speed upgrade, 2..5 outputs, then the player. data: 0 energy/1000, 1 capacity/1000, 2 progress, 3 cycle time, 4 status, 5 cost/100. */
public class MinerMenu extends AbstractContainerMenu {
    public static final int CARD = 0, UPGRADE = 1, OUT = 2, PLAYER = 6;
    public static final int CARD_X = 30, CARD_Y = 20, UPG_X = 30, UPG_Y = 46;
    public static final int BAR_X = 8, BAR_Y = 20, BAR_W = 8, BAR_H = 44;
    public static final int ITEM_X = 66, ITEM_Y = 24, PROG_X = 64, PROG_Y = 46, PROG_W = 20, PROG_H = 5;
    public static final int[][] OUT_POS = {{116, 20}, {134, 20}, {116, 38}, {134, 38}};
    public static final int INV_Y = 84, HEIGHT = 166;
    public final BlockPos pos;
    private final Player player;
    private final ContainerData data;

    /** Client. */
    public MinerMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), new ItemStackHandler(1), new ItemStackHandler(VoidMinerBlockEntity.SLOTS), new SimpleContainerData(6));
    }

    /** Server. */
    public MinerMenu(int id, Inventory inv, VoidMinerBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.cards, be.items, new ContainerData() {
            @Override public int get(int i) {
                return switch (i) {
                    case 0 -> be.energy.getEnergyStored() / 1000;
                    case 1 -> be.energy.getMaxEnergyStored() / 1000;
                    case 2 -> be.progress;
                    case 3 -> be.interval();
                    case 4 -> be.status();
                    default -> be.cost() / 100;
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return 6; }
        });
    }

    private MinerMenu(int id, Inventory inv, BlockPos pos, IItemHandler cards, IItemHandler items, ContainerData data) {
        super(OmniLogistics.MINER_MENU.get(), id);
        this.pos = pos;
        this.player = inv.player;
        this.data = data;
        addSlot(new SlotItemHandler(cards, 0, CARD_X, CARD_Y) {
            @Override public boolean mayPlace(ItemStack s) { return LogisticsCardItem.isFilterCard(s); }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new SlotItemHandler(items, VoidMinerBlockEntity.UPGRADE, UPG_X, UPG_Y) {
            @Override public boolean mayPlace(ItemStack s) { return s.is(OmniLogistics.SPEED_UPGRADE.get()); }
            @Override public int getMaxStackSize() { return 3; }
        });
        for (int i = 0; i < VoidMinerBlockEntity.OUT_N; i++)
            addSlot(new SlotItemHandler(items, VoidMinerBlockEntity.OUT + i, OUT_POS[i][0], OUT_POS[i][1]) {
                @Override public boolean mayPlace(ItemStack s) { return false; }
            });
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, INV_Y + 58));
        addDataSlots(data);
    }

    public int energy() { return data.get(0) * 1000; }
    public int capacity() { return Math.max(1, data.get(1)) * 1000; }
    public int progress() { return data.get(2); }
    public int time() { return Math.max(1, data.get(3)); }
    public int status() { return data.get(4); }
    public int cost() { return data.get(5) * 100; }

    public @Nullable VoidMinerBlockEntity miner() {
        return player.level().getBlockEntity(pos) instanceof VoidMinerBlockEntity m ? m : null;
    }

    /** Right-click the card in its slot: edit its filter in place. */
    @Override
    public void clicked(int slotId, int button, ContainerInput type, Player player) {
        if (slotId == CARD && button == 1 && type == ContainerInput.PICKUP && getCarried().isEmpty() && LogisticsCardItem.isFilterCard(slots.get(CARD).getItem())) {
            if (player instanceof ServerPlayer sp && miner() != null) SlotCardHost.open(sp, miner(), pos, 0);
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
        boolean ok = index < PLAYER
            ? moveItemStackTo(stack, PLAYER, slots.size(), true)
            : moveItemStackTo(stack, CARD, CARD + 1, false) || moveItemStackTo(stack, UPGRADE, UPGRADE + 1, false);
        if (!ok) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return miner() != null && player.isWithinBlockInteractionRange(pos, 4.0);
    }
}
