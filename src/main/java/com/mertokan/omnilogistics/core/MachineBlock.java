package com.mertokan.omnilogistics.core;

import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.pipe.ConduitBlockEntity;
import com.mertokan.omnilogistics.pipe.SmartPipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Shared block behaviour: server ticker, open menu on right click, wrench handling, drop contents on break.
 * Wrench: face = cycle (pipes: the clicked arm; the conduit centre cycles the redstone mode or re-enables an OFF face). Sneak + wrench: dismantle.
 */
public abstract class MachineBlock extends BaseEntityBlock {
    protected MachineBlock(Properties props) {
        super(props);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : (l, p, s, be) -> { if (be instanceof TickingBlockEntity t) t.serverTick(); };
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(OmniLogistics.WRENCH.get()) && !stack.is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (player.isShiftKeyDown()) {
                dismantle((net.minecraft.server.level.ServerLevel) level, pos, state, be, player, stack);
            } else if (be instanceof ConduitBlockEntity c && SmartPipeBlock.faceFor(hit, pos) == null) {
                // centre cube: an OFF face facing you comes back on, otherwise cycle the redstone mode
                if (c.mode(hit.getDirection()) == ConduitBlockEntity.Mode.NONE) player.sendOverlayMessage(c.wrenchFace(hit.getDirection(), player));
                else player.sendOverlayMessage(c.cycleRedstone());
            } else if (be instanceof Wrenchable w) {
                Direction face = state.getBlock() instanceof SmartPipeBlock ? SmartPipeBlock.faceFor(hit, pos) : hit.getDirection();
                player.sendOverlayMessage(w.wrenchFace(face == null ? hit.getDirection() : face, player));
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Sneak + wrench: the block and everything inside go straight into the player's inventory (overflow drops). */
    private static void dismantle(net.minecraft.server.level.ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity be, Player player, ItemStack tool) {
        java.util.List<ItemStack> loot = new java.util.ArrayList<>(net.minecraft.world.level.block.Block.getDrops(state, level, pos, be, player, tool));
        if (be instanceof TickingBlockEntity t) t.collect(loot);
        level.removeBlock(pos, false);
        for (ItemStack s : loot) player.getInventory().placeItemBackInInventory(s);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MenuHost mh)) return InteractionResult.PASS;
        if (mh instanceof ConduitBlockEntity c) {
            // conduits: blocks can be placed against them, and only the ends of a run (touching an inventory / machine) have anything to configure
            if (player.getMainHandItem().getItem() instanceof BlockItem || !c.isEnd()) return InteractionResult.PASS;
        }
        if (!level.isClientSide()) player.openMenu(mh, mh::writeMenuData);
        return InteractionResult.SUCCESS;
    }

}
