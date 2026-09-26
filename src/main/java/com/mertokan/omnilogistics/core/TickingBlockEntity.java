package com.mertokan.omnilogistics.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public abstract class TickingBlockEntity extends BlockEntity {
    protected TickingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public abstract void serverTick();

    /** Move everything stored here into {@code out} (slots are emptied). Override in block entities with inventories. */
    public void collect(java.util.List<ItemStack> out) {}

    /** The block is going away (broken, replaced): whatever is left inside drops. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        dropContents();
    }

    /** Called when the block is broken: whatever is left inside drops. */
    public void dropContents() {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        collect(out);
        for (ItemStack s : out) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), s);
    }

    protected static void take(IItemHandler handler, java.util.List<ItemStack> out) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (s.isEmpty()) continue;
            out.add(s.copy());
            if (handler instanceof net.neoforged.neoforge.items.IItemHandlerModifiable m) m.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    // ponytail: direct lookup every op instead of BlockCapabilityCache; switch if the profiler shows it
    protected @Nullable IItemHandler neighbor(Direction d) {
        return level == null ? null : Caps.items(level, worldPosition.relative(d), d.getOpposite());
    }

    public void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider regs) {
        return saveWithoutMetadata(regs);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
