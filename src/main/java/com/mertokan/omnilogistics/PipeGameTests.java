package com.mertokan.omnilogistics;

import com.mertokan.omnilogistics.api.ComponentPredicateEngine;
import com.mertokan.omnilogistics.pipe.ConduitBlockEntity;
import com.mertokan.omnilogistics.pipe.PipeBlockEntity;
import com.mertokan.omnilogistics.pipe.PipeTier;
import com.mertokan.omnilogistics.pipe.PipeType;
import com.mertokan.omnilogistics.pipe.SmartPipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.List;

/** Real-world checks: run with `gradlew runGameTestServer`. Template = 3x3x3 of air (tools/gen_structure.py). */
public class PipeGameTests {
    private static final BlockPos A = new BlockPos(0, 1, 1), P = new BlockPos(1, 1, 1), B = new BlockPos(2, 1, 1);

    private static Holder<Enchantment> sharpness(GameTestHelper h) {
        return h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
    }

    private static PipeBlockEntity pipe(GameTestHelper h, BlockPos pos) {
        return (PipeBlockEntity) GameTests.be(h, pos);
    }

    /** Fresh pipe next to a chest shows a plain arm; wrench cycles the face to PULL and the model follows. */
    @GameTests.OmniTest(timeoutTicks = 100)
    public static void wrenchUpdatesModel(GameTestHelper h) {
        h.setBlock(A, Blocks.CHEST);
        h.setBlock(P, OmniLogistics.pipe(PipeType.ITEM, PipeTier.BASIC).get());
        h.runAfterDelay(2, () -> {
            h.assertBlockProperty(P, SmartPipeBlock.PROP.get(Direction.WEST), SmartPipeBlock.Connection.PLAIN);
            h.assertBlockProperty(P, SmartPipeBlock.PROP.get(Direction.EAST), SmartPipeBlock.Connection.NONE);
            pipe(h, P).wrenchFace(Direction.WEST, h.makeMockPlayer(GameType.SURVIVAL));
            h.assertBlockProperty(P, SmartPipeBlock.PROP.get(Direction.WEST), SmartPipeBlock.Connection.PULL);
            pipe(h, P).wrenchFace(Direction.WEST, h.makeMockPlayer(GameType.SURVIVAL));
            h.assertBlockProperty(P, SmartPipeBlock.PROP.get(Direction.WEST), SmartPipeBlock.Connection.PUSH);
            pipe(h, P).wrenchFace(Direction.WEST, h.makeMockPlayer(GameType.SURVIVAL));
            h.assertBlockProperty(P, SmartPipeBlock.PROP.get(Direction.WEST), SmartPipeBlock.Connection.NONE);
            h.succeed();
        });
    }

    /** PULL from chest A, default NORMAL toward chest B: only the enchanted sword travels, stone stays. */
    @GameTests.OmniTest(timeoutTicks = 200)
    public static void filterMovesEnchantedOnly(GameTestHelper h) {
        h.setBlock(A, Blocks.CHEST);
        h.setBlock(B, Blocks.CHEST);
        h.setBlock(P, OmniLogistics.pipe(PipeType.ITEM, PipeTier.BASIC).get());
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(sharpness(h), 1);
        ChestBlockEntity a = GameTests.be(h, A);
        a.setItem(0, new ItemStack(Items.STONE, 16));
        a.setItem(1, sword);
        h.runAfterDelay(1, () -> {
            ItemStack card = new ItemStack(OmniLogistics.CARD.get());
            card.set(OmniLogistics.CARD_FLAGS.get(), ComponentPredicateEngine.HAS_ENCHANTS);
            pipe(h, P).cards.setStackInSlot(0, card);
            byte[] modes = pipe(h, P).modes();
            modes[Direction.WEST.ordinal()] = (byte) ConduitBlockEntity.Mode.PULL.ordinal();
            pipe(h, P).setModes(modes);
        });
        h.succeedWhen(() -> {
            ChestBlockEntity b = GameTests.be(h, B);
            GameTests.check(h, b.getItem(0).is(Items.DIAMOND_SWORD), "sword should arrive in chest B");
            GameTests.check(h, GameTests.<ChestBlockEntity>be(h, A).getItem(0).is(Items.STONE), "stone must stay in chest A");
        });
    }

