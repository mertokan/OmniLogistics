package com.mertokan.omnilogistics;

import com.mertokan.omnilogistics.api.ComponentPredicateEngine;
import com.mertokan.omnilogistics.miner.MinerTier;
import com.mertokan.omnilogistics.miner.VoidMinerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Void miner end-to-end: loot table -> outputs -> neighbour, card filter, redstone pause. */
@GameTestHolder(OmniLogistics.MODID)
@PrefixGameTestTemplate(false)
public class MinerGameTests {
    private static final BlockPos P = new BlockPos(1, 1, 1), C = new BlockPos(2, 1, 1);

    private static VoidMinerBlockEntity miner(GameTestHelper h, int speedUpgrades) {
        h.setBlock(P, OmniLogistics.MINERS.get(MinerTier.BASIC).get());
        VoidMinerBlockEntity be = h.getBlockEntity(P);
        be.energy.receiveEnergy(be.energy.getMaxEnergyStored(), false);
        be.setInterval(10);   // the clock is typed in now, so the tests do not wait out a whole tier cycle
        if (speedUpgrades > 0) be.items.setStackInSlot(VoidMinerBlockEntity.UPGRADE, new ItemStack(OmniLogistics.SPEED_UPGRADE.get(), speedUpgrades));
        return be;
    }

    /** Full buffer, chest next to it: one cycle later something from the loot table sits in the chest and FE was spent. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void minerFillsTheChestNextToIt(GameTestHelper h) {
        h.setBlock(C, Blocks.CHEST);
        VoidMinerBlockEntity be = miner(h, 0);
        int full = be.energy.getEnergyStored();
        h.succeedWhen(() -> {
            ChestBlockEntity chest = h.getBlockEntity(C);
            h.assertTrue(!chest.isEmpty(), "the chest should receive mined items");
            h.assertTrue(be.energy.getEnergyStored() < full, "mining must cost FE");
        });
    }

    /** Card with MATCH_ITEM cobblestone in the slot: everything that comes out is cobblestone. */
    @GameTest(template = "empty", timeoutTicks = 600)
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
                h.assertTrue(s.is(Items.COBBLESTONE), "only cobblestone may pass the card, got " + s);
                n += s.getCount();
            }
            h.assertTrue(n > 0, "nothing mined yet");
        });
    }

    /** Speed Upgrades no longer touch the clock: one cycle yields x8 items and costs x8 FE. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void speedUpgradesRaiseTheYieldNotTheClock(GameTestHelper h) {
        VoidMinerBlockEntity be = miner(h, 3);
        ItemStack card = new ItemStack(OmniLogistics.CARD.get());
        com.mertokan.omnilogistics.router.CardConfig.setFilter(card, new ItemStack(Items.COBBLESTONE));
        card.set(OmniLogistics.CARD_FLAGS.get(), ComponentPredicateEngine.MATCH_ITEM);
        be.cards.setStackInSlot(0, card);
        h.assertTrue(be.interval() == 10, "the typed clock wins over the tier and the upgrades");
        h.assertTrue(be.mult() == 8, "three speed upgrades multiply a cycle by 8");
        h.assertTrue(be.cost() == MinerTier.BASIC.cost() * 8, "and the FE per cycle with it, was " + be.cost());
        h.succeedWhen(() -> {
            be.energy.receiveEnergy(be.energy.getMaxEnergyStored(), false);   // an x8 cycle drains the buffer in three
            int n = 0;
            for (int i = VoidMinerBlockEntity.OUT; i < VoidMinerBlockEntity.OUT + VoidMinerBlockEntity.OUT_N; i++)
                n += be.items.getStackInSlot(i).getCount();
            h.assertTrue(n >= 8, "a x8 cycle must yield at least 8 items, got " + n);
        });
    }

    /** Redstone block next to it: no FE spent, status says so. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void minerPausesOnRedstone(GameTestHelper h) {
        h.setBlock(C, Blocks.REDSTONE_BLOCK);
        VoidMinerBlockEntity be = miner(h, 3);
        int full = be.energy.getEnergyStored();
        h.runAfterDelay(120, () -> {
            h.assertTrue(be.energy.getEnergyStored() == full, "a powered miner must not spend FE");
            h.assertTrue(be.status() == VoidMinerBlockEntity.REDSTONE, "status should be REDSTONE, was " + be.status());
            h.succeed();
        });
    }
}
