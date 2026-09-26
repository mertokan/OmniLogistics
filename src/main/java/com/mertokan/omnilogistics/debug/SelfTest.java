package com.mertokan.omnilogistics.debug;

import com.mojang.logging.LogUtils;
import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.core.FilterScreen;
import com.mertokan.omnilogistics.miner.MinerScreen;
import com.mertokan.omnilogistics.miner.MinerTier;
import com.mertokan.omnilogistics.miner.VoidMinerBlockEntity;
import com.mertokan.omnilogistics.pipe.ConduitBlockEntity;
import com.mertokan.omnilogistics.pipe.EnergyCableBlockEntity;
import com.mertokan.omnilogistics.pipe.PipeBlockEntity;
import com.mertokan.omnilogistics.pipe.PipeScreen;
import com.mertokan.omnilogistics.pipe.PipeTier;
import com.mertokan.omnilogistics.pipe.PipeType;
import com.mertokan.omnilogistics.router.CardKind;
import com.mertokan.omnilogistics.router.RouterBlockEntity;
import com.mertokan.omnilogistics.router.RouterScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * End-to-end client check, only with -Domnilogistics.selftest=true (gradle runClientSelfTest):
 * creates a flat world, opens the card / router / in-router card / pipe / in-pipe card GUIs, then screenshots a showcase row
 * (every machine and conduit type) into screenshots/omni_showcase.png plus close-ups omni_machines.png / omni_pipes.png
 * and every opened GUI as omni_gui_*.png,
 * writes selftest.txt and quits. The run is configured with a 1600x900 window (build.gradle) so the close-ups have detail.
 * Nothing here runs in a normal game.
 */
@EventBusSubscriber(modid = OmniLogistics.MODID, value = Dist.CLIENT)
public final class SelfTest {
    private static final boolean ON = Boolean.getBoolean("omnilogistics.selftest");
    /** -Domnilogistics.prepare=true (gradle runClientPrepare): create the "OmniTest" world, build the {@link Showcase} at spawn, save, quit. */
    private static final boolean PREPARE = Boolean.getBoolean("omnilogistics.prepare");
    /** -Domnilogistics.autoworld=true (gradle runClient): skip the menus, open "OmniTest", creating it with the showcase if it is missing. */
    private static final boolean AUTO = Boolean.getBoolean("omnilogistics.autoworld");
    /** -Domnilogistics.press=true (gradle runClientPress): build the world, then tour it and photograph every bay. */
    private static final boolean PRESS = Boolean.getBoolean("omnilogistics.press");
    private static final String WORLD = ON ? "selftest" : "OmniTest";
    private static boolean fresh;   // AUTO: the world was just created, so build the showcase
    private static final Logger LOG = LogUtils.getLogger();
    private static final List<String> RESULTS = new ArrayList<>();
    private static int step, timer;
    private static boolean serverDone;
    private static BlockPos routerPos, pipePos, minerPos, distPos, extractorPos, rowCenter, showcaseOrigin;
    private static int verifyAt;      // PREPARE: the tick the world is checked, so the build is a real end-to-end test
    private static boolean verified;