    /** Chain: chest A - pipe - pipe - chest B. Only the first pipe pulls; NORMAL sides pass items forward. */
    @GameTests.OmniTest(timeoutTicks = 300)
    public static void chainForwardsItems(GameTestHelper h) {
        BlockPos p1 = new BlockPos(1, 1, 0), p2 = new BlockPos(1, 1, 1), a = new BlockPos(0, 1, 0), b = new BlockPos(1, 1, 2);
        h.setBlock(a, Blocks.CHEST);
        h.setBlock(b, Blocks.CHEST);
        h.setBlock(p1, OmniLogistics.pipe(PipeType.ITEM, PipeTier.BASIC).get());
        h.setBlock(p2, OmniLogistics.pipe(PipeType.ITEM, PipeTier.BASIC).get());
        GameTests.<ChestBlockEntity>be(h, a).setItem(0, new ItemStack(Items.STONE, 8));
        h.runAfterDelay(1, () -> pipe(h, p1).wrenchFace(Direction.WEST, h.makeMockPlayer(GameType.SURVIVAL)));
        h.succeedWhen(() -> GameTests.check(h, GameTests.<ChestBlockEntity>be(h, b).getItem(0).getCount() == 8, "all 8 stone should reach chest B"));
    }

    /** Redstone mode ON_SIGNAL: nothing moves without a signal; a redstone block next to the pipe starts the flow. */
    @GameTests.OmniTest(timeoutTicks = 300)
    public static void redstoneGatesPipe(GameTestHelper h) {
        h.setBlock(A, Blocks.CHEST);
        h.setBlock(B, Blocks.CHEST);
        h.setBlock(P, OmniLogistics.pipe(PipeType.ITEM, PipeTier.BASIC).get());
        GameTests.<ChestBlockEntity>be(h, A).setItem(0, new ItemStack(Items.STONE, 4));
        h.runAfterDelay(1, () -> {
            pipe(h, P).wrenchFace(Direction.WEST, h.makeMockPlayer(GameType.SURVIVAL));
            pipe(h, P).setRedstone(ConduitBlockEntity.Redstone.ON_SIGNAL);
        });
        h.runAfterDelay(80, () -> {
            GameTests.check(h, GameTests.<ChestBlockEntity>be(h, B).isEmpty(), "no signal: chest B must stay empty");
            h.setBlock(new BlockPos(1, 2, 1), Blocks.REDSTONE_BLOCK);
        });
        h.succeedWhen(() -> GameTests.check(h, GameTests.<ChestBlockEntity>be(h, B).getItem(0).getCount() == 4, "with signal: all 4 stone reach chest B"));
    }

    /** Only the ends of a run open a GUI: a pipe touching a chest is an end, a lone pipe and the middle of a chain are not. */
    @GameTests.OmniTest(timeoutTicks = 100)
    public static void onlyEndsHaveAGui(GameTestHelper h) {
        BlockPos p0 = new BlockPos(1, 1, 0), p1 = new BlockPos(1, 1, 1), p2 = new BlockPos(1, 1, 2), chest = new BlockPos(0, 1, 2);
        h.setBlock(chest, Blocks.CHEST);
        for (BlockPos p : List.of(p0, p1, p2)) h.setBlock(p, OmniLogistics.pipe(PipeType.ITEM, PipeTier.BASIC).get());
        h.runAfterDelay(2, () -> {
            GameTests.check(h, !pipe(h, p0).isEnd(), "far end with nothing attached is a plain cable");
            GameTests.check(h, !pipe(h, p1).isEnd(), "middle of the chain is a plain cable");
            GameTests.check(h, pipe(h, p2).isEnd(), "pipe touching the chest is an end");
            pipe(h, p0).wrenchFace(Direction.NORTH, h.makeMockPlayer(GameType.SURVIVAL)); // an IN face with nothing there still counts
            GameTests.check(h, pipe(h, p0).isEnd(), "an IN face makes it configurable");
            pipe(h, p1).cards.setStackInSlot(0, new ItemStack(OmniLogistics.CARD.get()));
            GameTests.check(h, pipe(h, p1).isEnd(), "a middle pipe holding a card stays configurable");
            h.succeed();
        });
    }

    /** OFF side never receives. */
    @GameTests.OmniTest(timeoutTicks = 100)
    public static void offSideBlocksDelivery(GameTestHelper h) {
        h.setBlock(A, Blocks.CHEST);
        h.setBlock(B, Blocks.CHEST);
        h.setBlock(P, OmniLogistics.pipe(PipeType.ITEM, PipeTier.BASIC).get());
        GameTests.<ChestBlockEntity>be(h, A).setItem(0, new ItemStack(Items.STONE, 4));
        h.runAfterDelay(1, () -> {
            byte[] modes = pipe(h, P).modes();
            modes[Direction.WEST.ordinal()] = (byte) ConduitBlockEntity.Mode.PULL.ordinal();
            modes[Direction.EAST.ordinal()] = (byte) ConduitBlockEntity.Mode.NONE.ordinal();
            pipe(h, P).setModes(modes);
        });
        h.runAfterDelay(80, () -> {
            GameTests.check(h, GameTests.<ChestBlockEntity>be(h, B).isEmpty(), "chest B must stay empty behind an OFF side");
            h.succeed();
        });
    }
}
