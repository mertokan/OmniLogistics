package com.mertokan.omnilogistics.router;

import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.core.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Slots 0..7 cards, 8 buffer (take-out only), 9..10 upgrades, then player.
 *  data: 0 energy/1000, 1 range, 2 interval, 3 fluid/100, 4 capacity/1000. */
public class RouterMenu extends AbstractContainerMenu {
    private static final int CARDS = RouterBlockEntity.CARDS;
    private static final int BUFFER = CARDS, UPGRADE = CARDS + 1, PLAYER = UPGRADE + RouterBlockEntity.UPGRADES;
    public final BlockPos pos;
    private final ContainerData data;
    private @Nullable RouterBlockEntity be;

    public RouterMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), new ItemStackHandler(CARDS), new ItemStackHandler(1), new ItemStackHandler(RouterBlockEntity.UPGRADES), new SimpleContainerData(5));
    }

    public RouterMenu(int id, Inventory inv, RouterBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.cards, be.buffer, be.upgrades, new ContainerData() {
            @Override public int get(int i) { return i == 0 ? be.energy.getEnergyStored() / 1000 : i == 1 ? be.range() : i == 2 ? be.interval() : i == 3 ? be.fluid.getFluidAmount() / 100 : be.energy.getMaxEnergyStored() / 1000; }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return 5; }
        });
        this.be = be;
    }

    private RouterMenu(int id, Inventory inv, BlockPos pos, IItemHandler cards, IItemHandler buffer, IItemHandler upgrades, ContainerData data) {
        super(OmniLogistics.ROUTER_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        for (int i = 0; i < CARDS; i++) addSlot(new SlotItemHandler(cards, i, 8 + i * 18, 24) {
            @Override public boolean mayPlace(ItemStack s) { return s.getItem() instanceof LogisticsCardItem; }
        });
        addSlot(new SlotItemHandler(buffer, 0, 8, 62) {
            @Override public boolean mayPlace(ItemStack s) { return false; }
        });
        for (int i = 0; i < RouterBlockEntity.UPGRADES; i++) addSlot(new SlotItemHandler(upgrades, i, 44 + i * 18, 62) {
            @Override public boolean mayPlace(ItemStack s) { return Upgrades.isRouterUpgrade(s); }
            @Override public int getMaxStackSize() { return 3; }
        });
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 98 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 156));
        addDataSlots(data);
    }

    /** Right-click a card in its slot: filter cards open their GUI in place, mode-only cards flip EXTRACT / INSERT. */
    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < CARDS && button == 1 && type == ClickType.PICKUP && getCarried().isEmpty()
            && slots.get(slotId).getItem().getItem() instanceof LogisticsCardItem c && (c.kind.hasFilter || c.kind.hasMode)) {
            if (player instanceof ServerPlayer sp && be != null && !SlotCardHost.open(sp, be, pos, slotId)) {
                ItemStack card = slots.get(slotId).getItem();
                card.set(OmniLogistics.CARD_MODE.get(), card.getOrDefault(OmniLogistics.CARD_MODE.get(), 0) ^ 1);
                be.sync();
            }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    public int energy() { return data.get(0) * 1000; }
    public int fluidAmount() { return data.get(3) * 100; }
    public int capacity() { return Math.max(1, data.get(4)) * 1000; }
    public int range() { return data.get(1); }
    public int interval() { return Math.max(1, data.get(2)); }   // 0 = the data slot has not arrived yet

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        boolean ok = index < PLAYER
            ? moveItemStackTo(stack, PLAYER, slots.size(), true)
            : moveItemStackTo(stack, 0, CARDS, false) || moveItemStackTo(stack, UPGRADE, PLAYER, false);
        if (!ok) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.canInteractWithBlock(pos, 4.0) && (be == null || player.level().getBlockEntity(pos) == be);
    }
}
