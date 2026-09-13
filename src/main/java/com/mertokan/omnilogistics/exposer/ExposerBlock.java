package com.mertokan.omnilogistics.exposer;

import com.mojang.serialization.MapCodec;
import com.mertokan.omnilogistics.core.MachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ExposerBlock extends MachineBlock {
    public static final MapCodec<ExposerBlock> CODEC = simpleCodec(ExposerBlock::new);

    public ExposerBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExposerBlockEntity(pos, state);
    }
}