    @SubscribeEvent
    static void server(ServerTickEvent.Post e) {
        if (!(ON || PREPARE || AUTO) || e.getServer().getPlayerList().getPlayers().isEmpty()) return;
        ServerPlayer p = e.getServer().getPlayerList().getPlayers().get(0);
        if (serverDone) {
            if (PREPARE && !verified && verifyAt > 0 && p.tickCount >= verifyAt) verify(p);
            return;
        }
        if (p.tickCount < 40) return;
        serverDone = true;
        if (AUTO && !fresh) return;   // an existing OmniTest world: nothing to build
        ServerLevel sl = p.level();
        sl.dimensionType().defaultClock().ifPresent(c -> e.getServer().clockManager().setTotalTicks(c, 6000));   // noon
        if (PREPARE || AUTO) {   // a world to play in: always noon, no mobs, the whole showcase around spawn
            sl.getGameRules().set(GameRules.ADVANCE_TIME, false, e.getServer());
            sl.getGameRules().set(GameRules.SPAWN_MOBS, false, e.getServer());
            showcaseOrigin = p.blockPosition();
            Showcase.build(sl, showcaseOrigin, p);
            verifyAt = p.tickCount + 700;   // let the demos actually run before the world is judged
            LOG.info("[SELFTEST] prepared the OmniTest world at {}", showcaseOrigin);
            return;
        }
        // camera hovers above the ground looking north; the interactive router / pipe sit behind it (south), the showcase row in front
        BlockPos base = p.blockPosition();
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
        p.teleportTo(sl, base.getX() + 0.5, base.getY() + 2.5, base.getZ() + 0.5, Set.<Relative>of(), 180, 22, true);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(OmniLogistics.CARDS.get(CardKind.VOID).get()));
        int slot = 9;   // every conduit in the inventory rows, so the GUI screenshots show whether the icons tell tiers and types apart
        for (PipeType type : PipeType.values())
            for (PipeTier tier : PipeTier.values()) p.getInventory().setItem(slot++, new ItemStack(OmniLogistics.PIPE_ITEMS.get(type).get(tier).get()));
        routerPos = base.relative(Direction.SOUTH, 2);
        place(sl, routerPos, OmniLogistics.ROUTER.get());
        if (sl.getBlockEntity(routerPos) instanceof RouterBlockEntity r) r.cards.setStackInSlot(0, new ItemStack(OmniLogistics.CARD.get()));
        pipePos = base.relative(Direction.SOUTH, 2).relative(Direction.EAST, 2);
        place(sl, pipePos.east(), Blocks.CHEST);
        place(sl, pipePos, OmniLogistics.pipe(PipeType.ITEM, PipeTier.BASIC).get());
        if (sl.getBlockEntity(pipePos) instanceof PipeBlockEntity pipe) pipe.cards.setStackInSlot(0, new ItemStack(OmniLogistics.CARD.get()));
        minerPos = base.relative(Direction.SOUTH, 2).relative(Direction.WEST, 2);
        place(sl, minerPos, OmniLogistics.MINERS.get(MinerTier.BASIC).get());
        if (sl.getBlockEntity(minerPos) instanceof VoidMinerBlockEntity m) m.energy.receiveEnergy(m.energy.getMaxEnergyStored(), false);
        extractorPos = base.relative(Direction.SOUTH, 2).relative(Direction.EAST, 4);
        place(sl, extractorPos, OmniLogistics.EXTRACTOR.get());
        distPos = base.relative(Direction.SOUTH, 2).relative(Direction.WEST, 4);
        place(sl, distPos, OmniLogistics.DISTRIBUTOR.get());
        if (sl.getBlockEntity(distPos) instanceof com.mertokan.omnilogistics.distributor.DistributorBlockEntity d)
            d.cards.setStackInSlot(0, new ItemStack(OmniLogistics.CARD.get()));
        p.getInventory().setItem(1, new ItemStack(OmniLogistics.MULTI_CARDS.get(16).get()));   // the 16-slot filter panel
        p.getInventory().setItem(2, nbtCard(sl));                                               // the NBT rules editor
        rowCenter = base.relative(Direction.NORTH, 7);
        showcase(sl, rowCenter);
        LOG.info("[SELFTEST] server: card in hand, router at {}, pipe at {}", routerPos, pipePos);
    }

    /** PREPARE only: read the world back after the demos have had time to run, and write what worked to selftest.txt. */
    private static void verify(ServerPlayer p) {
        verified = true;
        for (String line : Showcase.verify(p.level(), showcaseOrigin)) record(line);
        try {
            Files.write(Path.of("selftest.txt"), RESULTS);
        } catch (IOException ex) {
            LOG.error("[SELFTEST] cannot write results", ex);
        }
    }

    private static void place(ServerLevel sl, BlockPos pos, Block block) {
        sl.setBlock(pos, block.defaultBlockState(), 3);
    }

    /** Chest - item pipes (IN, plain, OUT) - chest, then the three machines with a cable and a fluid pipe hanging off the router. */
    private static void showcase(ServerLevel sl, BlockPos center) {
        int x = center.getX() - 5, y = center.getY(), z = center.getZ();
        place(sl, new BlockPos(x, y, z), Blocks.CHEST);
        place(sl, new BlockPos(x + 1, y, z), OmniLogistics.pipe(PipeType.ITEM, PipeTier.BASIC).get());
        place(sl, new BlockPos(x + 2, y, z), OmniLogistics.pipe(PipeType.ITEM, PipeTier.ADVANCED).get());
        place(sl, new BlockPos(x + 3, y, z), OmniLogistics.pipe(PipeType.ITEM, PipeTier.ELITE).get());
        place(sl, new BlockPos(x + 4, y, z), Blocks.CHEST);
        place(sl, new BlockPos(x + 5, y, z), OmniLogistics.EXTRACTOR.get());
        place(sl, new BlockPos(x + 6, y, z), OmniLogistics.EXPOSER.get());
        place(sl, new BlockPos(x + 7, y, z), OmniLogistics.ROUTER.get());
        place(sl, new BlockPos(x + 8, y, z), OmniLogistics.pipe(PipeType.ENERGY, PipeTier.ULTIMATE).get());
        place(sl, new BlockPos(x + 9, y, z), OmniLogistics.pipe(PipeType.ENERGY, PipeTier.ULTIMATE).get());
        place(sl, new BlockPos(x + 7, y + 1, z), OmniLogistics.pipe(PipeType.FLUID, PipeTier.ADVANCED).get());
        place(sl, new BlockPos(x + 7, y + 2, z), OmniLogistics.pipe(PipeType.FLUID, PipeTier.ADVANCED).get());
        place(sl, new BlockPos(x + 7, y + 3, z), OmniLogistics.pipe(PipeType.FLUID, PipeTier.ADVANCED).get());
        // a monitor facing the camera, watching the router: the machines close-up shows its screen
        sl.setBlock(new BlockPos(x + 6, y, z + 1), OmniLogistics.MONITOR.get().defaultBlockState()
            .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH), 3);
        if (sl.getBlockEntity(new BlockPos(x + 6, y, z + 1)) instanceof com.mertokan.omnilogistics.monitor.MonitorBlockEntity m) {
            ItemStack c = new ItemStack(OmniLogistics.CARD.get());
            com.mertokan.omnilogistics.router.LogisticsCardItem.bind(c, sl, new BlockPos(x + 7, y, z), Direction.UP);
            m.card.setStackInSlot(0, c);
        }
        place(sl, new BlockPos(x + 10, y, z), OmniLogistics.MINERS.get(MinerTier.ELITE).get());   // fed by the cables, mining from tick one
        if (sl.getBlockEntity(new BlockPos(x + 10, y, z)) instanceof VoidMinerBlockEntity m) m.energy.receiveEnergy(m.energy.getMaxEnergyStored(), false);
        if (sl.getBlockEntity(new BlockPos(x + 1, y, z)) instanceof ConduitBlockEntity c) setMode(c, Direction.WEST, ConduitBlockEntity.Mode.PULL);
        if (sl.getBlockEntity(new BlockPos(x + 3, y, z)) instanceof ConduitBlockEntity c) setMode(c, Direction.EAST, ConduitBlockEntity.Mode.PUSH);
        if (sl.getBlockEntity(new BlockPos(x + 8, y, z)) instanceof ConduitBlockEntity c) setMode(c, Direction.WEST, ConduitBlockEntity.Mode.PULL);
        if (sl.getBlockEntity(new BlockPos(x + 7, y + 1, z)) instanceof ConduitBlockEntity c) setMode(c, Direction.DOWN, ConduitBlockEntity.Mode.PULL);
        // something to see through the glass: a chest full of mixed items, water and FE in the router
        if (sl.getBlockEntity(new BlockPos(x, y, z)) instanceof ChestBlockEntity chest) {
            var kinds = new net.minecraft.world.item.Item[]{Items.OAK_LOG, Items.IRON_INGOT, Items.COBBLESTONE, Items.DIAMOND, Items.STONE, Items.GOLD_INGOT};
            for (int i = 0; i < 27; i++) chest.setItem(i, new ItemStack(kinds[i % kinds.length], 64));
        }
        // the destination chest is full of something else, so items pile up inside the glass pipes and stay visible
        if (sl.getBlockEntity(new BlockPos(x + 4, y, z)) instanceof ChestBlockEntity full)
            for (int i = 0; i < 27; i++) full.setItem(i, new ItemStack(Items.SAND, 64));
        if (sl.getBlockEntity(new BlockPos(x + 7, y, z)) instanceof RouterBlockEntity r) {
            r.fluid.fill(new FluidStack(Fluids.WATER, r.fluid.getCapacity()), IFluidHandler.FluidAction.EXECUTE);
            r.energy.receiveEnergy(r.energy.getMaxEnergyStored(), false);
        }
        // fill the conduits themselves too, so every segment has something to show
        for (int dy = 1; dy <= 3; dy++) {
            IFluidHandler h = com.mertokan.omnilogistics.core.Caps.fluids(sl, new BlockPos(x + 7, y + dy, z), null);
            if (h != null) h.fill(new FluidStack(Fluids.WATER, 100000), IFluidHandler.FluidAction.EXECUTE);
        }
        for (int dx = 8; dx <= 9; dx++) {
            var e = com.mertokan.omnilogistics.core.Caps.energy(sl, new BlockPos(x + dx, y, z), null);
            if (e != null) e.receiveEnergy(Integer.MAX_VALUE, false);
        }
    }

    private static void setMode(ConduitBlockEntity c, Direction d, ConduitBlockEntity.Mode m) {
        byte[] modes = c.modes();
        modes[d.ordinal()] = (byte) m.ordinal();
        c.setModes(modes);
    }

    @SubscribeEvent
    static void client(ClientTickEvent.Post e) {
        if (!ON && !PREPARE && !AUTO) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            tick(mc);
        } catch (Exception ex) {
            record("EXCEPTION " + ex);
            finish(mc);
        }
    }

    private static void tick(Minecraft mc) {
        if (step == 0) {
            if (mc.screen instanceof TitleScreen) {
                step = 1;
                if (AUTO && mc.getLevelSource().levelExists(WORLD)) {
                    LOG.info("[SELFTEST] opening {}", WORLD);
                    mc.createWorldOpenFlows().openWorld(WORLD, () -> LOG.error("[SELFTEST] could not open {}", WORLD));
                    return;
                }
                fresh = true;
                LOG.info("[SELFTEST] creating flat world {}", WORLD);
                LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, WorldOptions.defaultWithRandomSeed(),
                    regs -> regs.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            }
            return;
        }
        if (mc.player == null || mc.level == null) return;
        timer++;
        if (AUTO) { step = 99; return; }   // in the world: hands off from here on
        if (PREPARE) {   // wait for the in-world verification, then quit (the world stays)
            if (verified && PRESS) { press(mc); return; }
            if (verified && timer > 40) { LOG.info("[SELFTEST] OmniTest world ready, quitting"); step = 99; mc.stop(); }
            else if (serverDone && timer > 1600) { LOG.warn("[SELFTEST] verification never ran, quitting"); step = 99; mc.stop(); }
            return;
        }
        switch (step) {
            case 1 -> { // wait for the server hook, then right-click the card in the air
                if (mc.screen != null || !(mc.player.getMainHandItem().getItem() instanceof com.mertokan.omnilogistics.router.LogisticsCardItem) || routerPos == null) return;
                if (timer < 20) return;
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                next(2);
            }
            case 2 -> {
                if (timer < 20) return;
                record("card in hand right-click opens FilterScreen: " + (mc.screen instanceof FilterScreen) + " (screen=" + name(mc) + ")");
                shot(mc, "omni_gui_card.png");
                mc.player.closeContainer();
                next(3);
            }
            case 3 -> { // open the router block
                if (timer < 20 || mc.screen != null) return;
                click(mc, routerPos);
                next(4);
            }
            case 4 -> {
                if (timer < 20) return;
                boolean ok = mc.screen instanceof RouterScreen;
                record("right-click router opens RouterScreen: " + ok + " (screen=" + name(mc) + ")");
                if (!ok) { finish(mc); return; }
                shot(mc, "omni_gui_router.png");
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 0, 1, ContainerInput.PICKUP, mc.player);
                next(5);
            }
            case 5 -> {
                if (timer < 20) return;
                record("right-click card in router opens FilterScreen: " + (mc.screen instanceof FilterScreen) + " (screen=" + name(mc) + ")");
                mc.player.closeContainer();
                next(6);
            }
            case 6 -> { // open the pipe (an end: it touches a chest)
                if (timer < 20 || mc.screen != null) return;
                click(mc, pipePos);
                next(7);
            }
            case 7 -> {
                if (timer < 20) return;
                boolean ok = mc.screen instanceof PipeScreen;
                record("right-click end pipe opens PipeScreen: " + ok + " (screen=" + name(mc) + ")");
                if (!ok) { finish(mc); return; }
                shot(mc, "omni_gui_pipe.png");
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 0, 1, ContainerInput.PICKUP, mc.player);
                next(8);
            }
            case 8 -> {
                if (timer < 20) return;
                record("right-click card in pipe opens FilterScreen: " + (mc.screen instanceof FilterScreen) + " (screen=" + name(mc) + ")");
                shot(mc, "omni_gui_pipe_card.png");
                mc.player.closeContainer();
                next(9);
            }
            case 9 -> { // open the void miner
                if (timer < 20 || mc.screen != null) return;
                click(mc, minerPos);
                next(10);
            }
            case 10 -> {
                if (timer < 20) return;
                record("right-click void miner opens MinerScreen: " + (mc.screen instanceof MinerScreen) + " (screen=" + name(mc) + ")");
                shot(mc, "omni_gui_miner.png");
                mc.player.closeContainer();
                next(11);
            }
            case 11 -> { // the distributor GUI (two lane columns, upgrade slot, mode button)
                if (timer < 20 || mc.screen != null) return;
                click(mc, distPos);
                next(18);
            }
            case 18 -> {
                if (timer < 20) return;
                record("right-click distributor opens DistributorScreen: " + (mc.screen instanceof com.mertokan.omnilogistics.distributor.DistributorScreen) + " (screen=" + name(mc) + ")");
                shot(mc, "omni_gui_distributor.png");
                mc.player.closeContainer();
                next(19);
            }
            case 19 -> { // an Elite card in hand: the 16-reference filter panel comes off the bigger sheet
                if (timer < 20 || mc.screen != null) return;
                mc.player.getInventory().setSelectedSlot(1);
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                next(20);
            }
            case 20 -> {
                if (timer < 20) return;
                boolean ok = mc.screen instanceof FilterScreen;
                record("Elite card opens the 16-slot FilterScreen: " + ok + " (screen=" + name(mc) + ")");
                shot(mc, "omni_gui_card16.png");
                mc.player.closeContainer();
                next(40);
            }
            case 40 -> { // a card with a worn, enchanted sword as reference and three NBT rules on it
                if (timer < 20 || mc.screen != null) return;
                mc.player.getInventory().setSelectedSlot(2);
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                next(41);
            }
            case 41 -> {
                if (timer < 20) return;
                if (mc.screen instanceof FilterScreen fs) clickPanel(fs, 172 + 39, 130 + 9);   // the NBT button
                next(42);
            }
            case 42 -> {
                if (timer < 10) return;
                record("NBT rules editor opens with the card's rules: " + (mc.screen instanceof FilterScreen)
                    + " rules=" + com.mertokan.omnilogistics.router.LogisticsCardItem.spec(mc.player.getMainHandItem()).rules().size());
                shot(mc, "omni_gui_nbt_rules.png");
                if (mc.screen instanceof FilterScreen fs) clickPanel(fs, 6 + 244 - 16 - 52 + 2 + 24, 36 + 6);   // + Pick
                next(43);
            }
            case 43 -> {
                if (timer < 10) return;
                shot(mc, "omni_gui_nbt_pick.png");
                mc.player.closeContainer();
                mc.player.getInventory().setSelectedSlot(0);
                next(22);
            }
            case 22 -> { // the extractor GUI: mode button + the tick field
                if (timer < 20 || mc.screen != null) return;
                click(mc, extractorPos);
                next(23);
            }
            case 23 -> {
                if (timer < 20) return;
                record("right-click extractor opens ExtractorScreen: " + (mc.screen instanceof com.mertokan.omnilogistics.extractor.ExtractorScreen) + " (screen=" + name(mc) + ")");
                shot(mc, "omni_gui_extractor.png");
                mc.player.closeContainer();
                next(12);
            }
            case 12 -> { // let chunks rebuild, hide the HUD, then grab the whole row
                if (timer < 60) return;
                mc.options.hideGui = true;
                next(13);
            }
            case 13 -> {
                if (timer < 5) return;
                // client-side proof that the glass has something to show: synced contents + a renderer for each conduit type
                int x = rowCenter.getX() - 5, y = rowCenter.getY(), z = rowCenter.getZ();
                for (BlockPos bp : new BlockPos[]{new BlockPos(x + 1, y, z), new BlockPos(x + 2, y, z), new BlockPos(x + 3, y, z), new BlockPos(x + 9, y, z), new BlockPos(x + 7, y + 1, z)}) {
                    if (!(mc.level.getBlockEntity(bp) instanceof ConduitBlockEntity c)) { record("no conduit at " + bp); continue; }
                    String contents = c instanceof PipeBlockEntity p ? p.bufferStack().toString()
                        : c instanceof EnergyCableBlockEntity e ? e.stored() + " FE" : ((com.mertokan.omnilogistics.pipe.FluidPipeBlockEntity) c).fluid().toString();
                    record("client " + c.getClass().getSimpleName() + " contents=" + contents + " renderer=" + (mc.getBlockEntityRenderDispatcher().getRenderer(c) != null));
                }
                if (mc.level.getBlockEntity(new BlockPos(x + 10, y, z)) instanceof VoidMinerBlockEntity m)
                    record("client VoidMinerBlockEntity mining=" + m.mining + " pending=" + m.pending + " renderer=" + (mc.getBlockEntityRenderDispatcher().getRenderer(m) != null));
                shot(mc, "omni_showcase.png");
                camera(mc, rowCenter.getX() + 3.5, rowCenter.getY() + 0.6, rowCenter.getZ() + 4.6, 180, 14); // machines + the fluid tower
                next(14);
            }
            case 14 -> {
                if (timer < 40) return;
                shot(mc, "omni_machines.png");
                camera(mc, rowCenter.getX() - 2.5, rowCenter.getY() + 0.4, rowCenter.getZ() + 3.4, 180, 22); // chest - pipes - chest
                next(15);
            }
            case 15 -> {
                if (timer < 40) return;
                shot(mc, "omni_pipes.png");
                camera(mc, rowCenter.getX() - 2.5, rowCenter.getY() + 4.5, rowCenter.getZ() + 0.5, 180, 90); // straight down on the item pipes
                next(16);
            }
            case 16 -> {
                if (timer < 40) return;
                shot(mc, "omni_top.png");
                camera(mc, rowCenter.getX() + 6.3, rowCenter.getY() + 1.4, rowCenter.getZ() + 3.6, 170, 4); // the elite miner and its beam
                next(17);
            }
            case 17 -> {
                if (timer < 40) return;
                shot(mc, "omni_miner.png");
                next(21);
            }
            case 21 -> {
                if (timer < 10) return;
                finish(mc);
            }
            default -> {}
        }
    }

    /** Camera stops for the press kit: {dx, dy, dz, yaw, pitch} around the showcase origin, and the file to write. */
    private static final Object[][] TOUR = {
        {  0.5, 12.0,  -6.0, 180f, 42f, "omni_press_hero.png"},        // the whole site, from above
        { -0.5,  4.2,   5.0, 180f, 30f, "omni_press_pipes.png"},       // row 1: pipes, exposer, extractor, router
        {  0.5,  3.8,  -4.0, 180f, 30f, "omni_press_miners.png"},      // row 3: every miner tier
        {  4.5,  1.4,  -6.2, 180f,  3f, "omni_press_monitor.png"},     // the Machine Monitor screen, up close
        { 10.0,  3.8,  -2.5, 180f, 30f, "omni_press_empowerer.png"},   // Actually Additions: 4 stands, no cables
        { -6.5,  4.2, -13.0, 180f, 30f, "omni_press_ae2.png"},         // row 5: a real ME network
        { -8.5,  3.6, -19.0, 180f, 30f, "omni_press_assembler.png"},   // row 6: five AE2 assemblers in CLUSTER mode
        {  5.0,  3.6, -19.0, 180f, 30f, "omni_press_mekanism.png"},    // row 6: five enrichment chambers
        {  5.0,  3.6, -23.0, 180f, 30f, "omni_press_infuser.png"},     // row 7: two ingredients, two faces
        {  0.0,  3.6, -27.0, 180f, 30f, "omni_press_mecraft.png"},     // row 8: the Pattern Provider loop
        { -3.0,  4.2, -30.0, 180f, 30f, "omni_press_hall.png"},        // row 9: four machines, three mods, one Distributor
    };
    private static int pressAt = -1, pressWait, ritualStep;
    private static boolean pressShoot;

    /** Fly the camera around the finished world and photograph it. Only ever runs with -Domnilogistics.press=true. */
    private static void press(Minecraft mc) {
        if (showcaseOrigin == null) { mc.stop(); return; }
        if (pressWait-- > 0) return;
        if (pressShoot) {
            shot(mc, (String) TOUR[pressAt][5]);
            pressShoot = false;
            pressWait = 4;
            return;
        }
        if (++pressAt >= TOUR.length) { ritual(mc); return; }
        if (pressAt == 0) {
            mc.player.connection.sendCommand("gamerule sendCommandFeedback false");   // nothing of this belongs in a screenshot
            mc.player.connection.sendCommand("gamemode spectator");                   // a camera does not fall, and has no body
        }
        mc.gui.getChat().clearMessages(false);
        mc.options.hideGui = true;
        Object[] t = TOUR[pressAt];
        camera(mc, showcaseOrigin.getX() + (double) (Double) t[0], showcaseOrigin.getY() + (double) (Double) t[1],
            showcaseOrigin.getZ() + (double) (Double) t[2], (Float) t[3], (Float) t[4]);
        pressShoot = true;
        pressWait = 50;   // the chunks have to catch up with the camera before the shutter
    }

    /** The last two photographs: the card upgrade ritual in third and first person, HUD on - it is a gameplay shot. */
    private static void ritual(Minecraft mc) {
        switch (ritualStep++) {
            case 0, 3 -> {
                boolean third = ritualStep == 1;
                mc.options.hideGui = false;
                mc.player.connection.sendCommand("gamemode creative");
                mc.options.setCameraType(third ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
                // third person looks back at the player, so face them south and keep the showcase behind them
                camera(mc, showcaseOrigin.getX() + 0.5, showcaseOrigin.getY() + 1,
                    showcaseOrigin.getZ() + (third ? 10.5 : 7.5), third ? 0f : 180f, third ? 0f : 2f);
                mc.player.connection.sendCommand("item replace entity @s hotbar." + mc.player.getInventory().getSelectedSlot()
                    + " with omnilogistics:logistics_card");   // the slot the server thinks is selected, not slot 0
                mc.player.connection.sendCommand("item replace entity @s weapon.offhand with minecraft:gold_ingot");
                pressWait = 30;
            }
            case 1, 4 -> {
                mc.gui.getChat().clearMessages(false);
                // the use key has to be HELD: Minecraft releases the item the moment it is not down, which quietly
                // cancelled the whole ritual on every earlier attempt
                net.minecraft.client.KeyMapping.set(mc.options.keyUse.getKey(), true);
                net.minecraft.client.KeyMapping.click(mc.options.keyUse.getKey());
                pressWait = 28;                                              // halfway: particles at their busiest
            }
            case 2 -> { shot(mc, "omni_press_upgrade_3rd.png"); release(mc); pressWait = 40; }
            case 5 -> { shot(mc, "omni_press_upgrade_1st.png"); release(mc); pressWait = 20; }
            default -> { LOG.info("[SELFTEST] press kit done"); step = 99; mc.stop(); }
        }
    }

    private static void release(Minecraft mc) {
        net.minecraft.client.KeyMapping.set(mc.options.keyUse.getKey(), false);
        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(
            net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
            net.minecraft.core.BlockPos.ZERO, net.minecraft.core.Direction.DOWN));
    }

    /** Click a point given in panel coordinates, the way the player would. */
    private static void clickPanel(FilterScreen fs, int px, int py) {
        int left = (fs.width - FilterScreen.WIDTH) / 2, top = (fs.height - com.mertokan.omnilogistics.core.FilterMenu.height(1)) / 2;
        fs.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(left + px, top + py, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
    }

    /** A plain card whose reference is a worn, enchanted, renamed sword, with three rules picked from it. */
    private static ItemStack nbtCard(ServerLevel sl) {
        var ench = sl.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        ItemStack sword = new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
        sword.enchant(ench.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 4);
        sword.enchant(ench.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 3);
        sword.set(net.minecraft.core.component.DataComponents.DAMAGE, 120);
        sword.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Night Blade"));
        ItemStack card = new ItemStack(OmniLogistics.CARD.get());
        com.mertokan.omnilogistics.router.CardConfig.setFilter(card, sword);
        com.mertokan.omnilogistics.router.CardConfig.apply(card, com.mertokan.omnilogistics.router.CardConfig.modes(card),
            com.mertokan.omnilogistics.api.ComponentPredicateEngine.MATCH_ITEM | com.mertokan.omnilogistics.api.ComponentPredicateEngine.MATCH_NBT, List.of(), List.of(), List.of(
                new com.mertokan.omnilogistics.api.NbtRule(List.of("minecraft:damage"), com.mertokan.omnilogistics.api.NbtRule.Op.LT, "200", true),
                new com.mertokan.omnilogistics.api.NbtRule(List.of("minecraft:enchantments", "levels", "minecraft:sharpness"),
                    com.mertokan.omnilogistics.api.NbtRule.Op.GE, "3", true),
                new com.mertokan.omnilogistics.api.NbtRule(List.of("minecraft:custom_name"), com.mertokan.omnilogistics.api.NbtRule.Op.CONTAINS, "blade", true)));
        return card;
    }

    private static void shot(Minecraft mc, String file) {
        Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), 1, c -> {});
        record("screenshot: screenshots/" + file);
    }

    /** Move the camera with /tp (the world is created with cheats on). */
    private static void camera(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.0f %.0f", x, y, z, yaw, pitch));
    }

    private static void click(Minecraft mc, BlockPos pos) {
        Vec3 hit = Vec3.atCenterOf(pos).relative(Direction.UP, 0.5);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, pos, false));
    }

    private static void next(int s) {
        step = s;
        timer = 0;
    }

    private static String name(Minecraft mc) {
        return mc.screen == null ? "null" : mc.screen.getClass().getSimpleName();
    }

    private static void record(String line) {
        RESULTS.add(line);
        LOG.info("[SELFTEST] {}", line);
    }

    private static void finish(Minecraft mc) {
        step = 99;
        try {
            Files.write(Path.of("selftest.txt"), RESULTS);
        } catch (IOException ex) {
            LOG.error("[SELFTEST] cannot write results", ex);
        }
        mc.stop();
    }
}
