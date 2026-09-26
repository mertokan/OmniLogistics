package com.mertokan.omnilogistics.debug;

import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.api.ComponentPredicateEngine;
import com.mertokan.omnilogistics.exposer.ExposerBlockEntity;
import com.mertokan.omnilogistics.extractor.ExtractorBlockEntity;
import com.mertokan.omnilogistics.miner.MinerTier;
import com.mertokan.omnilogistics.pipe.ConduitBlockEntity;
import com.mertokan.omnilogistics.pipe.PipeTier;
import com.mertokan.omnilogistics.pipe.PipeType;
import com.mertokan.omnilogistics.router.CardKind;
import com.mertokan.omnilogistics.router.LogisticsCardItem;
import com.mertokan.omnilogistics.router.RouterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the mod ships, built around one point so a person can try it by hand: {@code /omni showcase} in any world
 * (ops), or the prepared "OmniTest" world (gradlew runClientPrepare). Flattens a 27 x 27 area, so use it on open ground.
 * The player stands at the origin facing north; five rows in front, supply chests behind, signs on the ground.
 */
public final class Showcase {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    /** What the cross-mod bays feed their machines and what they expect back. Ids, not classes: no compile dependency. */
    private static final String MEK_INGREDIENT = "minecraft:redstone", MEK_PRODUCT = "mekanism:enriched_redstone";
    private static final String AE2_INGREDIENT = "minecraft:iron_block", AE2_PRODUCT = "minecraft:iron_ingot";
    private static final String INF_ITEM = "minecraft:copper_ingot", INF_INFUSION = "minecraft:redstone",
        INF_PRODUCT = "mekanism:alloy_infused";   // copper + redstone infusion -> Infused Alloy
    private static final String AE2_RECIPE = "minecraft:iron_ingot_from_iron_block";   // shapeless, 1 block -> 9 ingots
    private Showcase() {}

    public static void build(ServerLevel sl, BlockPos o, @Nullable ServerPlayer player) {
        int x = o.getX(), y = o.getY(), z = o.getZ();
        for (int dx = -13; dx <= 13; dx++)
            for (int dz = -42; dz <= 3; dz++) {   // rows 6-9 (cross-mod bays, ME auto-craft, machine hall) reach z-38
                set(sl, new BlockPos(x + dx, y - 1, z + dz), Blocks.SMOOTH_STONE);
                for (int dy = 0; dy <= 5; dy++) set(sl, new BlockPos(x + dx, y + dy, z + dz), Blocks.AIR);
            }
        Holder<Enchantment> sharp = ench(sl, Enchantments.SHARPNESS), eff = ench(sl, Enchantments.EFFICIENCY),
            unb = ench(sl, Enchantments.UNBREAKING), fort = ench(sl, Enchantments.FORTUNE), looting = ench(sl, Enchantments.LOOTING);

        // ---- row 1 (z-3): item pipes, exposer, extractor, router ------------------------------------------
        int r1 = z - 3;
        ItemStack[] source = stacks(Items.OAK_LOG, Items.IRON_INGOT, Items.COBBLESTONE, Items.DIAMOND, Items.STONE, Items.GOLD_INGOT);
        source[25] = enchanted(Items.DIAMOND_SWORD, sharp, 3);
        source[26] = enchanted(Items.IRON_PICKAXE, eff, 2);
        chest(sl, new BlockPos(x - 9, y, r1), source);
        PipeTier[] tiers = {PipeTier.BASIC, PipeTier.ADVANCED, PipeTier.ELITE, PipeTier.ULTIMATE};
        for (int k = 0; k < 4; k++) pipe(sl, new BlockPos(x - 8 + k, y, r1), PipeType.ITEM, tiers[k]);
        mode(sl, new BlockPos(x - 8, y, r1), Direction.WEST, ConduitBlockEntity.Mode.PULL);
        mode(sl, new BlockPos(x - 5, y, r1), Direction.EAST, ConduitBlockEntity.Mode.PUSH);
        if (!place(sl, new BlockPos(x - 4, y, r1), "functionalstorage:oak_1")) chest(sl, new BlockPos(x - 4, y, r1));   // a drawer when Functional Storage is around
        sign(sl, new BlockPos(x - 7, y, r1 + 1), "Item pipes", "chest: EXTRACT", "drawer: INSERT", "card = filter");

        chest(sl, new BlockPos(x - 1, y, r1 - 1), enchanted(Items.DIAMOND_SWORD, sharp, 5), enchanted(Items.DIAMOND_PICKAXE, fort, 3), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.IRON_INGOT, 32));
        set(sl, new BlockPos(x - 1, y, r1), OmniLogistics.EXPOSER.get());
        if (player != null && sl.getBlockEntity(new BlockPos(x - 1, y, r1)) instanceof ExposerBlockEntity ex) ex.wrenchFace(Direction.NORTH, player);
        chest(sl, new BlockPos(x, y, r1));   // sees only what the exposer lets through when pulled by a hopper / pipe
        sign(sl, new BlockPos(x - 1, y, r1 + 1), "Exposer", "target: north", "filter: enchants", "chest east = view");

        set(sl, new BlockPos(x + 2, y, r1), OmniLogistics.EXTRACTOR.get());
        if (sl.getBlockEntity(new BlockPos(x + 2, y, r1)) instanceof ExtractorBlockEntity ext) {
            ext.items.setStackInSlot(0, enchanted(Items.DIAMOND_SWORD, sharp, 4, looting, 3));
            ext.items.setStackInSlot(ExtractorBlockEntity.BOOK, new ItemStack(Items.BOOK, 16));
        }
        fillEnergy(sl, new BlockPos(x + 2, y, r1));
        chest(sl, new BlockPos(x + 3, y, r1));
        sign(sl, new BlockPos(x + 2, y, r1 + 1), "Extractor", "sword + books", "FE full", "ejects east");

        set(sl, new BlockPos(x + 6, y, r1), OmniLogistics.ROUTER.get());
        if (sl.getBlockEntity(new BlockPos(x + 6, y, r1)) instanceof RouterBlockEntity r) {
            r.cards.setStackInSlot(0, new ItemStack(OmniLogistics.CARD.get()));
            r.energy.receiveEnergy(r.energy.getMaxEnergyStored(), false);
            r.fluid.fill(new FluidStack(Fluids.WATER, r.fluid.getCapacity()), IFluidHandler.FluidAction.EXECUTE);
        }
        chest(sl, new BlockPos(x + 8, y, r1), stacks(Items.SAND, Items.GRAVEL, Items.DIRT));
        sign(sl, new BlockPos(x + 6, y, r1 + 1), "Router", "card in slot 0", "sneak+click a", "block to bind");

        // ---- row 2 (z-7): energy cables into a miner, fluid pipe tower ------------------------------------
        int r2 = z - 7;
        set(sl, new BlockPos(x - 5, y, r2), OmniLogistics.ROUTER.get());
        if (sl.getBlockEntity(new BlockPos(x - 5, y, r2)) instanceof RouterBlockEntity r) {
            r.energy.receiveEnergy(r.energy.getMaxEnergyStored(), false);
            r.fluid.fill(new FluidStack(Fluids.WATER, r.fluid.getCapacity()), IFluidHandler.FluidAction.EXECUTE);
        }
        for (int k = 0; k < 4; k++) {
            pipe(sl, new BlockPos(x - 4 + k, y, r2), PipeType.ENERGY, tiers[k]);
            pipe(sl, new BlockPos(x - 5, y + 1 + k, r2), PipeType.FLUID, tiers[k]);
        }
        mode(sl, new BlockPos(x - 4, y, r2), Direction.WEST, ConduitBlockEntity.Mode.PULL);
        mode(sl, new BlockPos(x - 5, y + 1, r2), Direction.DOWN, ConduitBlockEntity.Mode.PULL);
        set(sl, new BlockPos(x, y, r2), OmniLogistics.MINERS.get(MinerTier.ELITE).get());
        chest(sl, new BlockPos(x + 1, y, r2));
        sign(sl, new BlockPos(x - 2, y, r2 + 1), "Energy cables", "router FE ->", "elite miner", "fluid tower up");

        // ---- row 3 (z-11): every miner tier with a chest, lever = redstone pause -------------------------
        int r3 = z - 11;
        MinerTier[] mt = MinerTier.values();
        for (int k = 0; k < mt.length; k++) {
            BlockPos p = new BlockPos(x - 8 + 4 * k, y, r3);
            set(sl, p, OmniLogistics.MINERS.get(mt[k]).get());
            fillEnergy(sl, p);
            chest(sl, p.east());
        }
        set(sl, new BlockPos(x + 4, y, r3 + 1), Blocks.LEVER.defaultBlockState());
        monitor(sl, new BlockPos(x + 4, y, r3 + 2), new BlockPos(x + 4, y, r3));        // watches the ultimate miner
        sign(sl, new BlockPos(x - 2, y, r3 + 1), "Void miners", "basic..ultimate", "FE full (25 cycles)", "lever = pause");

