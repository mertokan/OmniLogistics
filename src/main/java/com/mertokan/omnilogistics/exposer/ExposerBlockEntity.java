package com.mertokan.omnilogistics.exposer;

import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.api.ComponentPredicateEngine;
import com.mertokan.omnilogistics.core.FilterHost;
import com.mertokan.omnilogistics.core.FilterLayout;
import com.mertokan.omnilogistics.core.FilterMenu;
import com.mertokan.omnilogistics.api.FilterSpec;
import com.mertokan.omnilogistics.core.MenuHost;
import com.mertokan.omnilogistics.core.TickingBlockEntity;
import com.mertokan.omnilogistics.core.Wrenchable;
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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Presents a *filtered view* of the neighbour on {@code target} as an item handler on every other side.
 * AE2 storage bus / RS external storage / pipes see only the items that pass the component filter.
 */
public class ExposerBlockEntity extends TickingBlockEntity implements FilterHost, MenuHost, Wrenchable, com.mertokan.omnilogistics.core.InfoLines {
    private @Nullable Direction target;
    private FilterSpec spec = new FilterSpec(List.of(), ComponentPredicateEngine.HAS_MOD_COMPONENTS, List.of(), List.of());

    private final IItemHandler view = new IItemHandler() {
        private @Nullable IItemHandler t() { return target == null ? null : neighbor(target); }

        @Override public int getSlots() { IItemHandler t = t(); return t == null ? 0 : t.getSlots(); }
        @Override public ItemStack getStackInSlot(int i) {
            IItemHandler t = t();
            if (t == null) return ItemStack.EMPTY;
            ItemStack s = t.getStackInSlot(i);
            return spec.test(s) ? s : ItemStack.EMPTY;
        }
        @Override public ItemStack insertItem(int i, ItemStack s, boolean sim) {
            IItemHandler t = t();
            return t == null || !spec.test(s) ? s : t.insertItem(i, s, sim);
        }
        @Override public ItemStack extractItem(int i, int n, boolean sim) {
            IItemHandler t = t();
            return t == null || !spec.test(t.getStackInSlot(i)) ? ItemStack.EMPTY : t.extractItem(i, n, sim);
        }
        @Override public int getSlotLimit(int i) { IItemHandler t = t(); return t == null ? 0 : t.getSlotLimit(i); }
        @Override public boolean isItemValid(int i, ItemStack s) { IItemHandler t = t(); return t != null && spec.test(s) && t.isItemValid(i, s); }
    };

    public ExposerBlockEntity(BlockPos pos, BlockState state) {
        super(OmniLogistics.EXPOSER_BE.get(), pos, state);
    }

    /** The target side returns null so the wrapped inventory can't be reached through itself. */
    public @Nullable IItemHandler viewFor(@Nullable Direction side) {
        return side != null && side == target ? null : view;
    }

    @Override
    public void serverTick() {}

    private void setTarget(@Nullable Direction d) {
        target = d;
        invalidateCapabilities(); // AE2/RS drop their cached handler and re-query
        sync();
    }

    // ---- FilterHost / Wrenchable ------------------------------------------------

    @Override public FilterLayout layout() { return FilterLayout.EXPOSER; }
    @Override public FilterSpec spec() { return spec; }

    @Override
    public byte[] modes() {
        byte[] b = new byte[6];
        if (target != null) b[target.ordinal()] = 1;
        return b;
    }

    @Override
    public void setFilter(int index, ItemStack stack) {
        spec = spec.withRef(index, stack);
        sync();
    }

    /** Radio semantics: the side that is newly 1 wins; none set = no target. */
    @Override
    public void applyConfig(byte[] modes, int flags, List<String> tags, List<String> components,
                            List<com.mertokan.omnilogistics.api.NbtRule> rules) {
        Direction next = null;
        for (int i = 0; i < 6; i++)
            if (modes[i] == 1 && (target == null || i != target.ordinal())) { next = Direction.values()[i]; break; }
        if (next == null)
            for (int i = 0; i < 6; i++) if (modes[i] == 1) next = Direction.values()[i];
        spec = spec.withConfig(flags, tags, components, rules);
        setTarget(next);
    }

    @Override
    public Component wrenchFace(Direction face, Player player) {
        setTarget(face == target ? null : face);
        return Component.translatable("msg.omnilogistics.exposer_target", target == null ? "-" : target.getName());
    }

    // ---- MenuHost ---------------------------------------------------------------

    @Override
    public java.util.List<Component> infoLines() {
        IItemHandler t = target == null ? null : neighbor(target);
        return java.util.List.of(Component.translatable("jade.omnilogistics.exposer", target == null ? "-" : target.getName(), t == null ? 0 : t.getSlots()));
    }

    @Override public Component getDisplayName() { return Component.translatable("block.omnilogistics.inventory_exposer"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new FilterMenu(id, inv, this, worldPosition, -1); }
    @Override public void writeMenuData(RegistryFriendlyByteBuf buf) { FilterMenu.writeData(buf, this, worldPosition, -1); }

    // ---- NBT --------------------------------------------------------------------

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput tag) {
        super.saveAdditional(tag);
        tag.putInt("Target", target == null ? -1 : target.ordinal());
        spec.save(tag.child("Filter"));
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput tag) {
        super.loadAdditional(tag);
        int t = tag.getIntOr("Target", 0);
        target = t >= 0 && t < 6 ? Direction.values()[t] : null;
        spec = FilterSpec.load(tag.childOrEmpty("Filter"));
    }
}
