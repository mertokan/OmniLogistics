package com.mertokan.omnilogistics.distributor;

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

import static com.mertokan.omnilogistics.distributor.DistributorBlockEntity.LANES;

/**
 * Two columns of six lanes: card slot + lane buffer (take-only, that is the manual un-jam) + the destination's icon.
 * Then the Parallel Upgrade slot and the SPLIT / CLUSTER button. data: per-lane status, then the mode and the lane count.
 */
public class DistributorMenu extends AbstractContainerMenu {
    public static final int UPGRADE_SLOT = LANES * 2, PLAYER = UPGRADE_SLOT + 1;
    public static final int ROW_Y = 20, ROW_H = 18, COL_W = 80, CARD_X = 8, LANE_X = 26, ICON_X = 45, PIP_X = 63;
    public static final int UPG_X = 152, UPG_Y = 129, MODE_X = 8, MODE_Y = 129, MODE_W = 86;
    public static final int BIND_X = 98, BIND_W = 50;
    public static final int INV_X = 8, INV_Y = 164, HEIGHT = 246;
    public final BlockPos pos;
    private final Player player;
    private final ContainerData data;

    public static int laneX(int i) { return (i < 6 ? CARD_X : CARD_X + COL_W); }
    public static int laneY(int i) { return ROW_Y + (i % 6) * ROW_H; }

    /** Client. */
    public DistributorMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), new ItemStackHandler(LANES), new ItemStackHandler(LANES),
            new ItemStackHandler(1), new SimpleContainerData(LANES + 2));
    }

    /** Server. */
    public DistributorMenu(int id, Inventory inv, DistributorBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.cards, be.lanes, be.upgrades, new ContainerData() {
            @Override public int get(int i) {
                return i < LANES ? be.status(i) : i == LANES ? (be.cluster() ? 1 : 0) : be.active();
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return LANES + 2; }
        });
    }

    private DistributorMenu(int id, Inventory inv, BlockPos pos, IItemHandler cards, IItemHandler lanes, IItemHandler upgrades, ContainerData data) {
        super(OmniLogistics.DISTRIBUTOR_MENU.get(), id);
        this.pos = pos;
        this.player = inv.player;
        this.data = data;
        for (int i = 0; i < LANES; i++) {
            int lane = i;
            addSlot(new SlotItemHandler(cards, i, laneX(i), laneY(i)) {
                @Override public boolean mayPlace(ItemStack s) { return lane < activeLanes() && LogisticsCardItem.isFilterCard(s); }
                @Override public boolean isActive() { return lane < activeLanes(); }
                @Override public int getMaxStackSize() { return 1; }
            });
        }
        for (int i = 0; i < LANES; i++) {
            int lane = i;
            addSlot(new SlotItemHandler(lanes, i, laneX(i) + (LANE_X - CARD_X), laneY(i)) {
                @Override public boolean mayPlace(ItemStack s) { return false; }
                @Override public boolean isActive() { return lane < activeLanes(); }
            });
        }
        addSlot(new SlotItemHandler(upgrades, 0, UPG_X, UPG_Y) {
            @Override public boolean mayPlace(ItemStack s) { return s.is(OmniLogistics.PARALLEL_UPGRADE.get()); }
            @Override public int getMaxStackSize() { return 2; }
        });
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, INV_X + c * 18, INV_Y + 58));
        addDataSlots(data);
    }

    public int status(int lane) { return data.get(lane); }
    public boolean cluster() { return data.get(LANES) == 1; }
    public int activeLanes() { return Math.max(DistributorBlockEntity.BASE_LANES, data.get(LANES + 1)); }

    public @Nullable DistributorBlockEntity distributor() {
        return player.level().getBlockEntity(pos) instanceof DistributorBlockEntity d ? d : null;
    }

    /** Right-click a card in its slot: edit its filter (and EXTRACT / INSERT) in place. */
    @Override
    public void clicked(int slotId, int button, ContainerInput type, Player player) {
        if (slotId >= 0 && slotId < LANES && button == 1 && type == ContainerInput.PICKUP && getCarried().isEmpty()
            && LogisticsCardItem.isFilterCard(slots.get(slotId).getItem())) {
            if (player instanceof ServerPlayer sp && distributor() != null) SlotCardHost.open(sp, distributor(), pos, slotId);
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
            : moveItemStackTo(stack, UPGRADE_SLOT, UPGRADE_SLOT + 1, false) || moveItemStackTo(stack, 0, activeLanes(), false);
        if (!ok) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return distributor() != null && player.isWithinBlockInteractionRange(pos, 4.0);
    }
}