        // ---- row 4 (z-15): other mods, only what is installed: creative power into our cables and miners, their cables into ours
        int r4 = z - 15;
        if (place(sl, new BlockPos(x - 9, y, r4), "powah:energy_cell_creative")) {
            for (int k = 0; k < 4; k++) pipe(sl, new BlockPos(x - 8 + k, y, r4), PipeType.ENERGY, PipeTier.ULTIMATE);
            mode(sl, new BlockPos(x - 8, y, r4), Direction.WEST, ConduitBlockEntity.Mode.PULL);
            set(sl, new BlockPos(x - 4, y, r4), OmniLogistics.MINERS.get(MinerTier.BASIC).get());
            chest(sl, new BlockPos(x - 3, y, r4));
            sign(sl, new BlockPos(x - 6, y, r4 + 1), "Powah creative", "cell -> our", "cables -> miner");
        }
        if (place(sl, new BlockPos(x + 1, y, r4), "mekanism:creative_energy_cube", Direction.EAST)) {   // the cube only outputs on its front
            for (int k = 0; k < 3; k++) place(sl, new BlockPos(x + 2 + k, y, r4), "mekanism:basic_universal_cable");
            set(sl, new BlockPos(x + 5, y, r4), OmniLogistics.MINERS.get(MinerTier.ADVANCED).get());
            chest(sl, new BlockPos(x + 6, y, r4));
            place(sl, new BlockPos(x + 5, y, r4 - 1), "mekanism:basic_bin");
            sign(sl, new BlockPos(x + 3, y, r4 + 1), "Mekanism cube", "-> universal", "cable -> miner", "-> bin north");
        }

