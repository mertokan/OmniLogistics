package com.mertokan.omnilogistics;

import com.mertokan.omnilogistics.api.ComponentPredicateEngine;
import com.mertokan.omnilogistics.api.FilterSpec;
import com.mertokan.omnilogistics.distributor.DistributorBlockEntity;
import com.mertokan.omnilogistics.exposer.ExposerBlockEntity;
import com.mertokan.omnilogistics.extractor.ExtractorBlockEntity;
import com.mertokan.omnilogistics.router.CardConfig;
import com.mertokan.omnilogistics.router.CardKind;
import com.mertokan.omnilogistics.router.LogisticsCardItem;
import com.mertokan.omnilogistics.router.RouterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Extractor, exposer, router and Batch Distributor end-to-end. Run with `gradlew runGameTestServer`. */
@GameTestHolder(OmniLogistics.MODID)
@PrefixGameTestTemplate(false)
public class MachineGameTests {
    private static final BlockPos A = new BlockPos(0, 1, 1), P = new BlockPos(1, 1, 1), B = new BlockPos(2, 1, 1);

    private static Holder<Enchantment> sharpness(GameTestHelper h) {
        return h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
    }

    private static ExtractorBlockEntity extractor(GameTestHelper h) {
        h.setBlock(P, OmniLogistics.EXTRACTOR.get());
        ExtractorBlockEntity be = h.getBlockEntity(P);
        be.energy.receiveEnergy(200_000, false);
        return be;
    }

    private static ItemStack card(GameTestHelper h, CardKind kind, BlockPos target, int mode) {
        ItemStack c = new ItemStack(OmniLogistics.CARDS.get(kind).get());
        if (target != null) {
            com.mertokan.omnilogistics.router.LogisticsCardItem.bind(c, h.getLevel(), h.absolutePos(target), Direction.UP);
        }
        c.set(OmniLogistics.CARD_MODE.get(), mode);
        return c;
    }

    /** The same card, bound to one particular face - a furnace wants its ore on top and its fuel on the side. */
    private static ItemStack card(GameTestHelper h, CardKind kind, BlockPos target, Direction side, int mode) {
        ItemStack c = card(h, kind, target, mode);
        c.set(OmniLogistics.CARD_SIDE.get(), side);
        return c;
    }

    /** A Logistics Card bound to {@code target} (top face), in INSERT mode, filtered to exactly one item. */
    private static ItemStack laneCard(GameTestHelper h, BlockPos target, Item filter) {
        ItemStack c = card(h, CardKind.ITEM, target, LogisticsCardItem.INSERT);
        CardConfig.setFilter(c, new ItemStack(filter));
        c.set(OmniLogistics.CARD_FLAGS.get(), ComponentPredicateEngine.MATCH_ITEM);
        return c;
    }

    private static RouterBlockEntity router(GameTestHelper h) {
        h.setBlock(P, OmniLogistics.ROUTER.get());
        return h.getBlockEntity(P);
    }

    // ---- extractor -------------------------------------------------------------------------

