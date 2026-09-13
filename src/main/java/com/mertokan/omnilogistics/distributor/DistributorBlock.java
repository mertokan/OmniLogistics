package com.mertokan.omnilogistics.distributor;

import com.mojang.serialization.MapCodec;
import com.mertokan.omnilogistics.core.MachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Everything (ticker, GUI, wrench, drop on break) comes from {@link MachineBlock}. */
public class DistributorBlock extends MachineBlock {
    public static final MapCodec<DistributorBlock> CODEC = simpleCodec(DistributorBlock::new);

    public DistributorBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DistributorBlockEntity(pos, state);
    }
}
