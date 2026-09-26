package com.mertokan.omnilogistics.pipe;

import com.mertokan.omnilogistics.core.Caps;
import com.mertokan.omnilogistics.OmniLogistics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/** FE conduit. Buffer = 8 ticks of throughput; moves every tick regardless of tier interval. */
public class EnergyCableBlockEntity extends ConduitBlockEntity {
    private final int rate;
    private final EnergyStorage buffer;
    private int lastStored = -1;

    public EnergyCableBlockEntity(BlockPos pos, BlockState state) {
        super(OmniLogistics.ENERGY_CABLE_BE.get(), pos, state);
        rate = ((SmartPipeBlock) state.getBlock()).tier.energy();
        buffer = new EnergyStorage(rate * 8, rate, rate);
    }

    public @Nullable IEnergyStorage handlerFor(@Nullable Direction side) {
        if (side != null && mode(side) == Mode.NONE) return null;
        return new IEnergyStorage() {
            @Override public int receiveEnergy(int max, boolean simulate) {
                if (!accepts(side)) return 0;
                int got = buffer.receiveEnergy(max, simulate);
                if (!simulate && got > 0) lastFrom = side;
                return got;
            }
            @Override public int extractEnergy(int max, boolean simulate) { return 0; }
            @Override public int getEnergyStored() { return buffer.getEnergyStored(); }
            @Override public int getMaxEnergyStored() { return buffer.getMaxEnergyStored(); }
            @Override public boolean canExtract() { return false; }
            @Override public boolean canReceive() { return accepts(side); }
        };
    }

    @Override protected int interval() { return 1; }
    public int stored() { return buffer.getEnergyStored(); }
    public int capacity() { return buffer.getMaxEnergyStored(); }

    @Override
    public void serverTick() {
        if (buffer.getEnergyStored() != lastStored) { // EnergyStorage has no change callback
            lastStored = buffer.getEnergyStored();
            markDirty();
        }
        super.serverTick();
    }
    @Override public net.minecraft.network.chat.Component bufferInfo() { return net.minecraft.network.chat.Component.translatable("jade.omnilogistics.buffer_energy", buffer.getEnergyStored(), buffer.getMaxEnergyStored()); }
    @Override protected boolean bufferEmpty() { return buffer.getEnergyStored() == 0; }
    @Override protected boolean bufferEmptyOrPartial() { return buffer.getEnergyStored() < buffer.getMaxEnergyStored(); }

    private @Nullable IEnergyStorage cap(Direction d) {
        return Caps.energy(level, worldPosition.relative(d), d.getOpposite());
    }

    @Override
    protected boolean pull(Direction from) {
        IEnergyStorage src = cap(from);
        if (src == null || !src.canExtract()) return false;
        int want = Math.min(rate, buffer.getMaxEnergyStored() - buffer.getEnergyStored());
        int e = src.extractEnergy(want, true);
        if (e <= 0) return false;
        buffer.receiveEnergy(src.extractEnergy(e, false), false);
        lastFrom = from;
        return true;
    }

    @Override
    protected boolean push(Direction to) {
        IEnergyStorage dst = cap(to);
        if (dst == null || !dst.canReceive()) return false;
        int e = dst.receiveEnergy(buffer.extractEnergy(rate, true), true);
        if (e <= 0) return false;
        dst.receiveEnergy(buffer.extractEnergy(e, false), false);
        return true;
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput tag) {
        super.saveAdditional(tag);
        buffer.serialize(tag.child("Energy"));
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput tag) {
        super.loadAdditional(tag);
        buffer.deserialize(tag.childOrEmpty("Energy"));
    }
}