    /** The base machine: enchantments off the gear and onto a book, gear cleaned in its own lane. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void extractorMovesEnchantsToBook(GameTestHelper h) {
        ExtractorBlockEntity be = extractor(h);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(sharpness(h), 3);
        be.items.setStackInSlot(0, sword);
        be.items.setStackInSlot(ExtractorBlockEntity.BOOK, new ItemStack(Items.BOOK, 2));
        h.succeedWhen(() -> {
            ItemStack out = be.items.getStackInSlot(ExtractorBlockEntity.EXTRA);
            h.assertTrue(out.is(Items.ENCHANTED_BOOK), "an enchanted book should appear in the output");
            h.assertTrue(out.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(sharpness(h)) == 3, "book must carry Sharpness III");
            h.assertTrue(!ComponentPredicateEngine.hasEnchantments(be.items.getStackInSlot(0)), "sword must be clean and still in its lane");
            h.assertTrue(be.items.getStackInSlot(ExtractorBlockEntity.BOOK).getCount() == 1, "exactly one book consumed");
        });
    }

    /** Component Module: item stacks embedded in gear (Apotheosis-style sockets) come out as items. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void componentModuleExtractsEmbeddedItems(GameTestHelper h) {
        ExtractorBlockEntity be = extractor(h);
        be.items.setStackInSlot(ExtractorBlockEntity.UPGRADE, new ItemStack(OmniLogistics.GEM_MODULE.get()));
        ItemStack chest = new ItemStack(Items.NETHERITE_CHESTPLATE);
        chest.set(OmniLogistics.EMBEDDED_ITEMS.get(), List.of(new ItemStack(Items.EMERALD), new ItemStack(Items.DIAMOND, 2)));
        be.items.setStackInSlot(0, chest);
        h.succeedWhen(() -> {
            h.assertTrue(be.items.getStackInSlot(ExtractorBlockEntity.EXTRA).is(Items.EMERALD), "emerald should be extracted");
            h.assertTrue(be.items.getStackInSlot(ExtractorBlockEntity.EXTRA + 1).getCount() == 2, "two diamonds should be extracted");
            h.assertTrue(!be.items.getStackInSlot(0).has(OmniLogistics.EMBEDDED_ITEMS.get()), "socket component removed from the gear");
        });
    }

    /** Fusion Module, FUSE mode: the donor book merges onto the gear instead of being stripped off it. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void fusionMergesBookOntoGear(GameTestHelper h) {
        ExtractorBlockEntity be = extractor(h);
        be.items.setStackInSlot(ExtractorBlockEntity.UPGRADE, new ItemStack(OmniLogistics.FUSION_MODULE.get()));
        be.toggleMode();
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        m.set(sharpness(h), 4);
        book.set(DataComponents.STORED_ENCHANTMENTS, m.toImmutable());
        be.items.setStackInSlot(0, new ItemStack(Items.DIAMOND_SWORD));
        be.items.setStackInSlot(ExtractorBlockEntity.BOOK, book);
        h.succeedWhen(() -> {
            h.assertTrue(be.items.getStackInSlot(0).getEnchantments().getLevel(sharpness(h)) == 4, "sword must gain Sharpness IV");
            h.assertTrue(be.items.getStackInSlot(ExtractorBlockEntity.BOOK).isEmpty(), "donor book consumed");
        });
    }

    /** Upgrades: Parallel unlocks lanes, Speed makes an operation cheaper (never faster), and every lane really processes. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void parallelUpgradeUnlocksLanes(GameTestHelper h) {
        ExtractorBlockEntity be = extractor(h);
        h.assertTrue(be.lanes() == 1, "base machine has one lane");
        be.items.setStackInSlot(ExtractorBlockEntity.UPGRADE, new ItemStack(OmniLogistics.PARALLEL_UPGRADE.get(), 2));
        be.items.setStackInSlot(ExtractorBlockEntity.UPGRADE + 1, new ItemStack(OmniLogistics.SPEED_UPGRADE.get(), 3));
        h.assertTrue(be.lanes() == 3, "two parallel upgrades give three lanes");
        be.setInterval(12);
        h.assertTrue(be.interval() == 12, "the clock is whatever was typed, upgrades do not touch it");
        h.assertTrue(be.cost() == com.mertokan.omnilogistics.core.OmniConfig.EXTRACTOR_COST.get() / 8,
            "three speed upgrades make an operation 8x cheaper, was " + be.cost());
        for (int lane = 0; lane < 3; lane++) {
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.enchant(sharpness(h), 1);
            be.items.setStackInSlot(lane, sword);
        }
        be.items.setStackInSlot(ExtractorBlockEntity.BOOK, new ItemStack(Items.BOOK, 3));
        h.succeedWhen(() -> {
            for (int lane = 0; lane < 3; lane++)
                h.assertTrue(!ComponentPredicateEngine.hasEnchantments(be.items.getStackInSlot(lane)), "lane " + lane + " must be processed");
        });
    }

    /** Speed Upgrades in the router raise throughput, not the pace: x4 means four card actions per cycle. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void speedUpgradesRaiseWhatOneCycleMoves(GameTestHelper h) {
        h.setBlock(A, Blocks.CHEST);
        h.setBlock(B, Blocks.CHEST);
        ChestBlockEntity src = h.getBlockEntity(A);
        for (int i = 0; i < src.getContainerSize(); i++) src.setItem(i, new ItemStack(Items.STONE, 64));   // 1728 items
        RouterBlockEntity be = router(h);
        be.upgrades.setStackInSlot(0, new ItemStack(OmniLogistics.SPEED_UPGRADE.get(), 2));
        be.setInterval(1);
        be.cards.setStackInSlot(0, card(h, CardKind.ITEM, A, LogisticsCardItem.EXTRACT));
        be.cards.setStackInSlot(1, card(h, CardKind.ITEM, B, LogisticsCardItem.INSERT));
        h.assertTrue(be.interval() == 1 && be.mult() == 4, "the upgrades must not touch the clock, only the actions per cycle");
        h.runAfterDelay(80, () -> {
            int n = 0;
            ChestBlockEntity dst = h.getBlockEntity(B);
            for (int i = 0; i < dst.getContainerSize(); i++) n += dst.getItem(i).getCount();
            h.assertTrue(n > 640, "x4 must beat the 8 items a tick a plain router manages, moved " + n);
            h.succeed();
        });
    }

    /** No upgrade in the way: any tick value 1..200 sticks, and out-of-range values are clamped, not dropped. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void theClockIsFreeAndTheUpgradesRaiseAmounts(GameTestHelper h) {
        h.setBlock(P, OmniLogistics.ROUTER.get());
        RouterBlockEntity be = h.getBlockEntity(P);
        h.assertTrue(be.interval() == RouterBlockEntity.DEFAULT_INTERVAL, "a fresh router runs at its default");
        h.assertTrue(be.speedUpgrades() == 0, "with no upgrades installed");
        be.setInterval(1);
        h.assertTrue(be.interval() == 1, "1 tick needs no upgrade, was " + be.interval());
        be.setInterval(200);
        h.assertTrue(be.interval() == 200, "200 ticks is allowed, was " + be.interval());
        be.setInterval(9999);
        h.assertTrue(be.interval() == 200, "out of range clamps to the maximum, was " + be.interval());
        be.setInterval(0);
        h.assertTrue(be.interval() == 1, "and to the minimum, was " + be.interval());
        h.succeed();
    }

    /** The card tier upgrade - the thing the hand ritual and the crafting table both run - keeps everything the card
     *  carried, and refuses a core that is not the next step up. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cardUpgradeKeepsTheWholeCard(GameTestHelper h) {
        ItemStack card = new ItemStack(OmniLogistics.CARD.get());
        CardConfig.setFilter(card, new ItemStack(Items.DIAMOND));
        card.set(OmniLogistics.CARD_FLAGS.get(), ComponentPredicateEngine.MATCH_ITEM);
        card.set(OmniLogistics.CARD_MODE.get(), LogisticsCardItem.INSERT);
        ItemStack up = com.mertokan.omnilogistics.router.CardUpgradeRecipe.upgraded(card, new ItemStack(Items.GOLD_INGOT));
        h.assertTrue(!up.isEmpty(), "gold must upgrade a plain card");
        h.assertTrue(LogisticsCardItem.capacity(up) == 4, "to the 4-reference card, got " + LogisticsCardItem.capacity(up));
        h.assertTrue(LogisticsCardItem.spec(up).test(new ItemStack(Items.DIAMOND)), "and it keeps the filter");
        h.assertTrue(!LogisticsCardItem.extractMode(up), "and the mode");
        h.assertTrue(com.mertokan.omnilogistics.router.CardUpgradeRecipe.upgraded(card, new ItemStack(Items.NETHERITE_INGOT)).isEmpty(),
            "skipping a tier must not work");
        h.assertTrue(com.mertokan.omnilogistics.router.CardUpgradeRecipe.upgraded(up, new ItemStack(Items.DIAMOND)).getItem()
            == OmniLogistics.MULTI_CARDS.get(16).get(), "and the next step up is the 16-reference card");
        h.succeed();
    }

    /** Auto input: the card is bound to the furnace's BOTTOM face, which only ever gives things out, and the ore still
     *  ends up in the input slot - our blocks try every face and use the one that works. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void autoFaceFindsTheWorkingSide(GameTestHelper h) {
        h.setBlock(A, Blocks.FURNACE);
        RouterBlockEntity be = router(h);
        be.setInterval(1);
        be.buffer.setStackInSlot(0, new ItemStack(Items.RAW_IRON, 8));
        be.cards.setStackInSlot(0, card(h, CardKind.ITEM, A, Direction.DOWN, LogisticsCardItem.INSERT));
        h.runAfterDelay(20, () -> {
            FurnaceBlockEntity f = h.getBlockEntity(A);
            h.assertTrue(f.getItem(0).is(Items.RAW_IRON), "the ore must reach the input slot even though the card names the output face");
            h.assertTrue(f.getItem(2).isEmpty(), "and nothing may be shoved into the output slot");
            h.succeed();
        });
    }

    /** CLUSTER dispatch treats a BLOCK as one machine, not a face: a furnace takes its ore on top and its fuel on the
     *  side, and the whole batch still has to land in the same furnace. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void clusterFeedsTwoFacesOfOneMachine(GameTestHelper h) {
        h.setBlock(A, Blocks.FURNACE);
        h.setBlock(P, OmniLogistics.DISTRIBUTOR.get());
        DistributorBlockEntity be = h.getBlockEntity(P);
        be.toggleMode();                                                   // CLUSTER
        be.cards.setStackInSlot(0, card(h, CardKind.ITEM, A, Direction.UP, LogisticsCardItem.INSERT));
        be.cards.setStackInSlot(1, card(h, CardKind.ITEM, A, Direction.WEST, LogisticsCardItem.INSERT));
        be.view.insertItem(0, new ItemStack(Items.RAW_IRON, 1), false);
        be.view.insertItem(1, new ItemStack(Items.COAL, 1), false);
        h.succeedWhen(() -> {
            FurnaceBlockEntity f = h.getBlockEntity(A);
            h.assertTrue(f.getItem(0).is(Items.RAW_IRON) || f.getItem(2).is(Items.IRON_INGOT), "the ore must reach the input slot");
            h.assertTrue(!f.getItem(1).isEmpty() || f.getItem(2).is(Items.IRON_INGOT), "the coal must reach the fuel slot");
        });
    }

    /** Auto-bind: one machine bound by hand, blank cards in the free lanes, and the identical chests around it join. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void autoBindFindsTheOtherMachines(GameTestHelper h) {
        BlockPos c1 = new BlockPos(0, 1, 1), c2 = new BlockPos(2, 1, 1), c3 = new BlockPos(1, 1, 2);
        for (BlockPos c : new BlockPos[]{c1, c2, c3}) h.setBlock(c, Blocks.CHEST);
        h.setBlock(P, OmniLogistics.DISTRIBUTOR.get());
        DistributorBlockEntity be = h.getBlockEntity(P);
        be.toggleMode();
        be.cards.setStackInSlot(0, card(h, CardKind.ITEM, c1, LogisticsCardItem.INSERT));
        be.cards.setStackInSlot(1, new ItemStack(OmniLogistics.CARD.get()));
        be.cards.setStackInSlot(2, new ItemStack(OmniLogistics.CARD.get()));
        int added = be.autoBind(h.getLevel());
        h.assertTrue(added == 2, "two more chests should have been bound, got " + added);
        h.assertTrue(LogisticsCardItem.targetPos(be.cards.getStackInSlot(1)) != null, "lane 1 must be bound now");
        h.assertTrue(LogisticsCardItem.targetPos(be.cards.getStackInSlot(2)) != null, "lane 2 must be bound now");
        h.succeed();
    }

    /** Stock Keeper: fills the bound chest to one stack and then stops, leaving the rest in the router. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void stockCardKeepsOneStack(GameTestHelper h) {
        h.setBlock(A, Blocks.CHEST);
        RouterBlockEntity be = router(h);
        be.setInterval(1);
        be.buffer.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 64));
        be.cards.setStackInSlot(0, card(h, CardKind.STOCK, A, LogisticsCardItem.INSERT));
        h.runAfterDelay(40, () -> {
            ChestBlockEntity chest = h.getBlockEntity(A);
            int n = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) n += chest.getItem(i).getCount();
            h.assertTrue(n == 64, "the chest should hold exactly one stack, holds " + n);
            h.succeed();
        });
    }

    /** Right-clicking one card onto another copies the filter, and only as far as the target card can hold. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cardCopiesItsFilterOntoAnother(GameTestHelper h) {
        ItemStack src = new ItemStack(OmniLogistics.CARD.get()), dst = new ItemStack(OmniLogistics.CARD.get());
        CardConfig.setFilter(src, new ItemStack(Items.DIAMOND));
        src.set(OmniLogistics.CARD_FLAGS.get(), ComponentPredicateEngine.MATCH_ITEM);
        CardConfig.copyFilter(src, dst);
        h.assertTrue(LogisticsCardItem.spec(dst).test(new ItemStack(Items.DIAMOND)), "the copy must match what the original matched");
        h.assertTrue(!LogisticsCardItem.spec(dst).test(new ItemStack(Items.COBBLESTONE)), "and nothing else");
        h.succeed();
    }

    /** NBT rules on a router card: only tools worn less than a threshold, and only swords at Sharpness III or better,
     *  leave the router - with the enchantment check needing the server's registries, which it gets in a real world. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nbtRulesPickByDurabilityAndEnchantLevel(GameTestHelper h) {
        h.setBlock(A, Blocks.CHEST);
        h.setBlock(B, Blocks.CHEST);
        RouterBlockEntity be = router(h);
        be.setInterval(1);
        var sharp = sharpness(h);
        ItemStack fresh = new ItemStack(Items.IRON_SWORD), worn = new ItemStack(Items.IRON_SWORD), blunt = new ItemStack(Items.IRON_SWORD);
        worn.set(net.minecraft.core.component.DataComponents.DAMAGE, 200);
        fresh.enchant(sharp, 4);
        worn.enchant(sharp, 4);
        blunt.enchant(sharp, 1);
        ChestBlockEntity src = h.getBlockEntity(A);
        src.setItem(0, worn);
        src.setItem(1, blunt);
        src.setItem(2, fresh);
        ItemStack pull = card(h, CardKind.ITEM, A, LogisticsCardItem.EXTRACT);   // the rules sit on the pulling card
        CardConfig.apply(pull, CardConfig.modes(pull), ComponentPredicateEngine.MATCH_NBT, List.of(), List.of(), List.of(
            new com.mertokan.omnilogistics.api.NbtRule(List.of("minecraft:damage"), com.mertokan.omnilogistics.api.NbtRule.Op.LT, "50", true),
            new com.mertokan.omnilogistics.api.NbtRule(List.of("minecraft:enchantments", "levels", "minecraft:sharpness"),
                com.mertokan.omnilogistics.api.NbtRule.Op.GE, "3", true)));
        be.cards.setStackInSlot(0, pull);
        be.cards.setStackInSlot(1, card(h, CardKind.ITEM, B, LogisticsCardItem.INSERT));
        h.runAfterDelay(60, () -> {
            ChestBlockEntity dst = h.getBlockEntity(B);
            int moved = 0;
            for (int i = 0; i < dst.getContainerSize(); i++) {
                ItemStack s = dst.getItem(i);
                if (s.isEmpty()) continue;
                moved++;
                h.assertTrue(s.getDamageValue() == 0 && ComponentPredicateEngine.enchantmentsOf(s).getLevel(sharp) == 4,
                    "only the fresh Sharpness IV sword may move, got " + s + " " + s.getComponentsPatch());
            }
            h.assertTrue(moved == 1, "exactly one sword should have moved, moved " + moved);
            h.succeed();
        });
    }

    /** A fluid filter names its fluid with a container: a water bucket in the reference slot means water, not lava. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fluidCardFiltersByBucket(GameTestHelper h) {
        ItemStack card = new ItemStack(OmniLogistics.CARDS.get(CardKind.FLUID).get());
        CardConfig.setFilter(card, new ItemStack(Items.WATER_BUCKET));
        FilterSpec spec = LogisticsCardItem.spec(card);
        h.assertTrue(spec.testFluid(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000)),
            "water must pass a water filter");
        h.assertTrue(!spec.testFluid(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000)),
            "lava must not");
        h.assertTrue(LogisticsCardItem.spec(new ItemStack(OmniLogistics.CARDS.get(CardKind.FLUID).get()))
            .testFluid(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000)),
            "an empty filter still means anything");
        h.succeed();
    }

    // ---- exposer ---------------------------------------------------------------------------

    /** A hopper under the exposer sees only what passes the filter; everything else stays in the chest. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void exposerHidesNonMatchingItems(GameTestHelper h) {
        BlockPos hopper = new BlockPos(1, 0, 1);
        h.setBlock(A, Blocks.CHEST);
        h.setBlock(P, OmniLogistics.EXPOSER.get());
        h.setBlock(hopper, Blocks.HOPPER);
        ChestBlockEntity chest = h.getBlockEntity(A);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(sharpness(h), 1);
        chest.setItem(0, new ItemStack(Items.STONE, 8));
        chest.setItem(1, sword);
        ExposerBlockEntity ex = h.getBlockEntity(P);
        byte[] modes = new byte[6];
        modes[Direction.WEST.ordinal()] = 1;
        ex.applyConfig(modes, ComponentPredicateEngine.HAS_ENCHANTS, List.of(), List.of(), List.of());
        h.runAfterDelay(120, () -> {
            HopperBlockEntity hop = h.getBlockEntity(hopper);
            boolean gotSword = false;
            for (int i = 0; i < hop.getContainerSize(); i++) {
                ItemStack s = hop.getItem(i);
                h.assertTrue(!s.is(Items.STONE), "stone must never pass the exposer");
                if (s.is(Items.DIAMOND_SWORD)) gotSword = true;
            }
            h.assertTrue(gotSword, "the enchanted sword should have been pulled through the exposer");
            h.assertTrue(chest.getItem(0).getCount() == 8, "stone must stay in the chest");
            h.succeed();
        });
    }

    // ---- router cards ----------------------------------------------------------------------

    /** EXTRACT card on chest A, INSERT card on chest B: the router moves the lot with no cables. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void routerMovesItemsBetweenBoundChests(GameTestHelper h) {
        h.setBlock(A, Blocks.CHEST);
        h.setBlock(B, Blocks.CHEST);
        ((ChestBlockEntity) h.getBlockEntity(A)).setItem(0, new ItemStack(Items.STONE, 20));
        RouterBlockEntity be = router(h);
        be.cards.setStackInSlot(0, card(h, CardKind.ITEM, A, LogisticsCardItem.EXTRACT));
        be.cards.setStackInSlot(1, card(h, CardKind.ITEM, B, LogisticsCardItem.INSERT));
        h.succeedWhen(() -> h.assertTrue(((ChestBlockEntity) h.getBlockEntity(B)).getItem(0).getCount() == 20, "all 20 stone should reach chest B"));
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void energyCardChargesExtractor(GameTestHelper h) {
        h.setBlock(A, OmniLogistics.EXTRACTOR.get());
        RouterBlockEntity router = router(h);
        router.energy.receiveEnergy(400_000, false);
        router.cards.setStackInSlot(0, card(h, CardKind.ENERGY, A, LogisticsCardItem.INSERT));
        ExtractorBlockEntity target = h.getBlockEntity(A);
        h.succeedWhen(() -> h.assertTrue(target.energy.getEnergyStored() >= 100_000, "extractor should be charged by the energy card"));
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void fluidCardDrainsCauldron(GameTestHelper h) {
        h.setBlock(A, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        RouterBlockEntity router = router(h);
        router.cards.setStackInSlot(0, card(h, CardKind.FLUID, A, LogisticsCardItem.EXTRACT));
        h.succeedWhen(() -> h.assertTrue(router.fluid.getFluidAmount() >= 1000, "router tank should receive the cauldron water"));
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void vacuumCardCollectsDrops(GameTestHelper h) {
        RouterBlockEntity router = router(h);
        router.cards.setStackInSlot(0, card(h, CardKind.VACUUM, null, LogisticsCardItem.EXTRACT));
        BlockPos above = new BlockPos(1, 2, 1);
        h.getLevel().addFreshEntity(new ItemEntity(h.getLevel(),
            h.absolutePos(above).getX() + 0.5, h.absolutePos(above).getY() + 0.5, h.absolutePos(above).getZ() + 0.5,
            new ItemStack(Items.STONE, 5)));
        h.succeedWhen(() -> h.assertTrue(router.buffer.getStackInSlot(0).getCount() == 5, "vacuum card should pull the 5 stone into the buffer"));
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void voidCardDestroysBuffer(GameTestHelper h) {
        RouterBlockEntity router = router(h);
        router.buffer.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 3));
        router.cards.setStackInSlot(0, card(h, CardKind.VOID, null, LogisticsCardItem.EXTRACT));
        h.succeedWhen(() -> h.assertTrue(router.buffer.getStackInSlot(0).isEmpty(), "void card should empty the buffer"));
    }

    /** Breaker into the buffer, placer out of it: the block moves from A to B without a player. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void breakerThenPlacerMovesABlock(GameTestHelper h) {
        h.setBlock(A, Blocks.COBBLESTONE);
        RouterBlockEntity router = router(h);
        router.cards.setStackInSlot(0, card(h, CardKind.BREAKER, A, LogisticsCardItem.EXTRACT));
        router.cards.setStackInSlot(1, card(h, CardKind.PLACER, B, LogisticsCardItem.EXTRACT));
        h.succeedWhen(() -> {
            h.assertBlockPresent(Blocks.AIR, A);
            h.assertBlockPresent(Blocks.COBBLESTONE, B);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void detectorEmitsRedstone(GameTestHelper h) {
        h.setBlock(A, Blocks.CHEST);
        RouterBlockEntity router = router(h);
        router.cards.setStackInSlot(0, card(h, CardKind.DETECTOR, A, LogisticsCardItem.EXTRACT));
        h.runAfterDelay(20, () -> h.assertTrue(router.signal() == 0, "empty chest: no signal"));
        h.runAfterDelay(21, () -> ((ChestBlockEntity) h.getBlockEntity(A)).setItem(0, new ItemStack(Items.STONE)));
        h.succeedWhen(() -> h.assertTrue(router.signal() == 15, "chest with an item: signal 15"));
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void activatorPressesButton(GameTestHelper h) {
        h.setBlock(A, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR));
        RouterBlockEntity router = router(h);
        router.cards.setStackInSlot(0, card(h, CardKind.ACTIVATOR, A, LogisticsCardItem.EXTRACT));
        h.succeedWhen(() -> h.assertTrue(h.getBlockState(A).getValue(ButtonBlock.POWERED), "activator should press the button"));
    }

    // ---- filter cards ----------------------------------------------------------------------

    /** A 4-slot card whitelists four different items at once; anything else is still refused. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void multiCardMatchesAnyOfItsReferences(GameTestHelper h) {
        ItemStack card = new ItemStack(OmniLogistics.MULTI_CARDS.get(4).get());
        CardConfig.setFilter(card, 0, new ItemStack(Items.COBBLESTONE));
        CardConfig.setFilter(card, 1, new ItemStack(Items.IRON_INGOT));
        card.set(OmniLogistics.CARD_FLAGS.get(), ComponentPredicateEngine.MATCH_ITEM);
        FilterSpec spec = LogisticsCardItem.spec(card);
        h.assertTrue(spec.refs().size() == 2, "the card should carry both references, got " + spec.refs().size());
        h.assertTrue(spec.test(new ItemStack(Items.COBBLESTONE)), "cobblestone must pass");
        h.assertTrue(spec.test(new ItemStack(Items.IRON_INGOT)), "iron must pass");
        h.assertFalse(spec.test(new ItemStack(Items.GOLD_INGOT)), "gold must not pass");
        CardConfig.setFilter(card, 1, ItemStack.EMPTY);
        h.assertFalse(LogisticsCardItem.spec(card).test(new ItemStack(Items.IRON_INGOT)), "clearing slot 2 must drop iron");
        h.succeed();
    }

    // ---- batch distributor -----------------------------------------------------------------

    /** A mixed batch pushed in the way AE2 pushes one (slot by slot, carrying the remainder) ends up split across the lanes. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void distributorDealsABatchToItsLanes(GameTestHelper h) {
        h.setBlock(A, Blocks.CHEST);
        h.setBlock(B, Blocks.CHEST);
        h.setBlock(P, OmniLogistics.DISTRIBUTOR.get());
        DistributorBlockEntity be = h.getBlockEntity(P);
        be.cards.setStackInSlot(0, laneCard(h, A, Items.COBBLESTONE));
        be.cards.setStackInSlot(1, laneCard(h, B, Items.IRON_INGOT));
        h.runAfterDelay(1, () -> {
            ItemStack iron = new ItemStack(Items.IRON_INGOT, 4);
            h.assertTrue(be.view.insertItem(0, iron, false).getCount() == 4, "lane 0 filters cobblestone, so it must refuse iron");
            // exactly what ExternalStorageFacade.insertExternal does: walk the slots carrying the remainder
            ItemStack rest = new ItemStack(Items.COBBLESTONE, 3);
            for (int i = 0; i < DistributorBlockEntity.LANES && !rest.isEmpty(); i++) rest = be.view.insertItem(i, rest, false);
            h.assertTrue(rest.isEmpty(), "the cobblestone should have found lane 0");
            rest = new ItemStack(Items.IRON_INGOT, 2);
            for (int i = 0; i < DistributorBlockEntity.LANES && !rest.isEmpty(); i++) rest = be.view.insertItem(i, rest, false);
            h.assertTrue(rest.isEmpty(), "the iron should have found lane 1");
        });
        h.succeedWhen(() -> {
            ChestBlockEntity a = h.getBlockEntity(A), b = h.getBlockEntity(B);
            h.assertTrue(a.getItem(0).is(Items.COBBLESTONE) && a.getItem(0).getCount() == 3, "chest A should hold the 3 cobblestone");
            h.assertTrue(b.getItem(0).is(Items.IRON_INGOT) && b.getItem(0).getCount() == 2, "chest B should hold the 2 iron");
        });
    }

    /** An unbound card is not a lane at all: nothing may land in it, or a batch would vanish into a dead end. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void distributorUnboundLaneAcceptsNothing(GameTestHelper h) {
        h.setBlock(P, OmniLogistics.DISTRIBUTOR.get());
        DistributorBlockEntity be = h.getBlockEntity(P);
        be.cards.setStackInSlot(0, card(h, CardKind.ITEM, null, LogisticsCardItem.INSERT));   // no target
        h.runAfterDelay(1, () -> {
            ItemStack s = new ItemStack(Items.COBBLESTONE, 4);
            h.assertTrue(be.view.insertItem(0, s, false).getCount() == 4, "an unbound lane must refuse everything");
            h.assertTrue(be.status(0) == DistributorBlockEntity.UNSET, "an unbound lane must report UNSET");
            h.succeed();
        });
    }

    /** CLUSTER mode hands a whole batch to one machine and the next batch to the next machine. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void distributorClusterFeedsMachinesInTurn(GameTestHelper h) {
        h.setBlock(A, Blocks.CHEST);
        h.setBlock(B, Blocks.CHEST);
        h.setBlock(P, OmniLogistics.DISTRIBUTOR.get());
        DistributorBlockEntity be = h.getBlockEntity(P);
        be.toggleMode();
        be.cards.setStackInSlot(0, card(h, CardKind.ITEM, A, LogisticsCardItem.INSERT));
        be.cards.setStackInSlot(1, card(h, CardKind.ITEM, B, LogisticsCardItem.INSERT));
        h.assertTrue(be.cluster(), "the distributor should be in CLUSTER mode");
        h.runAfterDelay(1, () -> {                                    // batch 1: two ingredients, one machine
            be.view.insertItem(0, new ItemStack(Items.COBBLESTONE, 3), false);
            be.view.insertItem(1, new ItemStack(Items.IRON_INGOT, 2), false);
        });
        h.runAfterDelay(6, () -> {                                    // batch 2 goes to the other machine
            be.view.insertItem(0, new ItemStack(Items.COBBLESTONE, 3), false);
            be.view.insertItem(1, new ItemStack(Items.IRON_INGOT, 2), false);
        });
        h.succeedWhen(() -> {
            ChestBlockEntity a = h.getBlockEntity(A), b = h.getBlockEntity(B);
            h.assertTrue(!a.isEmpty() && !b.isEmpty(), "both machines should have been fed in turn");
            for (ChestBlockEntity c : new ChestBlockEntity[]{a, b}) {
                int cobble = 0, iron = 0;
                for (int i = 0; i < c.getContainerSize(); i++) {
                    ItemStack s = c.getItem(i);
                    if (s.is(Items.COBBLESTONE)) cobble += s.getCount();
                    if (s.is(Items.IRON_INGOT)) iron += s.getCount();
                }
                h.assertTrue(cobble == 3 && iron == 2, "each machine must get one WHOLE batch, got " + cobble + " + " + iron);
            }
        });
    }

    /** Drop bound cards in with no filter, run one batch through: each lane keeps the ingredient it caught. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void distributorLearnsTheFilterFromTheFirstBatch(GameTestHelper h) {
        h.setBlock(A, Blocks.CHEST);
        h.setBlock(B, Blocks.CHEST);
        h.setBlock(P, OmniLogistics.DISTRIBUTOR.get());
        DistributorBlockEntity be = h.getBlockEntity(P);
        be.cards.setStackInSlot(0, card(h, CardKind.ITEM, A, LogisticsCardItem.INSERT));   // bound, NO filter
        be.cards.setStackInSlot(1, card(h, CardKind.ITEM, B, LogisticsCardItem.INSERT));
        h.runAfterDelay(1, () -> {
            h.assertTrue(be.status(0) == DistributorBlockEntity.LEARN, "an unfiltered bound lane should report LEARN");
            ItemStack rest = new ItemStack(Items.COBBLESTONE, 3);                          // AE2 walks the slots with the remainder
            for (int i = 0; i < DistributorBlockEntity.LANES && !rest.isEmpty(); i++) rest = be.view.insertItem(i, rest, false);
            rest = new ItemStack(Items.IRON_INGOT, 2);
            for (int i = 0; i < DistributorBlockEntity.LANES && !rest.isEmpty(); i++) rest = be.view.insertItem(i, rest, false);
            h.assertTrue(LogisticsCardItem.spec(be.cards.getStackInSlot(0)).test(new ItemStack(Items.COBBLESTONE)),
                "lane 1 should have learned cobblestone");
            h.assertFalse(LogisticsCardItem.spec(be.cards.getStackInSlot(0)).test(new ItemStack(Items.IRON_INGOT)),
                "lane 1 must not also take iron");
            h.assertTrue(LogisticsCardItem.spec(be.cards.getStackInSlot(1)).test(new ItemStack(Items.IRON_INGOT)),
                "lane 2 should have learned iron");
        });
        h.succeedWhen(() -> {
            ChestBlockEntity a = h.getBlockEntity(A), b = h.getBlockEntity(B);
            h.assertTrue(a.getItem(0).is(Items.COBBLESTONE) && b.getItem(0).is(Items.IRON_INGOT), "each learned ingredient goes to its own machine");
        });
    }

    /** An unfiltered EXTRACT lane takes the product back and leaves the ingredients alone - no filter to fill in. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void distributorReturnLaneTakesOnlyTheProduct(GameTestHelper h) {
        h.setBlock(A, Blocks.CHEST);            // stands in for the machine
        h.setBlock(B, Blocks.CHEST);            // the return face
        h.setBlock(P, OmniLogistics.DISTRIBUTOR.get());
        DistributorBlockEntity be = h.getBlockEntity(P);
        be.cards.setStackInSlot(0, laneCard(h, A, Items.COBBLESTONE));                     // ingredient lane
        be.cards.setStackInSlot(1, card(h, CardKind.ITEM, A, LogisticsCardItem.EXTRACT));  // return lane, NO filter
        ChestBlockEntity machine = h.getBlockEntity(A);
        machine.setItem(0, new ItemStack(Items.COBBLESTONE, 8));                           // an ingredient sitting in the machine
        machine.setItem(1, new ItemStack(Items.STONE, 4));                                 // the product
        h.runAfterDelay(20, () -> {
            ChestBlockEntity back = h.getBlockEntity(B);
            int stone = 0, cobble = 0;
            for (int i = 0; i < back.getContainerSize(); i++) {
                ItemStack s = back.getItem(i);
                if (s.is(Items.STONE)) stone += s.getCount();
                if (s.is(Items.COBBLESTONE)) cobble += s.getCount();
            }
            h.assertTrue(stone > 0, "the product should have come back");
            h.assertTrue(cobble == 0, "the ingredient must stay in the machine, got " + cobble);
            h.succeed();
        });
    }
}
