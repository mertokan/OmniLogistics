package com.mertokan.omnilogistics.miner;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mertokan.omnilogistics.core.MachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** One block class per tier; the tier lives on the block, the behaviour in {@link VoidMinerBlockEntity}. */
public class VoidMinerBlock extends MachineBlock {
    public static final MapCodec<VoidMinerBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        propertiesCodec(), MinerTier.CODEC.fieldOf("tier").forGetter(b -> b.tier)).apply(i, VoidMinerBlock::new));
    public final MinerTier tier;

    public VoidMinerBlock(Properties props, MinerTier tier) {
        super(props);
        this.tier = tier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VoidMinerBlockEntity(pos, state);
    }

    /** Clients tick too: the particles being sucked into the beam. */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide()) return super.getTicker(level, state, type);
        return (l, p, s, be) -> { if (be instanceof VoidMinerBlockEntity m) m.clientTick(); };
    }
}