        // ---- the Batch Distributor, driven by a plain hopper so it can be watched without AE2 ---------------
        BlockPos dp = new BlockPos(x - 1, y, r4);
        set(sl, dp, OmniLogistics.DISTRIBUTOR.get());
        chest(sl, new BlockPos(x - 2, y, r4));                       // lane 1 destination
        chest(sl, new BlockPos(x, y, r4));                           // lane 2 destination
        if (sl.getBlockEntity(dp) instanceof com.mertokan.omnilogistics.distributor.DistributorBlockEntity d) {
            // bound but deliberately unfiltered: the first hopper batch teaches each lane its ingredient
            d.cards.setStackInSlot(0, card(sl, CardKind.ITEM, new BlockPos(x - 2, y, r4), Direction.UP, LogisticsCardItem.INSERT));
            d.cards.setStackInSlot(1, card(sl, CardKind.ITEM, new BlockPos(x, y, r4), Direction.UP, LogisticsCardItem.INSERT));
        }
        set(sl, new BlockPos(x - 1, y + 1, r4), Blocks.HOPPER);      // stands in for an AE2 Pattern Provider
        chest(sl, new BlockPos(x - 1, y + 2, r4), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.IRON_INGOT, 64),
            new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.IRON_INGOT, 64));
        sign(sl, new BlockPos(x - 1, y, r4 + 1), "Distributor", "cards bound but", "EMPTY: they learn", "from 1st batch");

        // ---- CLUSTER mode: one hopper batch at a time, dealt to a different machine each time -------------
        BlockPos cp = new BlockPos(x - 5, y, r4);
        set(sl, cp, OmniLogistics.DISTRIBUTOR.get());
        chest(sl, new BlockPos(x - 6, y, r4));
        chest(sl, new BlockPos(x - 4, y, r4));
        chest(sl, new BlockPos(x - 5, y, r4 - 1));
        if (sl.getBlockEntity(cp) instanceof com.mertokan.omnilogistics.distributor.DistributorBlockEntity d) {
            d.toggleMode();                                          // CLUSTER
            d.cards.setStackInSlot(0, card(sl, CardKind.ITEM, new BlockPos(x - 6, y, r4), Direction.UP, LogisticsCardItem.INSERT));
            d.cards.setStackInSlot(1, card(sl, CardKind.ITEM, new BlockPos(x - 4, y, r4), Direction.UP, LogisticsCardItem.INSERT));
            d.cards.setStackInSlot(2, card(sl, CardKind.ITEM, new BlockPos(x - 5, y, r4 - 1), Direction.UP, LogisticsCardItem.INSERT));
            d.upgrades.setStackInSlot(0, new ItemStack(OmniLogistics.PARALLEL_UPGRADE.get(), 2));   // 12 lanes unlocked
        }
        set(sl, new BlockPos(x - 5, y + 1, r4), Blocks.HOPPER);
        chest(sl, new BlockPos(x - 5, y + 2, r4), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.IRON_INGOT, 64),
            new ItemStack(Items.GOLD_INGOT, 64), new ItemStack(Items.DIAMOND, 64));
        sign(sl, new BlockPos(x - 5, y, r4 + 1), "CLUSTER mode", "each batch to", "the next chest", "5 machines = 5x");

        // ---- row 5 (z-19..z-21): a real, powered AE2 network. Adjacent AE2 devices form ONE grid with no cable
        //      (InWorldGridNode.findInWorldConnections -> GridHelper.getExposedNode -> GridConnection.create); an ad-hoc
        //      (controller-less) network carries 8 channels, and this uses 4.
        int a = z - 19, b5 = z - 20, c5 = z - 21;
        if (place(sl, new BlockPos(x - 10, y, a), "ae2:creative_energy_cell")) {
            place(sl, new BlockPos(x - 11, y, a), "ae2:chest", Direction.SOUTH);   // front = cell slot toward the player; top face = ME view
            cell(sl, new BlockPos(x - 11, y, a), Direction.SOUTH, 0, "ae2:item_storage_cell_4k");
            place(sl, new BlockPos(x - 9, y, a), "ae2:drive", Direction.SOUTH);    // the only block that narrows: every side but its front
            place(sl, new BlockPos(x - 8, y, a), "ae2:pattern_provider");          // default push_direction=all -> all 6 sides connect
            place(sl, new BlockPos(x - 7, y, a), "ae2:interface");
            for (int k = 0; k < 3; k++) pipe(sl, new BlockPos(x - 6 + k, y, a), PipeType.ITEM, PipeTier.ADVANCED);
            mode(sl, new BlockPos(x - 4, y, a), Direction.EAST, ConduitBlockEntity.Mode.PULL);
            mode(sl, new BlockPos(x - 6, y, a), Direction.WEST, ConduitBlockEntity.Mode.PUSH);
            chest(sl, new BlockPos(x - 3, y, a), stacks(Items.COBBLESTONE, Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND));

            set(sl, new BlockPos(x - 10, y, b5), OmniLogistics.EXPOSER.get());
            if (sl.getBlockEntity(new BlockPos(x - 10, y, b5)) instanceof ExposerBlockEntity ex) {
                byte[] m = new byte[6];
                m[Direction.NORTH.ordinal()] = 1;
                ex.applyConfig(m, ComponentPredicateEngine.HAS_ENCHANTS, List.of(), List.of(), List.of());
            }
            // (x-9, b5) stays AIR on purpose: the player drops a fluix cable there (it touches the Drive to the south)
            // and puts a Storage Bus on its west face, pointing at the Exposer.
            BlockPos mp = new BlockPos(x - 8, y, b5);
            set(sl, mp, OmniLogistics.MINERS.get(MinerTier.ELITE).get());          // ejects south into the Pattern Provider = straight into ME
            fillEnergy(sl, mp);
            BlockPos pp = new BlockPos(x - 7, y, b5);
            pipe(sl, pp, PipeType.ITEM, PipeTier.ADVANCED);
            mode(sl, pp, Direction.SOUTH, ConduitBlockEntity.Mode.PULL);           // pulls whatever the Interface stocks
            mode(sl, pp, Direction.WEST, ConduitBlockEntity.Mode.NONE);            // OFF, so miner loot cannot fake this demo
            BlockPos rp = new BlockPos(x - 5, y, b5);
            set(sl, rp, OmniLogistics.ROUTER.get());
            if (sl.getBlockEntity(rp) instanceof RouterBlockEntity r) {
                r.cards.setStackInSlot(0, card(sl, CardKind.ITEM, new BlockPos(x - 5, y, c5), Direction.UP, LogisticsCardItem.EXTRACT));
                r.cards.setStackInSlot(1, card(sl, CardKind.ITEM, new BlockPos(x - 7, y, a), Direction.UP, LogisticsCardItem.INSERT));
                r.energy.receiveEnergy(r.energy.getMaxEnergyStored(), false);
            }

            chest(sl, new BlockPos(x - 10, y, c5), enchanted(Items.DIAMOND_SWORD, sharp, 5), enchanted(Items.DIAMOND_PICKAXE, fort, 3),
                enchanted(Items.IRON_PICKAXE, eff, 2), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.IRON_INGOT, 64));
            chest(sl, new BlockPos(x - 7, y, c5));
            chest(sl, new BlockPos(x - 5, y, c5), stacks(Items.GOLD_INGOT));

            monitor(sl, new BlockPos(x - 11, y, a + 2), new BlockPos(x - 11, y, a));    // watches the ME Chest
            sign(sl, new BlockPos(x - 11, y, a + 1), "ME Chest", "4k cell inside", "click TOP face", "= ME contents");
            place(sl, new BlockPos(x - 8, y + 1, a), "ae2:1k_crafting_storage");   // one storage on its own = a whole CPU
            sign(sl, new BlockPos(x - 9, y, a + 1), "AE2 = 1 grid", "no cables: the", "blocks touch", "creative cell");
            sign(sl, new BlockPos(x - 8, y, a + 1), "Crafting CPU", "1k storage on", "the provider", "card+terminal ->");
            sign(sl, new BlockPos(x - 7, y, a + 1), "ME Interface", "our pipe INSERT", "-> ME storage", "no click needed");
            sign(sl, new BlockPos(x - 5, y, a + 1), "Item pipe -> ME", "east: EXTRACT", "west: INSERT", "-> interface");
            sign(sl, new BlockPos(x - 3, y, a + 1), "Source chest", "cobble iron", "gold diamond", "-> ME storage");
            sign(sl, new BlockPos(x - 10, y, c5 - 1), "Exposer -> AE2", "wraps N chest", "only enchanted", "AE2 bus target");
            sign(sl, new BlockPos(x - 8, y, c5 - 1), "Void Miner", "-> AE2 Pattern", "Provider south", "-> into ME");
            sign(sl, new BlockPos(x - 7, y, c5 - 1), "Out of ME leg", "put 1 cobble in", "the Interface", "config slot 1st");
            sign(sl, new BlockPos(x - 5, y, c5 - 1), "Router + AE2", "card0 EXTRACT", "gold chest N", "card1 -> iface");
            sign(sl, new BlockPos(x - 2, y, c5 - 1), "AE2 PARTS", "cells + buses", "cable in gap by", "the ME Drive");

            List<ItemStack> ae = new ArrayList<>();   // cable-bus PARTS need a cable in the bus, so they stay hand-placed
            for (String[] q : new String[][]{{"ae2:fluix_glass_cable", "16"}, {"ae2:storage_bus", "4"}, {"ae2:import_bus", "2"},
                {"ae2:export_bus", "2"}, {"ae2:terminal", "2"}, {"ae2:crafting_terminal", "1"}, {"ae2:cable_anchor", "8"},
                {"ae2:item_storage_cell_4k", "6"}, {"ae2:item_storage_cell_64k", "2"}, {"ae2:cell_workbench", "1"},
                {"ae2:memory_card", "1"}, {"ae2:network_tool", "1"}, {"ae2:certus_quartz_wrench", "1"}, {"ae2:interface", "4"},
                {"ae2:pattern_provider", "4"}, {"ae2:drive", "2"}, {"ae2:chest", "2"}, {"ae2:creative_energy_cell", "4"},
                {"ae2:energy_acceptor", "4"}, {"ae2:controller", "8"}, {"ae2things:disk_drive_4k", "2"},
                {"extendedae:ex_interface", "1"}, {"extendedae:ingredient_buffer", "1"}}) {
                ItemStack st = item(q[0], Integer.parseInt(q[1]));
                if (!st.isEmpty()) ae.add(st);
            }
            chest(sl, new BlockPos(x - 2, y, c5), ae.toArray(ItemStack[]::new));
        }
        // Our FE into a SECOND, independent ME network, and the leg that needs zero clicks: a powered ME Chest's input slot
        // accepts anything, so our pipe fills its 4k cell on its own while the player watches.
        if (BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("ae2:energy_acceptor"))) {
            if (!place(sl, new BlockPos(x - 1, y, a), "powah:energy_cell_creative")) {
                set(sl, new BlockPos(x - 1, y, a), OmniLogistics.ROUTER.get());
                fillEnergy(sl, new BlockPos(x - 1, y, a));
            }
            for (int k = 0; k < 3; k++) pipe(sl, new BlockPos(x + k, y, a), PipeType.ENERGY, PipeTier.ULTIMATE);
            mode(sl, new BlockPos(x, y, a), Direction.WEST, ConduitBlockEntity.Mode.PULL);
            place(sl, new BlockPos(x + 3, y, a), "ae2:energy_acceptor");            // FE in on all six faces
            place(sl, new BlockPos(x + 4, y, a), "ae2:chest", Direction.SOUTH);
            cell(sl, new BlockPos(x + 4, y, a), Direction.SOUTH, 0, "ae2:item_storage_cell_4k");
            for (int k = 0; k < 3; k++) pipe(sl, new BlockPos(x + 5 + k, y, a), PipeType.ITEM, PipeTier.ADVANCED);
            mode(sl, new BlockPos(x + 7, y, a), Direction.EAST, ConduitBlockEntity.Mode.PULL);
            mode(sl, new BlockPos(x + 5, y, a), Direction.WEST, ConduitBlockEntity.Mode.PUSH);
            chest(sl, new BlockPos(x + 8, y, a), stacks(Items.COBBLESTONE, Items.SAND));
            sign(sl, new BlockPos(x + 1, y, a + 1), "Our FE -> AE2", "creative cell", "our cable ->", "energy acceptor");
            sign(sl, new BlockPos(x + 6, y, a + 1), "Pipe -> MEChest", "east: EXTRACT", "cell stores it", "top face = view");
        }

        // ---- Actually Additions Empowerer: the real multi-position craft ---------------------------------
        //      Empowerer in the middle holds the base crystal, four Display Stands 3 blocks out hold one modifier each
        //      and each needs FE. One Batch Distributor with six UNFILTERED cards feeds all five and takes the product
        //      back: the lanes learn what to carry from the first batch that comes through.
        int ax = x + 10, az = z - 8;
        if (place(sl, new BlockPos(ax, y, az), "actuallyadditions:empowerer")) {
            BlockPos[] stands = {new BlockPos(ax, y, az - 3), new BlockPos(ax + 3, y, az),
                new BlockPos(ax, y, az + 3), new BlockPos(ax - 3, y, az)};
            for (BlockPos st : stands) place(sl, st, "actuallyadditions:display_stand");

            BlockPos dp2 = new BlockPos(ax + 1, y, az - 1);
            set(sl, dp2, OmniLogistics.DISTRIBUTOR.get());
            if (sl.getBlockEntity(dp2) instanceof com.mertokan.omnilogistics.distributor.DistributorBlockEntity d) {
                d.upgrades.setStackInSlot(0, new ItemStack(OmniLogistics.PARALLEL_UPGRADE.get(), 1));   // 9 lanes
                d.cards.setStackInSlot(0, card(sl, CardKind.ITEM, new BlockPos(ax, y, az), Direction.UP, LogisticsCardItem.INSERT));
                for (int k = 0; k < 4; k++)
                    d.cards.setStackInSlot(k + 1, card(sl, CardKind.ITEM, stands[k], Direction.UP, LogisticsCardItem.INSERT));
                // the return lane: no filter at all, so it takes whatever the Empowerer holds that is not an ingredient
                d.cards.setStackInSlot(5, card(sl, CardKind.ITEM, new BlockPos(ax, y, az), Direction.UP, LogisticsCardItem.EXTRACT));
            }
            chest(sl, new BlockPos(ax + 2, y, az - 1));            // the return face: the finished crystal lands here
            // no wrench pin any more: the return lane skips the face that fed it, so the automatic pick finds the chest

            set(sl, new BlockPos(ax + 1, y + 1, az - 1), Blocks.HOPPER);   // stands in for an AE2 Pattern Provider
            ItemStack base = item("actuallyadditions:restonia_crystal", 16);
            chest(sl, new BlockPos(ax + 1, y + 2, az - 1), base, new ItemStack(Items.RED_DYE, 16),
                new ItemStack(Items.NETHER_BRICK, 16), new ItemStack(Items.REDSTONE, 16), new ItemStack(Items.BRICK, 16));

            // the stands run on FE: one router with four bound Energy Cards, no cables to run
            BlockPos rp2 = new BlockPos(ax - 1, y, az - 1);
            set(sl, rp2, OmniLogistics.ROUTER.get());
            if (sl.getBlockEntity(rp2) instanceof RouterBlockEntity r) {
                for (int k = 0; k < 4; k++)
                    r.cards.setStackInSlot(k, card(sl, CardKind.ENERGY, stands[k], Direction.UP, LogisticsCardItem.INSERT));
                r.energy.receiveEnergy(r.energy.getMaxEnergyStored(), false);
            }
            monitor(sl, new BlockPos(ax + 2, y, az - 2), new BlockPos(ax, y, az));      // watches the empowerer itself
            sign(sl, new BlockPos(ax + 1, y, az - 2), "AA Empowerer", "craft from 5 spots", "1 block + 6 cards", "cards start EMPTY");
            sign(sl, new BlockPos(ax - 1, y, az - 2), "Router = FE", "4 energy cards", "-> 4 stands", "no cables at all");
        }

        // ---- row 6 (z-25 / z-26): the cross-mod auto-craft bays -------------------------------------------
        //      One shape twice (see clusterBay): a chest drips ingredients through a hopper into a Batch Distributor in
        //      CLUSTER mode, which hands each whole batch to the next of five identical machines from another mod, and a
        //      Wireless Router with one filtered EXTRACT card per machine brings the product back to one chest.
        //      Which face of a foreign machine takes what is never hard-coded: inFace/outFace/feFace ask the capability.
        int r6 = z - 26, r6s = z - 24;   // one row of clearance: an assembler pushes its result into ANY adjacent
                                         // inventory, and the Distributor view is one of those

        BlockPos[] asm = new BlockPos[5];
        for (int k = 0; k < 5; k++) asm[k] = new BlockPos(x - 11 + k, y, r6);
        ItemStack ae2In = item(AE2_INGREDIENT, 1);
        if (!ae2In.isEmpty() && place(sl, asm[0], "ae2:molecular_assembler")) {
            for (int k = 1; k < 5; k++) place(sl, asm[k], "ae2:molecular_assembler");
            place(sl, new BlockPos(x - 12, y, r6), "ae2:creative_energy_cell");   // touching the row = one ad-hoc grid, all five powered
            for (BlockPos a2 : asm) assemblerPattern(sl, a2);
            machineBay(sl, true, new BlockPos(x - 9, y, r6s), asm, new ItemStack[]{ae2In},
                new BlockPos(x - 11, y, r6s), new BlockPos(x - 12, y, r6s), item(AE2_PRODUCT, 1).getItem(),
                item(AE2_INGREDIENT, 64), item(AE2_INGREDIENT, 64), item(AE2_INGREDIENT, 64), item(AE2_INGREDIENT, 64));
            sign(sl, new BlockPos(x - 9, y, r6s + 1), "AE2 x5 assembler", "1 chest ->", "5 machines in turn", "CLUSTER mode");
            sign(sl, new BlockPos(x - 11, y, r6s + 1), "Router = product", "5 EXTRACT cards ->", "1 INSERT card", "-> west chest");
            sign(sl, new BlockPos(x - 12, y, r6 - 1), "AE2 power", "creative cell", "touches the row", "= one grid");
        }

        BlockPos[] mek = new BlockPos[5];
        for (int k = 0; k < 5; k++) mek[k] = new BlockPos(x + 2 + k, y, r6);
        ItemStack mekIn = item(MEK_INGREDIENT, 1);
        if (!mekIn.isEmpty() && place(sl, mek[0], "mekanism:enrichment_chamber", Direction.SOUTH)) {
            for (int k = 1; k < 5; k++) place(sl, mek[k], "mekanism:enrichment_chamber", Direction.SOUTH);
            // FE first: a powered machine answers the probes the same way an empty one does, but this is also the demo
            BlockPos fe = new BlockPos(x + 8, y, r6s);
            set(sl, fe, OmniLogistics.ROUTER.get());
            if (sl.getBlockEntity(fe) instanceof RouterBlockEntity r) {
                for (int k = 0; k < 5; k++)
                    r.cards.setStackInSlot(k, card(sl, CardKind.ENERGY, mek[k], Direction.UP, LogisticsCardItem.INSERT));
                r.upgrades.setStackInSlot(0, new ItemStack(OmniLogistics.SPEED_UPGRADE.get(), 3));
                r.energy.receiveEnergy(r.energy.getMaxEnergyStored(), false);
            }
            machineBay(sl, true, new BlockPos(x + 4, y, r6s), mek, new ItemStack[]{mekIn},
                new BlockPos(x + 1, y, r6s), new BlockPos(x, y, r6s), item(MEK_PRODUCT, 1).getItem(),
                item(MEK_INGREDIENT, 64), item(MEK_INGREDIENT, 64), item(MEK_INGREDIENT, 64), item(MEK_INGREDIENT, 64));
            sign(sl, new BlockPos(x + 4, y, r6s + 1), "Mekanism x5", "1 batch each ->", "5 machines in turn", "CLUSTER mode");
            sign(sl, new BlockPos(x + 1, y, r6s + 1), "Router = product", "5 EXTRACT cards ->", "1 INSERT card", "-> west chest");
            sign(sl, new BlockPos(x + 8, y, r6s + 1), "Router = FE", "5 energy cards", "no cables", "it finds the face");
        }

        // ---- row 7 (z-28 / z-30): the two-ingredient machine, the one a single face cannot feed -------------
        //      Mekanism's Metallurgic Infuser wants the item on one face and the infusion material on another, so this
        //      bay runs in SPLIT mode: one filtered lane per (machine, ingredient), ten lanes for five machines. The
        //      faces are not hard-coded - inFace() asks each machine which face takes copper and which takes redstone.
        int r7 = z - 30, r7s = z - 28;
        BlockPos[] inf = new BlockPos[5];
        for (int k = 0; k < 5; k++) inf[k] = new BlockPos(x + 2 + k, y, r7);
        ItemStack infItem = item(INF_ITEM, 1), infDust = item(INF_INFUSION, 1);
        if (!infItem.isEmpty() && place(sl, inf[0], "mekanism:metallurgic_infuser", Direction.SOUTH)) {
            for (int k = 1; k < 5; k++) place(sl, inf[k], "mekanism:metallurgic_infuser", Direction.SOUTH);
            BlockPos fe2 = new BlockPos(x + 8, y, r7s);
            set(sl, fe2, OmniLogistics.ROUTER.get());
            if (sl.getBlockEntity(fe2) instanceof RouterBlockEntity r) {
                for (int k = 0; k < 5; k++)
                    r.cards.setStackInSlot(k, card(sl, CardKind.ENERGY, inf[k], Direction.UP, LogisticsCardItem.INSERT));
                r.upgrades.setStackInSlot(0, new ItemStack(OmniLogistics.SPEED_UPGRADE.get(), 3));
                r.energy.receiveEnergy(r.energy.getMaxEnergyStored(), false);
            }
            machineBay(sl, false, new BlockPos(x + 4, y, r7s), inf, new ItemStack[]{infItem, infDust},
                new BlockPos(x + 1, y, r7s), new BlockPos(x, y, r7s), item(INF_PRODUCT, 1).getItem(),
                // small stacks on purpose: a vanilla hopper moves one item per 8 ticks, so a 64-stack of copper would
                // keep the redstone waiting 512 ticks before the first craft could even start
                item(INF_ITEM, 8), item(INF_INFUSION, 8), item(INF_ITEM, 8), item(INF_INFUSION, 8),
                item(INF_ITEM, 8), item(INF_INFUSION, 8), item(INF_ITEM, 8), item(INF_INFUSION, 8));
            sign(sl, new BlockPos(x + 4, y, r7s + 1), "Infuser x5", "2 inputs, 2 faces:", "copper from above,", "redstone below");
            sign(sl, new BlockPos(x + 1, y, r7s + 1), "Router = product", "5 EXTRACT cards", "-> west chest", "SPLIT mode");
            sign(sl, new BlockPos(x + 8, y, r7s + 1), "Router = FE", "5 energy cards", "no cables", "it finds the face");
        }

        // ---- row 8 (z-32 / z-34): AE2 Pattern Provider automation, the whole loop with nobody clicking ------
        //      Spine (all touching, so one ad-hoc grid): creative cell - 1k crafting storage (a CPU on its own) -
        //      drive - ME chest - stocking interface - pattern provider - OUR Distributor.
        //      A stocking Interface asks the network for the product; the network has none, so the CPU schedules the
        //      encoded PROCESSING pattern; the Provider pushes its ingredients into the Distributor (one tick, one
        //      batch, which is exactly what CLUSTER mode wants); the machines run; our Router pushes the product back
        //      into the Provider face, which is how a machine returns a result to an ME network.
        int r8 = z - 34, r8s = z - 32;
        BlockPos cell8 = new BlockPos(x - 12, y, r8s), cpu8 = new BlockPos(x - 11, y, r8s),
            drive8 = new BlockPos(x - 10, y, r8s), meChest = new BlockPos(x - 9, y, r8s),
            stock8 = new BlockPos(x - 8, y, r8s), provider = new BlockPos(x - 7, y, r8s),
            dist8 = new BlockPos(x - 6, y, r8s);
        ItemStack meIn = item(MEK_INGREDIENT, 1);
        if (!meIn.isEmpty() && place(sl, cell8, "ae2:creative_energy_cell") && place(sl, provider, "ae2:pattern_provider")) {
            place(sl, cpu8, "ae2:1k_crafting_storage");
            place(sl, drive8, "ae2:drive", Direction.SOUTH);
            cell(sl, drive8, Direction.SOUTH, 0, "ae2:item_storage_cell_4k");
            place(sl, meChest, "ae2:chest", Direction.SOUTH);
            cell(sl, meChest, Direction.SOUTH, 0, "ae2:item_storage_cell_4k");
            place(sl, stock8, "ae2:interface");

            // ingredients into the network: our own pipe drops them into the ME Chest, which files them in its cell
            pipe(sl, new BlockPos(x - 9, y + 1, r8s), PipeType.ITEM, PipeTier.ADVANCED);
            mode(sl, new BlockPos(x - 9, y + 1, r8s), Direction.UP, ConduitBlockEntity.Mode.PULL);
            mode(sl, new BlockPos(x - 9, y + 1, r8s), Direction.DOWN, ConduitBlockEntity.Mode.PUSH);
            chest(sl, new BlockPos(x - 9, y + 2, r8s), item(MEK_INGREDIENT, 64), item(MEK_INGREDIENT, 64),
                item(MEK_INGREDIENT, 64), item(MEK_INGREDIENT, 64));

            // the machines this network drives
            BlockPos[] cham = new BlockPos[3];
            for (int k = 0; k < 3; k++) cham[k] = new BlockPos(x - 9 + k, y, r8);
            for (BlockPos m : cham) place(sl, m, "mekanism:enrichment_chamber", Direction.SOUTH);
            BlockPos fe8 = new BlockPos(x - 5, y, r8);
            set(sl, fe8, OmniLogistics.ROUTER.get());
            if (sl.getBlockEntity(fe8) instanceof RouterBlockEntity r) {
                for (int k = 0; k < 3; k++)
                    r.cards.setStackInSlot(k, card(sl, CardKind.ENERGY, cham[k], Direction.UP, LogisticsCardItem.INSERT));
                r.upgrades.setStackInSlot(0, new ItemStack(OmniLogistics.SPEED_UPGRADE.get(), 3));
                r.energy.receiveEnergy(r.energy.getMaxEnergyStored(), false);
            }

            // our Distributor takes the whole pattern batch and hands it to a free machine
            set(sl, dist8, OmniLogistics.DISTRIBUTOR.get());
            if (sl.getBlockEntity(dist8) instanceof com.mertokan.omnilogistics.distributor.DistributorBlockEntity d) {
                d.toggleMode();
                for (int k = 0; k < 3; k++)
                    d.cards.setStackInSlot(k, card(sl, CardKind.ITEM, cham[k], Direction.UP, LogisticsCardItem.INSERT));
            }
            // and our Router puts the product back into the Provider face - an ME network's idea of "machine finished"
            BlockPos back8 = new BlockPos(x - 11, y, r8);
            set(sl, back8, OmniLogistics.ROUTER.get());
            if (sl.getBlockEntity(back8) instanceof RouterBlockEntity r) {
                Item product = item(MEK_PRODUCT, 1).getItem();
                for (int k = 0; k < 3; k++)
                    r.cards.setStackInSlot(k, pullCard(sl, cham[k], Direction.UP, product));
                r.cards.setStackInSlot(3, card(sl, CardKind.ITEM, provider, Direction.NORTH, LogisticsCardItem.INSERT));
                r.upgrades.setStackInSlot(0, new ItemStack(OmniLogistics.SPEED_UPGRADE.get(), 3));
                r.energy.receiveEnergy(r.energy.getMaxEnergyStored(), false);
            }

            processingPattern(sl, provider, item(MEK_INGREDIENT, 1), item(MEK_PRODUCT, 1));
            stockRequest(sl, stock8, MEK_PRODUCT, 32);

            sign(sl, new BlockPos(x - 9, y, r8s + 1), "ME network", "cell + CPU + drive", "our pipe puts the", "redstone into it");
            sign(sl, new BlockPos(x - 7, y, r8s + 1), "Pattern Provider", "processing pattern", "1 redstone ->", "1 enriched");
            sign(sl, new BlockPos(x - 8, y, r8s + 1), "Stock Interface", "asks for 32 ->", "the CPU starts", "the craft itself");
            sign(sl, new BlockPos(x - 6, y, r8s + 1), "Distributor", "CLUSTER: one batch", "per machine, the", "next to the next");
            sign(sl, new BlockPos(x - 11, y, r8 - 1), "Router = return", "pushes it back to", "the provider: the", "job is complete");
        }

        // ---- row 9 (z-36 / z-38): the machine hall - one Distributor, four machines, three mods ------------
        //      SPLIT mode with one filtered lane per machine: the hopper drips a mixed chest in, each ingredient walks
        //      to the lane that asked for it, and one Router with four filtered EXTRACT cards brings every product back
        //      to a single chest. This is the "does it work with everything installed" test.
        int r9 = z - 38, r9s = z - 36;
        record Hall(String block, String in, String out, boolean fe) {}
        Hall[] hall = {                                   // no faces anywhere: the mod finds them
            new Hall("mekanism:crusher", "minecraft:bone", "minecraft:bone_meal", true),
            new Hall("mekanism:precision_sawmill", "minecraft:barrel", "minecraft:oak_planks", true),
            new Hall("actuallyadditions:crusher", "minecraft:blaze_rod", "minecraft:blaze_powder", true),
            new Hall("ae2:charger", "ae2:certus_quartz_crystal", "ae2:charged_certus_quartz_crystal", false),
        };
        BlockPos dist9 = new BlockPos(x - 4, y, r9s), fe9 = new BlockPos(x + 3, y, r9s),
            back9 = new BlockPos(x - 8, y, r9s), out9 = new BlockPos(x - 9, y, r9s);
        List<ItemStack> feed9 = new ArrayList<>();
        int built = 0;
        set(sl, dist9, OmniLogistics.DISTRIBUTOR.get());
        set(sl, fe9, OmniLogistics.ROUTER.get());
        set(sl, back9, OmniLogistics.ROUTER.get());
        chest(sl, out9);
        for (int k = 0; k < hall.length; k++) {
            BlockPos m = new BlockPos(x - 6 + 2 * k, y, r9);
            ItemStack in = item(hall[k].in(), 1);
            if (in.isEmpty() || !place(sl, m, hall[k].block(), Direction.SOUTH)) continue;
            if (hall[k].block().startsWith("ae2:")) place(sl, m.north(), "ae2:creative_energy_cell");   // AE2 machines run on grid power
            if (sl.getBlockEntity(dist9) instanceof com.mertokan.omnilogistics.distributor.DistributorBlockEntity d)
                d.cards.setStackInSlot(built, laneCard(sl, m, Direction.UP, in.getItem()));
            if (hall[k].fe() && sl.getBlockEntity(fe9) instanceof RouterBlockEntity r)
                r.cards.setStackInSlot(built, card(sl, CardKind.ENERGY, m, Direction.UP, LogisticsCardItem.INSERT));
            if (sl.getBlockEntity(back9) instanceof RouterBlockEntity r)
                r.cards.setStackInSlot(built, pullCard(sl, m, Direction.UP, item(hall[k].out(), 1).getItem()));
            feed9.add(item(hall[k].in(), 8));
            feed9.add(item(hall[k].in(), 8));
            built++;
        }
        if (built > 0) {
            set(sl, dist9.above(), Blocks.HOPPER);
            chest(sl, dist9.above().above(), feed9.toArray(ItemStack[]::new));
            for (BlockPos r : new BlockPos[]{fe9, back9})
                if (sl.getBlockEntity(r) instanceof RouterBlockEntity rr) {
                    rr.upgrades.setStackInSlot(0, new ItemStack(OmniLogistics.SPEED_UPGRADE.get(), 3));
                    rr.energy.receiveEnergy(rr.energy.getMaxEnergyStored(), false);
                }
            if (sl.getBlockEntity(back9) instanceof RouterBlockEntity r)
                r.cards.setStackInSlot(RouterBlockEntity.CARDS - 1, card(sl, CardKind.ITEM, out9, Direction.UP, LogisticsCardItem.INSERT));
            sign(sl, new BlockPos(x - 4, y, r9s + 1), "Machine hall", built + " machines, 3 mods", "one Distributor", "SPLIT mode");
            sign(sl, new BlockPos(x - 8, y, r9s + 1), "Router = product", "every machine's own", "product into", "a single chest");
            sign(sl, new BlockPos(x + 3, y, r9s + 1), "Router = FE", "no cables", "it finds the face", "");
        }

        // ---- supplies behind the player ------------------------------------------------------------------
        List<ItemStack> mod = new ArrayList<>();
        for (CardKind kind : CardKind.values()) mod.add(new ItemStack(OmniLogistics.CARDS.get(kind).get()));
        for (int cap : new int[]{4, 16, 64}) mod.add(new ItemStack(OmniLogistics.MULTI_CARDS.get(cap).get()));
        mod.add(new ItemStack(OmniLogistics.DISTRIBUTOR_ITEM.get(), 4));
        mod.add(new ItemStack(OmniLogistics.WRENCH.get()));
        mod.add(new ItemStack(OmniLogistics.SPEED_UPGRADE.get(), 3));
        mod.add(new ItemStack(OmniLogistics.PARALLEL_UPGRADE.get(), 2));
        mod.add(new ItemStack(OmniLogistics.RANGE_UPGRADE.get(), 3));
        mod.add(new ItemStack(OmniLogistics.GEM_MODULE.get()));
        mod.add(new ItemStack(OmniLogistics.FUSION_MODULE.get()));
        mod.add(new ItemStack(OmniLogistics.EXPOSER_ITEM.get(), 4));
        mod.add(new ItemStack(OmniLogistics.ROUTER_ITEM.get(), 4));
        mod.add(new ItemStack(OmniLogistics.EXTRACTOR_ITEM.get(), 4));
        for (MinerTier t : mt) mod.add(new ItemStack(OmniLogistics.MINER_ITEMS.get(t).get(), 2));
        chest(sl, new BlockPos(x - 2, y, z + 2), mod.toArray(ItemStack[]::new));
        List<ItemStack> pipes = new ArrayList<>();
        for (PipeType t : PipeType.values())
            for (PipeTier tier : PipeTier.values()) pipes.add(new ItemStack(OmniLogistics.PIPE_ITEMS.get(t).get(tier).get(), 64));
        chest(sl, new BlockPos(x, y, z + 2), pipes.toArray(ItemStack[]::new));
        chest(sl, new BlockPos(x + 2, y, z + 2),
            new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.IRON_INGOT, 64), new ItemStack(Items.GOLD_INGOT, 64), new ItemStack(Items.DIAMOND, 64),
            new ItemStack(Items.BOOK, 64), new ItemStack(Items.BOOKSHELF, 16), new ItemStack(Items.ENDER_PEARL, 16), new ItemStack(Items.SAND, 64),
            new ItemStack(Items.REDSTONE_BLOCK, 16), new ItemStack(Items.LEVER, 8), new ItemStack(Items.HOPPER, 8), new ItemStack(Items.CHEST, 16),
            new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.LAVA_BUCKET), new ItemStack(Items.OAK_LOG, 64),
            enchanted(Items.ENCHANTED_BOOK, sharp, 3), enchanted(Items.ENCHANTED_BOOK, eff, 4), enchanted(Items.ENCHANTED_BOOK, unb, 3),
            enchanted(Items.DIAMOND_SWORD, sharp, 5, looting, 3), enchanted(Items.DIAMOND_PICKAXE, eff, 5, fort, 3), enchanted(Items.DIAMOND_PICKAXE, eff, 5, unb, 3),
            new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.IRON_PICKAXE));
        sign(sl, new BlockPos(x, y, z + 3), "Supplies", "mod items | pipes", "| materials", "| other mods");
        sign(sl, new BlockPos(x - 2, y, z + 3), "Card upgrade", "card in one hand,", "gold in the other:", "HOLD right-click");
        List<ItemStack> others = new ArrayList<>();   // whatever of these is installed: power sources, their cables, storage to plug ours into
        for (String id : new String[]{"powah:energy_cell_creative", "powah:energy_cable_nitro", "mekanism:creative_energy_cube", "mekanism:ultimate_universal_cable",
            "mekanism:creative_fluid_tank", "mekanism:creative_bin", "mekanism:basic_logistical_transporter", "mekanism:basic_mechanical_pipe",
            "ae2:controller", "ae2:creative_energy_cell", "ae2:chest", "ae2:drive", "ae2:storage_bus", "ae2:import_bus", "ae2:export_bus",
            "ae2:pattern_provider", "ae2:energy_acceptor", "ae2:cell_workbench", "ae2:memory_card", "ae2:network_tool",
            "ae2:fluix_glass_cable", "ae2:item_storage_cell_4k", "ae2:terminal", "functionalstorage:oak_1", "functionalstorage:storage_controller",
            "industrialforegoing:pity_black_hole_unit", "industrialforegoing:black_hole_controller", "industrialforegoing:conveyor",
            "mekanism:basic_bin", "functionalstorage:oak_4", "extendedae:ex_interface", "ae2things:disk_drive_4k", "apotheosis:gem_dust"}) {
            ItemStack s = item(id, 16);
            if (!s.isEmpty()) others.add(s);
        }
        for (int i = 0; i < others.size(); i += 27)   // chest() writes 27 slots; spill into a second chest
            chest(sl, new BlockPos(x + 4 + 2 * (i / 27), y, z + 2), others.subList(i, Math.min(i + 27, others.size())).toArray(ItemStack[]::new));

        if (player != null) {
            player.getInventory().setItem(0, new ItemStack(OmniLogistics.WRENCH.get()));
            player.getInventory().setItem(1, new ItemStack(OmniLogistics.CARD.get()));
            player.getInventory().setItem(2, new ItemStack(OmniLogistics.SPEED_UPGRADE.get(), 3));
            player.getInventory().setItem(3, new ItemStack(OmniLogistics.CARDS.get(CardKind.ENERGY).get()));
            player.getInventory().setItem(4, new ItemStack(OmniLogistics.CARDS.get(CardKind.FLUID).get()));
            // the off hand already holds gold, so the hand upgrade works the moment you walk in: hold a card and right-click
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.GOLD_INGOT, 8));
            player.getInventory().setItem(5, new ItemStack(Items.GOLD_INGOT, 8));       // the tier cores, for the hand upgrade
            player.getInventory().setItem(6, new ItemStack(Items.DIAMOND, 8));
            player.getInventory().setItem(7, new ItemStack(Items.NETHERITE_INGOT, 4));
        }
    }

    /** Runs a few hundred ticks after {@link #build}: did the demos actually do anything? One line per check, so a
     *  headless run (gradlew runClientPrepare) is a real end-to-end test of the mod inside a full modpack. */
    public static List<String> verify(ServerLevel sl, BlockPos o) {
        int x = o.getX(), y = o.getY(), z = o.getZ();
        List<String> out = new ArrayList<>();
        int r1 = z - 3, r4 = z - 15;
        out.add(check("item pipe run delivered", countIn(sl, new BlockPos(x - 4, y, r1)) > 0));
        out.add(check("distributor SPLIT lanes learned",
            has(sl, new BlockPos(x - 2, y, r4), Items.COBBLESTONE) && has(sl, new BlockPos(x, y, r4), Items.IRON_INGOT)));
        out.add(check("distributor CLUSTER fed more than one machine",
            (countIn(sl, new BlockPos(x - 6, y, r4)) > 0 ? 1 : 0) + (countIn(sl, new BlockPos(x - 4, y, r4)) > 0 ? 1 : 0)
                + (countIn(sl, new BlockPos(x - 5, y, r4 - 1)) > 0 ? 1 : 0) >= 2));
        out.add(check("void miners produced", countIn(sl, new BlockPos(x - 7, y, z - 11)) > 0));

        int ax = x + 10, az = z - 8;                       // Actually Additions bay
        if (sl.getBlockEntity(new BlockPos(ax, y, az)) != null) {
            BlockPos[] stands = {new BlockPos(ax, y, az - 3), new BlockPos(ax + 3, y, az),
                new BlockPos(ax, y, az + 3), new BlockPos(ax - 3, y, az)};
            out.add(check("AA empowerer exposes an item handler",
                sl.getCapability(Capabilities.ItemHandler.BLOCK, new BlockPos(ax, y, az), null) != null));
            IItemHandler st0 = sl.getCapability(Capabilities.ItemHandler.BLOCK, stands[0], null);
            out.add(check("AA display stand exposes an item handler", st0 != null));
            out.add(check("AA display stand takes FE",
                sl.getCapability(Capabilities.EnergyStorage.BLOCK, stands[0], null) != null));
            int filled = 0;
            for (BlockPos st : stands) {
                IItemHandler h = sl.getCapability(Capabilities.ItemHandler.BLOCK, st, null);
                if (h != null && !h.getStackInSlot(0).isEmpty()) filled++;
            }
            out.add(check("our lanes filled " + filled + "/4 display stands", filled == 4));
            IItemHandler emp = sl.getCapability(Capabilities.ItemHandler.BLOCK, new BlockPos(ax, y, az), null);
            out.add(check("base item reached the empowerer", emp != null && !emp.getStackInSlot(0).isEmpty()));
            Item product = item("actuallyadditions:empowered_restonia_crystal", 1).getItem();
            boolean inChest = has(sl, new BlockPos(ax + 2, y, az - 1), product);
            boolean inEmpowerer = emp != null && emp.getStackInSlot(0).is(product);
            out.add(check("the empowerer crafted it", inChest || inEmpowerer));
            out.add(check("the return lane brought it back", inChest));
            out.add(check("the monitor read the empowerer",
                sl.getBlockEntity(new BlockPos(ax + 2, y, az - 2)) instanceof com.mertokan.omnilogistics.monitor.MonitorBlockEntity m
                    && !m.title.isEmpty() && !m.items.isEmpty()));
            out.add("info empowerer holds " + (emp == null ? "-" : emp.getStackInSlot(0))
                + ", hopper holds " + describe(sl, new BlockPos(ax + 1, y + 1, az - 1))
                + ", return chest holds " + describe(sl, new BlockPos(ax + 2, y, az - 1)));
        }

        int r6 = z - 26, r6s = z - 24;                     // the two cross-mod bays
        bayChecks(sl, out, "AE2 assembler", new BlockPos(x - 11, y, r6), new BlockPos(x - 9, y, r6s),
            new BlockPos(x - 12, y, r6s), AE2_INGREDIENT, AE2_PRODUCT);
        // the slots a machine never offered are not ours to fill: forcing a FACE is the feature, reaching behind the
        // face into an assembler pattern slot or a crafting output is the bug that made this check exist
        IItemHandler asmInv = sl.getCapability(Capabilities.ItemHandler.BLOCK, new BlockPos(x - 11, y, r6), null);
        if (asmInv != null && asmInv.getSlots() >= 11) {
            ItemStack ing = item(AE2_INGREDIENT, 1);
            out.add(check("AE2 assembler pattern slot untouched",
                !asmInv.getStackInSlot(10).isEmpty() && !ItemStack.isSameItem(asmInv.getStackInSlot(10), ing)));
            out.add(check("AE2 assembler output slot free of raw ingredient",
                !ItemStack.isSameItem(asmInv.getStackInSlot(9), ing)));
        }
        bayChecks(sl, out, "Mekanism", new BlockPos(x + 2, y, r6), new BlockPos(x + 4, y, r6s),
            new BlockPos(x, y, r6s), MEK_INGREDIENT, MEK_PRODUCT);
        int r7 = z - 30, r7s = z - 28;
        bayChecks(sl, out, "Infuser", new BlockPos(x + 2, y, r7), new BlockPos(x + 4, y, r7s),
            new BlockPos(x, y, r7s), INF_ITEM, INF_PRODUCT);

        int r9 = z - 38, r9s = z - 36;                     // the machine hall
        BlockPos out9 = new BlockPos(x - 9, y, r9s);
        String[][] hallCheck = {
            {"mekanism:crusher", "minecraft:bone_meal"}, {"mekanism:precision_sawmill", "minecraft:oak_planks"},
            {"actuallyadditions:crusher", "minecraft:blaze_powder"}, {"ae2:charger", "ae2:charged_certus_quartz_crystal"},
        };
        for (int k = 0; k < hallCheck.length; k++) {
            BlockPos m = new BlockPos(x - 6 + 2 * k, y, r9);
            if (sl.getBlockEntity(m) == null) continue;
            out.add(check("hall " + hallCheck[k][0] + " produced " + hallCheck[k][1],
                has(sl, out9, item(hallCheck[k][1], 1).getItem())));
        }
        if (sl.getBlockEntity(new BlockPos(x - 6, y, r9)) != null)
            out.add("info hall out " + describe(sl, out9) + " | dist " + describe(sl, new BlockPos(x - 4, y, r9s))
                + " | feed " + describe(sl, new BlockPos(x - 4, y + 2, r9s)));

        int r8 = z - 34, r8s = z - 32;                     // the ME auto-craft loop
        BlockPos provider = new BlockPos(x - 7, y, r8s), stock8 = new BlockPos(x - 8, y, r8s);
        if (sl.getBlockEntity(provider) != null) {
            out.add(check("ME pattern provider holds the encoded pattern",
                sl.getBlockEntity(provider).saveWithoutMetadata(sl.registryAccess()).contains("patterns")));
            if (!item(MEK_PRODUCT, 1).isEmpty())            // the loop's machine is Mekanism's: no Mekanism, nothing to stock
                out.add(check("ME network stocked " + MEK_PRODUCT, has(sl, stock8, item(MEK_PRODUCT, 1).getItem())));
            String ifaceNbt = sl.getBlockEntity(stock8) == null ? "-"
                : sl.getBlockEntity(stock8).saveWithoutMetadata(sl.registryAccess()).toString();
            out.add("info ME iface nbt " + ifaceNbt.substring(0, Math.min(300, ifaceNbt.length())));
            out.add("info ME feed " + describe(sl, new BlockPos(x - 9, y + 2, r8s))
                + " | pipe " + describe(sl, new BlockPos(x - 9, y + 1, r8s)));
            out.add("info ME provider " + describe(sl, provider) + " | stock iface " + describe(sl, stock8)
                + " | machine " + describe(sl, new BlockPos(x - 9, y, r8))
                + " | dist " + describe(sl, new BlockPos(x - 6, y, r8s))
                + " | me chest " + describe(sl, new BlockPos(x - 9, y, r8s)));
        }
        return out;
    }

    /** One cross-mod bay: does the foreign machine talk to us at all, did anything come out the far end, and when it did
     *  not, which link broke - the faces it offers, what the hopper and the Distributor are still holding. */
    private static void bayChecks(ServerLevel sl, List<String> out, String name, BlockPos machine, BlockPos dist,
                                  BlockPos chest, String ingredientId, String productId) {
        if (sl.getBlockEntity(machine) == null) return;                       // that mod is not installed: no lines at all
        IItemHandler h = sl.getCapability(Capabilities.ItemHandler.BLOCK, machine, null);
        out.add(check(name + " machine exposes an item handler", h != null));
        out.add(check(name + " cluster produced " + productId, has(sl, chest, item(productId, 1).getItem())));
        ItemStack probe = item(ingredientId, 1);
        StringBuilder faces = new StringBuilder();
        for (Direction d : Direction.values()) {
            IItemHandler hs = sl.getCapability(Capabilities.ItemHandler.BLOCK, machine, d);
            faces.append(d.getName().charAt(0))
                .append(hs == null ? "-" : net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(hs, probe.copy(), true).isEmpty() ? "+" : "x")
                .append(hs == null ? "" : "" + hs.getSlots()).append(' ');
        }
        StringBuilder row = new StringBuilder();
        for (int k = 0; k < 5; k++) {
            BlockPos m = machine.east(k);
            if (sl.getBlockEntity(m) == null) break;
            row.append(k).append(':').append(describe(sl, m)).append(" | ");
        }
        out.add("info " + name + " row " + row);
        out.add("info " + name + " router " + describe(sl, dist.west(3)) + " / " + describe(sl, dist.west(4)));
        if (sl.getBlockEntity(machine) != null) {
            String nbt = sl.getBlockEntity(machine).saveWithoutMetadata(sl.registryAccess()).toString();
            out.add("info " + name + " nbt " + nbt.substring(0, Math.min(700, nbt.length())));
        }
        IEnergyStorage fe = sl.getCapability(Capabilities.EnergyStorage.BLOCK, machine, null);
        out.add("info " + name + " fe=" + (fe == null ? "-" : fe.getEnergyStored() + "/" + fe.getMaxEnergyStored())
            + " faces[" + faces.toString().trim() + "] machine " + describe(sl, machine)
            + " | dist " + describe(sl, dist) + " | hopper " + describe(sl, dist.above())
            + " | feed " + describe(sl, dist.above().above()) + " | out " + describe(sl, chest));
    }

    private static String check(String name, boolean ok) {
        return (ok ? "OK   " : "FAIL ") + name;
    }

    private static int countIn(ServerLevel sl, BlockPos pos) {
        IItemHandler h = sl.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (h == null) return 0;
        int n = 0;
        for (int i = 0; i < h.getSlots(); i++) n += h.getStackInSlot(i).getCount();
        return n;
    }

    /** What a container holds, folded per item so a full chest does not print twelve identical stacks. */
    private static String describe(ServerLevel sl, BlockPos pos) {
        IItemHandler h = sl.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (h == null) return "-";
        java.util.Map<Item, Integer> total = new java.util.LinkedHashMap<>();
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (!s.isEmpty()) total.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        if (total.isEmpty()) return "empty";
        StringBuilder sb = new StringBuilder();
        total.forEach((item, n) -> sb.append(n).append("x").append(item).append(" "));
        return sb.toString().trim();
    }

    private static boolean has(ServerLevel sl, BlockPos pos, Item item) {
        IItemHandler h = sl.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (h == null || item == net.minecraft.world.item.Items.AIR) return false;   // a missing mod's item is air: never "found"
        for (int i = 0; i < h.getSlots(); i++) if (h.getStackInSlot(i).is(item)) return true;
        return false;
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    private static void set(ServerLevel sl, BlockPos pos, Block block) {
        sl.setBlock(pos, block.defaultBlockState(), 3);
    }

    private static void set(ServerLevel sl, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        sl.setBlock(pos, state, 3);
    }

    /** Block of another mod by id; false (nothing placed) when that mod is not installed. */
    private static boolean place(ServerLevel sl, BlockPos pos, String id) {
        var block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id));
        block.ifPresent(b -> {
            set(sl, pos, b);
            applyItemDefaults(sl, pos);
        });
        return block.isPresent();
    }

    /**
     * What a player's hand does and {@code setBlock} does not: hand the fresh block entity the default components of its
     * own BlockItem. Mekanism keeps a machine's whole side configuration in one of those ({@code SIDE_CONFIG}), so a
     * machine placed by code starts with every face set to NONE - no item handler on any side, only on the null side,
     * and no automation can reach it. Vanilla blocks have nothing to apply and do not care.
     */
    private static void applyItemDefaults(ServerLevel sl, BlockPos pos) {
        BlockEntity be = sl.getBlockEntity(pos);
        ItemStack asItem = new ItemStack(sl.getBlockState(pos).getBlock());
        if (be == null || asItem.isEmpty()) return;
        be.applyComponentsFromItemStack(asItem);
        be.setChanged();
        sl.invalidateCapabilities(pos);
    }

    /** A Machine Monitor facing the player (south), already holding a card bound to {@code target}. */
    private static void monitor(ServerLevel sl, BlockPos pos, BlockPos target) {
        set(sl, pos, OmniLogistics.MONITOR.get().defaultBlockState()
            .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        if (sl.getBlockEntity(pos) instanceof com.mertokan.omnilogistics.monitor.MonitorBlockEntity m)
            m.card.setStackInSlot(0, card(sl, CardKind.ITEM, target, Direction.UP, LogisticsCardItem.EXTRACT));
    }

    /** Foreign block by id with its "facing" property set; false (nothing placed) when that mod is not installed. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean place(ServerLevel sl, BlockPos pos, String id, Direction facing) {
        var block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id));
        if (block.isEmpty()) return false;
        BlockState st = block.get().defaultBlockState();
        Property<?> p = block.get().getStateDefinition().getProperty("facing");   // AE2 uses the vanilla FACING, Mekanism its own
        if (p != null && p.getPossibleValues().contains(facing)) st = st.setValue((Property) p, (Comparable) facing);
        set(sl, pos, st);
        applyItemDefaults(sl, pos);
        return true;
    }

    /** Storage cell into an AE2 Drive / ME Chest through the plain item-handler capability (AE2 registers one for every
     *  AEBaseInvBlockEntity). No AE2 dependency; a no-op when AE2 is absent. */
    private static void cell(ServerLevel sl, BlockPos pos, @Nullable Direction side, int slot, String cellId) {
        ItemStack c = item(cellId, 1);
        IItemHandler h = sl.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        if (!c.isEmpty() && h != null && slot < h.getSlots()) h.insertItem(slot, c, false);
    }

    /** A Logistics Card already bound to a block face, as if the player had sneak-clicked it. */
    private static ItemStack card(ServerLevel sl, CardKind kind, BlockPos target, Direction side, int cardMode) {
        ItemStack c = new ItemStack(OmniLogistics.CARDS.get(kind).get());
        LogisticsCardItem.bind(c, sl, target, side);
        c.set(OmniLogistics.CARD_MODE.get(), cardMode);
        return c;
    }

    /** A Logistics Card bound to a block face, in INSERT mode, filtered to exactly one item: one Distributor lane, ready to run. */
    private static ItemStack laneCard(ServerLevel sl, BlockPos target, Direction side, Item filter) {
        ItemStack c = card(sl, CardKind.ITEM, target, side, LogisticsCardItem.INSERT);
        com.mertokan.omnilogistics.router.CardConfig.setFilter(c, new ItemStack(filter));
        c.set(OmniLogistics.CARD_FLAGS.get(), ComponentPredicateEngine.MATCH_ITEM);
        return c;
    }

    /** Same, but EXTRACT and on a chosen face: pull exactly one item out of someone else's machine. */
    private static ItemStack pullCard(ServerLevel sl, BlockPos target, Direction side, Item filter) {
        ItemStack c = card(sl, CardKind.ITEM, target, side, LogisticsCardItem.EXTRACT);
        com.mertokan.omnilogistics.router.CardConfig.setFilter(c, new ItemStack(filter));
        c.set(OmniLogistics.CARD_FLAGS.get(), ComponentPredicateEngine.MATCH_ITEM);
        return c;
    }

    /**
     * The encoded crafting pattern a Molecular Assembler needs before it will take a single item: one iron block in,
     * nine ingots out. Written as plain NBT and applied through {@link DataComponentPatch}, so nothing here links
     * against AE2 - the shape is AE2's own record (inputs: 9 sparse slots, empty = {}, then result, recipeId and the two
     * substitution flags), and AE2 re-runs the recipe on load, so the id has to be a real crafting recipe.
     * A no-op when AE2, the item or the recipe is missing: the bay then simply sits idle instead of crashing.
     */
    private static void assemblerPattern(ServerLevel sl, BlockPos pos) {
        ItemStack pattern = item("ae2:crafting_pattern", 1), in = item(AE2_INGREDIENT, 1), result = item(AE2_PRODUCT, 9);
        if (pattern.isEmpty() || in.isEmpty() || result.isEmpty()
            || sl.getServer().getRecipeManager().byKey(ResourceLocation.parse(AE2_RECIPE)).isEmpty()) return;
        DynamicOps<Tag> ops = sl.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        ListTag inputs = new ListTag();
        for (int i = 0; i < 9; i++) inputs.add(i == 0 ? ItemStack.CODEC.encodeStart(ops, in).getOrThrow() : new CompoundTag());
        CompoundTag encoded = new CompoundTag();
        encoded.put("inputs", inputs);
        encoded.put("result", ItemStack.CODEC.encodeStart(ops, result).getOrThrow());
        encoded.putString("recipeId", AE2_RECIPE);
        encoded.putBoolean("canSubstitute", false);
        encoded.putBoolean("canSubstituteFluids", false);
        CompoundTag patch = new CompoundTag();
        patch.put("ae2:encoded_crafting_pattern", encoded);
        var parsed = DataComponentPatch.CODEC.parse(ops, patch);
        parsed.error().ifPresent(e -> LOG.warn("[SHOWCASE] could not encode the assembler pattern: {}", e.message()));
        parsed.result().ifPresent(pattern::applyComponents);

        // the raw, unfiltered inventory is only on the null side: 0..8 crafting grid (one item each), 9 output, 10 pattern
        IItemHandler h = sl.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (h != null && h.getSlots() > 10) h.insertItem(10, pattern, false);
    }

    /**
     * An encoded PROCESSING pattern in a Pattern Provider: N in, M out, no vanilla recipe involved. AE2 stores it as
     * {@code ae2:encoded_processing_pattern} = two sparse GenericStack lists, and a GenericStack is
     * {@code {"#t": "ae2:i", "id": "...", "#": amount}} (an empty map means "no entry"). Written as plain NBT and
     * applied through DataComponentPatch, so nothing here links against AE2; the pattern itself goes into the
     * provider's own pattern inventory, which is what the null-side item handler exposes.
     */
    private static void processingPattern(ServerLevel sl, BlockPos provider, ItemStack in, ItemStack out) {
        ItemStack pattern = item("ae2:processing_pattern", 1);
        if (pattern.isEmpty() || in.isEmpty() || out.isEmpty()) return;
        CompoundTag encoded = new CompoundTag();
        encoded.put("sparseInputs", genericStacks(in));
        encoded.put("sparseOutputs", genericStacks(out));
        CompoundTag patch = new CompoundTag();
        patch.put("ae2:encoded_processing_pattern", encoded);
        DynamicOps<Tag> ops = sl.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var parsed = DataComponentPatch.CODEC.parse(ops, patch);
        parsed.error().ifPresent(e -> LOG.warn("[SHOWCASE] could not encode the processing pattern: {}", e.message()));
        parsed.result().ifPresent(pattern::applyComponents);
        // the provider's item handler is its RETURN inventory - that is how a machine hands a result back to the
        // network - so the pattern itself has to go in through the block entity's own NBT, under "patterns"
        BlockEntity be = sl.getBlockEntity(provider);
        if (be == null) return;
        CompoundTag entry = new CompoundTag();
        entry.putInt("Slot", 0);
        ListTag patterns = new ListTag();
        patterns.add(pattern.save(sl.registryAccess(), entry));   // save() COPIES the prefix: the return value is the merged tag
        CompoundTag tag = be.saveWithoutMetadata(sl.registryAccess());
        tag.put("patterns", patterns);
        be.loadWithComponents(tag, sl.registryAccess());
        be.setChanged();
    }

    private static ListTag genericStacks(ItemStack... stacks) {
        ListTag list = new ListTag();
        for (ItemStack s : stacks) {
            CompoundTag t = new CompoundTag();
            t.putString("#t", "ae2:i");
            t.putString("id", BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
            t.putLong("#", s.getCount());
            list.add(t);
        }
        return list;
    }

    /**
     * Ask an ME Interface to keep N of something in stock. When the network cannot supply it, the interface asks the
     * network to CRAFT it ({@code InterfaceLogic.handleCrafting}), which is the whole trigger for this bay - no
     * terminal, no player. The config inventory is a list of GenericStacks under "config" in the interface's own NBT.
     */
    private static void stockRequest(ServerLevel sl, BlockPos iface, String id, int amount) {
        ItemStack want = item(id, 1);
        BlockEntity be = sl.getBlockEntity(iface);
        if (want.isEmpty() || be == null) return;
        CompoundTag tag = be.saveWithoutMetadata(sl.registryAccess());
        tag.put("config", genericStacks(want.copyWithCount(amount)));
        // without a Crafting Card the interface only ever PULLS from storage: InterfaceLogic.handleCrafting refuses to
        // ask the network to craft unless the card is installed, and the whole bay would just sit there
        ItemStack craftingCard = item("ae2:crafting_card", 1);
        if (!craftingCard.isEmpty()) {
            CompoundTag slot = new CompoundTag();
            slot.putInt("Slot", 0);
            ListTag ups = new ListTag();
            ups.add(craftingCard.save(sl.registryAccess(), slot));
            tag.put("upgrades", ups);
        }
        be.loadWithComponents(tag, sl.registryAccess());
        be.setChanged();
    }

    /**
     * The shape both cross-mod bays use, and the answer to "one Pattern Provider, five machines": a chest drips
     * ingredients through a hopper into a Batch Distributor in CLUSTER mode, which hands each whole batch to the next
     * machine that can swallow it; a Wireless Router with one filtered EXTRACT card per machine pulls the finished
     * product into one chest. Nothing in here knows which mod the machines come from - it is block ids and capabilities.
     */
    private static void machineBay(ServerLevel sl, boolean cluster, BlockPos dist, BlockPos[] machines, ItemStack[] feeds,
                                   BlockPos router, BlockPos out, Item product, ItemStack... supply) {
        set(sl, dist, OmniLogistics.DISTRIBUTOR.get());
        if (sl.getBlockEntity(dist) instanceof com.mertokan.omnilogistics.distributor.DistributorBlockEntity d) {
            if (cluster) d.toggleMode();
            d.upgrades.setStackInSlot(0, new ItemStack(OmniLogistics.PARALLEL_UPGRADE.get(), 2));
            int lane = 0;
            for (BlockPos m : machines)
                for (ItemStack f : feeds) {
                    if (lane >= com.mertokan.omnilogistics.distributor.DistributorBlockEntity.LANES) break;
                    // every card binds to the top face and nothing else: which face actually takes the ingredient is the
                    // mod's problem now (auto input), which is exactly what this bay is here to prove
                    // CLUSTER routes by machine, so its cards stay unfiltered; SPLIT routes by filter, so each lane names its own
                    d.cards.setStackInSlot(lane++, cluster ? card(sl, CardKind.ITEM, m, Direction.UP, LogisticsCardItem.INSERT)
                        : laneCard(sl, m, Direction.UP, f.getItem()));
                }
        }
        set(sl, dist.above(), Blocks.HOPPER);                                                // stands in for a Pattern Provider
        chest(sl, dist.above().above(), supply);

        chest(sl, out);
        set(sl, router, OmniLogistics.ROUTER.get());
        if (sl.getBlockEntity(router) instanceof RouterBlockEntity r) {
            for (int k = 0; k < machines.length && k < RouterBlockEntity.CARDS - 1; k++)
                r.cards.setStackInSlot(k, pullCard(sl, machines[k], Direction.UP, product));   // auto output finds the face
            r.cards.setStackInSlot(Math.min(machines.length, RouterBlockEntity.CARDS - 1),
                card(sl, CardKind.ITEM, out, Direction.UP, LogisticsCardItem.INSERT));
            r.upgrades.setStackInSlot(0, new ItemStack(OmniLogistics.SPEED_UPGRADE.get(), 3));   // 8 card actions a cycle
            r.energy.receiveEnergy(r.energy.getMaxEnergyStored(), false);
        }
    }

    private static ItemStack item(String id, int count) {

        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).map(i -> new ItemStack(i, count)).orElse(ItemStack.EMPTY);
    }

    private static void pipe(ServerLevel sl, BlockPos pos, PipeType type, PipeTier tier) {
        set(sl, pos, OmniLogistics.pipe(type, tier).get());
    }

    private static void mode(ServerLevel sl, BlockPos pos, Direction d, ConduitBlockEntity.Mode m) {
        if (!(sl.getBlockEntity(pos) instanceof ConduitBlockEntity c)) return;
        byte[] modes = c.modes();
        modes[d.ordinal()] = (byte) m.ordinal();
        c.setModes(modes);
    }

    private static void chest(ServerLevel sl, BlockPos pos, ItemStack... items) {
        set(sl, pos, Blocks.CHEST);
        if (sl.getBlockEntity(pos) instanceof ChestBlockEntity chest)
            for (int i = 0; i < items.length && i < 27; i++) chest.setItem(i, items[i]);
    }

    /** 27 stacks of 64 cycling through the given items (the mixed source chest). */
    private static ItemStack[] stacks(Item... items) {
        ItemStack[] out = new ItemStack[27];
        for (int i = 0; i < 27; i++) out[i] = new ItemStack(items[i % items.length], 64);
        return out;
    }

    private static void fillEnergy(ServerLevel sl, BlockPos pos) {
        IEnergyStorage e = sl.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
        if (e != null) e.receiveEnergy(Integer.MAX_VALUE, false);
    }

    private static Holder<Enchantment> ench(ServerLevel sl, ResourceKey<Enchantment> key) {
        return sl.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    /** item with (enchantment, level) pairs; works for gear and for enchanted books. */
    @SafeVarargs
    private static ItemStack enchanted(Item item, Object... pairs) {
        ItemStack s = new ItemStack(item);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            @SuppressWarnings("unchecked") Holder<Enchantment> h = (Holder<Enchantment>) pairs[i];
            int lvl = (Integer) pairs[i + 1];
            EnchantmentHelper.updateEnchantments(s, m -> m.set(h, lvl));
        }
        return s;
    }

    /** Standing oak sign facing south (toward the player), waxed so it cannot be edited by accident. Lines fit 15 chars. */
    private static void sign(ServerLevel sl, BlockPos pos, String... lines) {
        set(sl, pos, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 0));
        if (!(sl.getBlockEntity(pos) instanceof SignBlockEntity s)) return;
        SignText t = s.getText(true);
        for (int i = 0; i < lines.length && i < 4; i++) t = t.setMessage(i, Component.literal(lines[i]));
        s.setText(t, true);
        s.setWaxed(true);
    }
}
