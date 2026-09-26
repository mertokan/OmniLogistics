package com.mertokan.omnilogistics;

import com.mertokan.omnilogistics.api.ComponentPredicateEngine;
import com.mertokan.omnilogistics.miner.MinerTier;
import com.mertokan.omnilogistics.miner.VoidMinerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Void miner end-to-end: loot table -> outputs -> neighbour, card filter, redstone pause. */
public class MinerGameTests {
    private static final BlockPos P = new BlockPos(1, 1, 1), C = new BlockPos(2, 1, 1);

    private static VoidMinerBlockEntity miner(GameTestHelper h, int speedUpgrades) {
        h.setBlock(P, OmniLogistics.MINERS.get(MinerTier.BASIC).get());
        VoidMinerBlockEntity be = GameTests.be(h, P);
        be.energy.receiveEnergy(be.energy.getMaxEnergyStored(), false);
        be.setInterval(10);   // the clock is typed in now, so the tests do not wait out a whole tier cycle
        if (speedUpgrades > 0) be.items.setStackInSlot(VoidMinerBlockEntity.UPGRADE, new ItemStack(OmniLogistics.SPEED_UPGRADE.get(), speedUpgrades));
        return be;
    }

    /** Full buffer, chest next to it: one cycle later something from the loot table sits in the chest and FE was spent. */
    @GameTests.OmniTest(timeoutTicks = 400)
    public static void minerFillsTheChestNextToIt(GameTestHelper h) {
        h.setBlock(C, Blocks.CHEST);
        VoidMinerBlockEntity be = miner(h, 0);
        int full = be.energy.getEnergyStored();
        h.succeedWhen(() -> {
            ChestBlockEntity chest = GameTests.be(h, C);
            GameTests.check(h, !chest.isEmpty(), "the chest should receive mined items");
            GameTests.check(h, be.energy.getEnergyStored() < full, "mining must cost FE");
        });
    }

    /** Card with MATCH_ITEM cobblestone in the slot: everything that comes out is cobblestone. */
    @GameTests.OmniTest(timeoutTicks = 600)
    public static void minerKeepsOnlyWhatTheCardAllows(GameTestHelper h) {
        VoidMinerBlockEntity be = miner(h, 3);
        ItemStack card = new ItemStack(OmniLogistics.CARD.get());
        com.mertokan.omnilogistics.router.CardConfig.setFilter(card, new ItemStack(Items.COBBLESTONE));
        card.set(OmniLogistics.CARD_FLAGS.get(), ComponentPredicateEngine.MATCH_ITEM);
        be.cards.setStackInSlot(0, card);
        h.succeedWhen(() -> {
            be.energy.receiveEnergy(be.energy.getMaxEnergyStored(), false);   // an x8 cycle drains the buffer in three
            int n = 0;
            for (int i = VoidMinerBlockEntity.OUT; i < VoidMinerBlockEntity.OUT + VoidMinerBlockEntity.OUT_N; i++) {
                ItemStack s = be.items.getStackInSlot(i);
                if (s.isEmpty()) continue;
                GameTests.check(h, s.is(Items.COBBLESTONE), "only cobblestone may pass the card, got " + s);
                n += s.getCount();
            }
            GameTests.check(h, n > 0, "nothing mined yet");
        });
    }

    /** Speed Upgrades no longer touch the clock: one cycle yields x8 items and costs x8 FE. */
    @GameTests.OmniTest(timeoutTicks = 300)
    public static void speedUpgradesRaiseTheYieldNotTheClock(GameTestHelper h) {
        VoidMinerBlockEntity be = miner(h, 3);
        ItemStack card = new ItemStack(OmniLogistics.CARD.get());
        com.mertokan.omnilogistics.router.CardConfig.setFilter(card, new ItemStack(Items.COBBLESTONE));
        card.set(OmniLogistics.CARD_FLAGS.get(), ComponentPredicateEngine.MATCH_ITEM);
        be.cards.setStackInSlot(0, card);
        GameTests.check(h, be.interval() == 10, "the typed clock wins over the tier and the upgrades");
        GameTests.check(h, be.mult() == 8, "three speed upgrades multiply a cycle by 8");
        GameTests.check(h, be.cost() == MinerTier.BASIC.cost() * 8, "and the FE per cycle with it, was " + be.cost());
        h.succeedWhen(() -> {
            be.energy.receiveEnergy(be.energy.getMaxEnergyStored(), false);   // an x8 cycle drains the buffer in three
            int n = 0;
            for (int i = VoidMinerBlockEntity.OUT; i < VoidMinerBlockEntity.OUT + VoidMinerBlockEntity.OUT_N; i++)
                n += be.items.getStackInSlot(i).getCount();
            GameTests.check(h, n >= 8, "a x8 cycle must yield at least 8 items, got " + n);
        });
    }

    /** Redstone block next to it: no FE spent, status says so. */
    @GameTests.OmniTest(timeoutTicks = 300)
    public static void minerPausesOnRedstone(GameTestHelper h) {
        h.setBlock(C, Blocks.REDSTONE_BLOCK);
        VoidMinerBlockEntity be = miner(h, 3);
        int full = be.energy.getEnergyStored();
        h.runAfterDelay(120, () -> {
            GameTests.check(h, be.energy.getEnergyStored() == full, "a powered miner must not spend FE");
            GameTests.check(h, be.status() == VoidMinerBlockEntity.REDSTONE, "status should be REDSTONE, was " + be.status());
            h.succeed();
        });
    }
}
