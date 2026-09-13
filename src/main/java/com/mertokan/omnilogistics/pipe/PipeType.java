package com.mertokan.omnilogistics.pipe;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.Locale;

/** What a conduit carries. Conduits only connect to conduits of the same type. */
public enum PipeType implements StringRepresentable {
    ITEM("item_pipe"), ENERGY("energy_cable"), FLUID("fluid_pipe");

    public static final Codec<PipeType> CODEC = StringRepresentable.fromEnum(PipeType::values);
    public final String id;

    PipeType(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (this) {
            case ITEM -> new PipeBlockEntity(pos, state);
            case ENERGY -> new EnergyCableBlockEntity(pos, state);
            case FLUID -> new FluidPipeBlockEntity(pos, state);
        };
    }

    /** Does the block at pos offer this type's capability on the given side? Drives the connection model. */
    public boolean hasCapability(Level level, BlockPos pos, Direction side) {
        return switch (this) {
            case ITEM -> level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side) != null;
            case ENERGY -> level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side) != null;
            case FLUID -> level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side) != null;
        };
    }
}
