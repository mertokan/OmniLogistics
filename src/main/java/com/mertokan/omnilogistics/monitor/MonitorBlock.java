package com.mertokan.omnilogistics.monitor;

import com.mojang.serialization.MapCodec;
import com.mertokan.omnilogistics.core.MachineBlock;
import com.mertokan.omnilogistics.router.LogisticsCardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A screen for someone else's machine. No GUI: right-click it with a Logistics Card that is bound to the block you want
 * to watch, right-click empty-handed to take the card back. It faces the player who places it.
 */
public class MonitorBlock extends MachineBlock {
    public static final MapCodec<MonitorBlock> CODEC = simpleCodec(MonitorBlock::new);

    public MonitorBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> b) {
        b.add(HorizontalDirectionalBlock.FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MonitorBlockEntity(pos, state);
    }

    /** A Logistics Card in hand goes in (the wrench and everything else falls through to {@link MachineBlock}). */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!LogisticsCardItem.isFilterCard(stack)) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MonitorBlockEntity be) {
            ItemStack old = be.card.getStackInSlot(0);
            if (!old.isEmpty()) player.getInventory().placeItemBackInInventory(old);
            be.card.setStackInSlot(0, stack.split(1));
            player.sendOverlayMessage(LogisticsCardItem.targetPos(be.card.getStackInSlot(0)) == null
                ? Component.translatable("msg.omnilogistics.monitor_unbound")
                : Component.translatable("msg.omnilogistics.monitor_watching", LogisticsCardItem.targetPos(be.card.getStackInSlot(0)).toShortString()));
        }
        return InteractionResult.SUCCESS;
    }

    /** Empty hand: the card comes back out. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MonitorBlockEntity be) {
            ItemStack c = be.card.extractItem(0, 1, false);
            if (!c.isEmpty()) player.getInventory().placeItemBackInInventory(c);
        }
        return InteractionResult.SUCCESS;
    }
}
