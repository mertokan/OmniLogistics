package com.mertokan.omnilogistics;

import com.mertokan.omnilogistics.core.Caps;
import com.mojang.serialization.Codec;
import com.mertokan.omnilogistics.core.FilterConfigPayload;
import com.mertokan.omnilogistics.core.FilterMenu;
import com.mertokan.omnilogistics.api.FilterRef;
import com.mertokan.omnilogistics.api.FilterSpec;
import com.mertokan.omnilogistics.core.OmniConfig;
import com.mertokan.omnilogistics.exposer.ExposerBlock;
import com.mertokan.omnilogistics.exposer.ExposerBlockEntity;
import com.mertokan.omnilogistics.extractor.ExtractorBlock;
import com.mertokan.omnilogistics.extractor.ExtractorBlockEntity;
import com.mertokan.omnilogistics.extractor.ExtractorMenu;
import com.mertokan.omnilogistics.distributor.DistributorBlock;
import com.mertokan.omnilogistics.distributor.DistributorBlockEntity;
import com.mertokan.omnilogistics.distributor.DistributorMenu;
import com.mertokan.omnilogistics.extractor.ExtractorModePayload;
import com.mertokan.omnilogistics.miner.MinerMenu;
import com.mertokan.omnilogistics.monitor.MonitorBlock;
import com.mertokan.omnilogistics.monitor.MonitorBlockEntity;
import com.mertokan.omnilogistics.miner.MinerTier;
import com.mertokan.omnilogistics.miner.VoidMinerBlock;
import com.mertokan.omnilogistics.miner.VoidMinerBlockEntity;
import com.mertokan.omnilogistics.pipe.EnergyCableBlockEntity;
import com.mertokan.omnilogistics.pipe.FluidPipeBlockEntity;
import com.mertokan.omnilogistics.pipe.PipeBlockEntity;
import com.mertokan.omnilogistics.pipe.PipeConfigPayload;
import com.mertokan.omnilogistics.pipe.PipeMenu;
import com.mertokan.omnilogistics.pipe.PipeTier;
import com.mertokan.omnilogistics.pipe.PipeType;
import com.mertokan.omnilogistics.pipe.SmartPipeBlock;
import com.mertokan.omnilogistics.router.CardKind;
import com.mertokan.omnilogistics.router.CardUpgradeRecipe;
import com.mertokan.omnilogistics.router.LogisticsCardItem;
import com.mertokan.omnilogistics.router.RouterBlock;
import com.mertokan.omnilogistics.router.RouterBlockEntity;
import com.mertokan.omnilogistics.router.RouterMenu;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Mod(OmniLogistics.MODID)
public class OmniLogistics {
    public static final String MODID = "omnilogistics";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);

    private static BlockBehaviour.Properties machine() {
        return BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.METAL);
    }

    // ---- conduits: 3 types x 5 tiers ----------------------------------------------------
    public static final Map<PipeType, Map<PipeTier, DeferredBlock<SmartPipeBlock>>> PIPES = new EnumMap<>(PipeType.class);
    public static final Map<PipeType, Map<PipeTier, DeferredItem<BlockItem>>> PIPE_ITEMS = new EnumMap<>(PipeType.class);
    static {
        for (PipeType type : PipeType.values()) {
            PIPES.put(type, new EnumMap<>(PipeTier.class));
            PIPE_ITEMS.put(type, new EnumMap<>(PipeTier.class));
            for (PipeTier tier : PipeTier.values()) {
                String name = pipeId(type, tier);
                DeferredBlock<SmartPipeBlock> block = BLOCKS.registerBlock(name, p -> new SmartPipeBlock(p, type, tier), () -> machine().noOcclusion());
                PIPES.get(type).put(tier, block);
                PIPE_ITEMS.get(type).put(tier, ITEMS.registerSimpleBlockItem(block));
            }
        }
    }

    public static String pipeId(PipeType type, PipeTier tier) {
        return tier.getSerializedName() + "_" + type.id;
    }

    public static DeferredBlock<SmartPipeBlock> pipe(PipeType type, PipeTier tier) {
        return PIPES.get(type).get(tier);
    }

    private static Block[] pipesOf(PipeType type) {
        return PIPES.get(type).values().stream().map(DeferredBlock::get).toArray(Block[]::new);
    }

    public static final Supplier<BlockEntityType<PipeBlockEntity>> ITEM_PIPE_BE = BLOCK_ENTITIES.register("item_pipe",
        () -> new BlockEntityType<>(PipeBlockEntity::new, pipesOf(PipeType.ITEM)));
    public static final Supplier<BlockEntityType<EnergyCableBlockEntity>> ENERGY_CABLE_BE = BLOCK_ENTITIES.register("energy_cable",
        () -> new BlockEntityType<>(EnergyCableBlockEntity::new, pipesOf(PipeType.ENERGY)));
    public static final Supplier<BlockEntityType<FluidPipeBlockEntity>> FLUID_PIPE_BE = BLOCK_ENTITIES.register("fluid_pipe",
        () -> new BlockEntityType<>(FluidPipeBlockEntity::new, pipesOf(PipeType.FLUID)));

    // ---- machines ---------------------------------------------------------------------------
    public static final DeferredBlock<ExposerBlock> EXPOSER = BLOCKS.registerBlock("inventory_exposer", ExposerBlock::new, OmniLogistics::machine);
    public static final DeferredBlock<RouterBlock> ROUTER = BLOCKS.registerBlock("wireless_router", RouterBlock::new, OmniLogistics::machine);
    public static final DeferredBlock<ExtractorBlock> EXTRACTOR = BLOCKS.registerBlock("component_extractor", ExtractorBlock::new, OmniLogistics::machine);
    public static final DeferredBlock<DistributorBlock> DISTRIBUTOR = BLOCKS.registerBlock("batch_distributor", DistributorBlock::new, OmniLogistics::machine);
    public static final DeferredBlock<MonitorBlock> MONITOR = BLOCKS.registerBlock("machine_monitor", MonitorBlock::new, OmniLogistics::machine);

    public static final DeferredItem<BlockItem> EXPOSER_ITEM = ITEMS.registerSimpleBlockItem(EXPOSER);
    public static final DeferredItem<BlockItem> ROUTER_ITEM = ITEMS.registerSimpleBlockItem(ROUTER);
    public static final DeferredItem<BlockItem> EXTRACTOR_ITEM = ITEMS.registerSimpleBlockItem(EXTRACTOR);
    public static final DeferredItem<BlockItem> DISTRIBUTOR_ITEM = ITEMS.registerSimpleBlockItem(DISTRIBUTOR);
    public static final DeferredItem<BlockItem> MONITOR_ITEM = ITEMS.registerSimpleBlockItem(MONITOR);

    public static final Supplier<BlockEntityType<ExposerBlockEntity>> EXPOSER_BE = BLOCK_ENTITIES.register("inventory_exposer",
        () -> new BlockEntityType<>(ExposerBlockEntity::new, EXPOSER.get()));
    public static final Supplier<BlockEntityType<RouterBlockEntity>> ROUTER_BE = BLOCK_ENTITIES.register("wireless_router",
        () -> new BlockEntityType<>(RouterBlockEntity::new, ROUTER.get()));
    public static final Supplier<BlockEntityType<ExtractorBlockEntity>> EXTRACTOR_BE = BLOCK_ENTITIES.register("component_extractor",
        () -> new BlockEntityType<>(ExtractorBlockEntity::new, EXTRACTOR.get()));
    public static final Supplier<BlockEntityType<DistributorBlockEntity>> DISTRIBUTOR_BE = BLOCK_ENTITIES.register("batch_distributor",
        () -> new BlockEntityType<>(DistributorBlockEntity::new, DISTRIBUTOR.get()));
    public static final Supplier<BlockEntityType<MonitorBlockEntity>> MONITOR_BE = BLOCK_ENTITIES.register("machine_monitor",
        () -> new BlockEntityType<>(MonitorBlockEntity::new, MONITOR.get()));

    // ---- void miner: 4 tiers, one block entity type ------------------------------------------
    public static final Map<MinerTier, DeferredBlock<VoidMinerBlock>> MINERS = new EnumMap<>(MinerTier.class);
    public static final Map<MinerTier, DeferredItem<BlockItem>> MINER_ITEMS = new EnumMap<>(MinerTier.class);
    static {
        for (MinerTier tier : MinerTier.values()) {
            DeferredBlock<VoidMinerBlock> block = BLOCKS.registerBlock(tier.getSerializedName() + "_void_miner", p -> new VoidMinerBlock(p, tier), OmniLogistics::machine);
            MINERS.put(tier, block);
            MINER_ITEMS.put(tier, ITEMS.registerSimpleBlockItem(block));
        }
    }
    public static final Supplier<BlockEntityType<VoidMinerBlockEntity>> MINER_BE = BLOCK_ENTITIES.register("void_miner",
        () -> new BlockEntityType<>(VoidMinerBlockEntity::new, MINERS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)));

    // ---- items --------------------------------------------------------------------------------
    public static final DeferredItem<Item> WRENCH = ITEMS.registerSimpleItem("wrench", p -> p.stacksTo(1));
    public static final Map<CardKind, DeferredItem<LogisticsCardItem>> CARDS = new EnumMap<>(CardKind.class);
    static {
        for (CardKind kind : CardKind.values())
            CARDS.put(kind, ITEMS.registerItem(kind == CardKind.ITEM ? "logistics_card" : kind.id(),
                p -> new LogisticsCardItem(p, kind, kind == CardKind.FLUID ? 4 : 1), q -> q.stacksTo(1)));
    }
    public static final DeferredItem<LogisticsCardItem> CARD = CARDS.get(CardKind.ITEM);
    /** Same card, more reference slots: 4 / 16 / 64 instead of 1. Everything that takes a Logistics Card takes these. */
    public static final Map<Integer, DeferredItem<LogisticsCardItem>> MULTI_CARDS = new java.util.LinkedHashMap<>();
    static {
        for (var e : Map.of("advanced", 4, "elite", 16, "ultimate", 64).entrySet())
            MULTI_CARDS.put(e.getValue(), ITEMS.registerItem(e.getKey() + "_logistics_card",
                p -> new LogisticsCardItem(p, CardKind.ITEM, e.getValue()), q -> q.stacksTo(1)));
    }
    public static final DeferredItem<LogisticsCardItem> ENERGY_CARD = CARDS.get(CardKind.ENERGY);
    public static final DeferredItem<Item> SPEED_UPGRADE = ITEMS.registerSimpleItem("speed_upgrade", p -> p.stacksTo(3));
    public static final DeferredItem<Item> CHUNK_UPGRADE = ITEMS.registerSimpleItem("chunk_upgrade", p -> p.stacksTo(1));
    /** Chunk tickets the Chunk Loader Upgrade hands out; registered on the mod bus, so old tickets survive a restart. */
    public static final net.neoforged.neoforge.common.world.chunk.TicketController CHUNK_TICKETS =
        new net.neoforged.neoforge.common.world.chunk.TicketController(net.minecraft.resources.Identifier.fromNamespaceAndPath(MODID, "router"));
    public static final DeferredItem<Item> PARALLEL_UPGRADE = ITEMS.registerSimpleItem("parallel_upgrade", p -> p.stacksTo(2));
    public static final DeferredItem<Item> RANGE_UPGRADE = ITEMS.registerSimpleItem("range_upgrade", p -> p.stacksTo(3));
    public static final DeferredItem<Item> GEM_MODULE = ITEMS.registerSimpleItem("gem_module", p -> p.stacksTo(1));
    public static final DeferredItem<Item> FUSION_MODULE = ITEMS.registerSimpleItem("fusion_module", p -> p.stacksTo(1));

    public static final Supplier<RecipeSerializer<CardUpgradeRecipe>> CARD_UPGRADE_RECIPE = RECIPES.register("card_upgrade",
        () -> CardUpgradeRecipe.SERIALIZER);

    /** Recipe conditions, so the module flags can switch recipes off (see core/ModuleCondition). */
    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.neoforged.neoforge.common.conditions.ICondition>> CONDITIONS =
        DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.CONDITION_CODECS, MODID);
    public static final Supplier<com.mojang.serialization.MapCodec<? extends net.neoforged.neoforge.common.conditions.ICondition>> MODULE_CONDITION =
        CONDITIONS.register("module", () -> com.mertokan.omnilogistics.core.ModuleCondition.CODEC);

    // ---- menus --------------------------------------------------------------------------------
    public static final Supplier<MenuType<FilterMenu>> FILTER_MENU = MENUS.register("filter", () -> IMenuTypeExtension.create(FilterMenu::new));
    public static final Supplier<MenuType<PipeMenu>> PIPE_MENU = MENUS.register("pipe", () -> IMenuTypeExtension.create(PipeMenu::new));
    public static final Supplier<MenuType<RouterMenu>> ROUTER_MENU = MENUS.register("router", () -> IMenuTypeExtension.create(RouterMenu::new));
    public static final Supplier<MenuType<ExtractorMenu>> EXTRACTOR_MENU = MENUS.register("extractor", () -> IMenuTypeExtension.create(ExtractorMenu::new));
    public static final Supplier<MenuType<MinerMenu>> MINER_MENU = MENUS.register("miner", () -> IMenuTypeExtension.create(MinerMenu::new));
    public static final Supplier<MenuType<DistributorMenu>> DISTRIBUTOR_MENU = MENUS.register("distributor", () -> IMenuTypeExtension.create(DistributorMenu::new));

    // ---- data components ----------------------------------------------------------------------
    public static final Supplier<DataComponentType<GlobalPos>> CARD_TARGET = COMPONENTS.register("card_target",
        () -> DataComponentType.<GlobalPos>builder().persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC).build());
    public static final Supplier<DataComponentType<Direction>> CARD_SIDE = COMPONENTS.register("card_side",
        () -> DataComponentType.<Direction>builder().persistent(Direction.CODEC).networkSynchronized(Direction.STREAM_CODEC).build());
    public static final Supplier<DataComponentType<Integer>> CARD_MODE = COMPONENTS.register("card_mode",
        () -> DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    /** The block a card is bound to, by id: nobody remembers a coordinate a day later, but they remember a furnace. */
    public static final Supplier<DataComponentType<String>> CARD_TARGET_BLOCK = COMPONENTS.register("card_target_block",
        () -> DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
    public static final Supplier<DataComponentType<Integer>> CARD_SPEED = COMPONENTS.register("card_speed",
        () -> DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final Supplier<DataComponentType<FilterRef>> CARD_FILTER = COMPONENTS.register("card_filter",
        () -> DataComponentType.<FilterRef>builder().persistent(FilterRef.CODEC).networkSynchronized(FilterRef.STREAM_CODEC).build());
    public static final Supplier<DataComponentType<Integer>> CARD_FLAGS = COMPONENTS.register("card_flags",
        () -> DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final Supplier<DataComponentType<List<String>>> CARD_TAGS = COMPONENTS.register("card_tags",
        () -> DataComponentType.<List<String>>builder().persistent(Codec.STRING.listOf())
            .networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(FilterSpec.MAX_TAGS))).build());
    public static final Supplier<DataComponentType<List<String>>> CARD_COMPONENTS = COMPONENTS.register("card_components",
        () -> DataComponentType.<List<String>>builder().persistent(Codec.STRING.listOf())
            .networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(FilterSpec.MAX_COMPONENTS))).build());
    public static final Supplier<DataComponentType<List<com.mertokan.omnilogistics.api.NbtRule>>> CARD_NBT = COMPONENTS.register("card_nbt",
        () -> DataComponentType.<List<com.mertokan.omnilogistics.api.NbtRule>>builder()
            .persistent(com.mertokan.omnilogistics.api.NbtRule.CODEC.listOf())
            .networkSynchronized(com.mertokan.omnilogistics.api.NbtRule.LIST_STREAM_CODEC).build());
    /** Generic "socket" component: item stacks embedded in gear. The Component Module extracts them; datapacks and tests can use it. */
    public static final Supplier<DataComponentType<List<ItemStack>>> EMBEDDED_ITEMS = COMPONENTS.register("embedded_items",
        () -> DataComponentType.<List<ItemStack>>builder().persistent(ItemStack.CODEC.listOf())
            .networkSynchronized(ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(64))).build());

    public static final Supplier<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.omnilogistics"))
        .icon(() -> PIPE_ITEMS.get(PipeType.ITEM).get(PipeTier.BASIC).get().getDefaultInstance())
        .displayItems((params, out) -> {
            for (PipeType type : PipeType.values())
                for (PipeTier tier : PipeTier.values()) out.accept(PIPE_ITEMS.get(type).get(tier).get());
            out.accept(EXPOSER_ITEM.get());
            out.accept(ROUTER_ITEM.get());
            out.accept(EXTRACTOR_ITEM.get());
            out.accept(DISTRIBUTOR_ITEM.get());
            out.accept(MONITOR_ITEM.get());
            if (com.mertokan.omnilogistics.core.OmniConfig.moduleEnabled("mining"))
                for (MinerTier tier : MinerTier.values()) out.accept(MINER_ITEMS.get(tier).get());
            for (CardKind kind : CardKind.values()) out.accept(CARDS.get(kind).get());
            for (int cap : new int[]{4, 16, 64}) out.accept(MULTI_CARDS.get(cap).get());
            out.accept(WRENCH.get());
            out.accept(SPEED_UPGRADE.get());
            out.accept(CHUNK_UPGRADE.get());
            out.accept(PARALLEL_UPGRADE.get());
            out.accept(RANGE_UPGRADE.get());
            out.accept(GEM_MODULE.get());
            out.accept(FUSION_MODULE.get());
        })
        .build());

    public OmniLogistics(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, OmniConfig.SPEC);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        COMPONENTS.register(bus);
        TABS.register(bus);
        RECIPES.register(bus);
        CONDITIONS.register(bus);
        bus.addListener((net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent e) -> e.register(CHUNK_TICKETS));
        bus.addListener(this::registerPayloads);
        bus.addListener(this::registerCapabilities);
        GameTests.register(bus);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent e) {
        e.registrar("1")
            .playToServer(FilterConfigPayload.TYPE, FilterConfigPayload.CODEC, FilterConfigPayload::handle)
            .playToServer(PipeConfigPayload.TYPE, PipeConfigPayload.CODEC, PipeConfigPayload::handle)
            .playToServer(ExtractorModePayload.TYPE, ExtractorModePayload.CODEC, ExtractorModePayload::handle)
            .playToServer(com.mertokan.omnilogistics.distributor.DistributorModePayload.TYPE, com.mertokan.omnilogistics.distributor.DistributorModePayload.CODEC, com.mertokan.omnilogistics.distributor.DistributorModePayload::handle)
            .playToServer(com.mertokan.omnilogistics.core.IntervalPayload.TYPE, com.mertokan.omnilogistics.core.IntervalPayload.CODEC, com.mertokan.omnilogistics.core.IntervalPayload::handle);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent e) {
        e.registerBlockEntity(Capabilities.Item.BLOCK, ITEM_PIPE_BE.get(), Caps.items(PipeBlockEntity::handlerFor));
        e.registerBlockEntity(Capabilities.Energy.BLOCK, ENERGY_CABLE_BE.get(), Caps.energy(EnergyCableBlockEntity::handlerFor));
        e.registerBlockEntity(Capabilities.Fluid.BLOCK, FLUID_PIPE_BE.get(), Caps.fluids(FluidPipeBlockEntity::handlerFor));
        e.registerBlockEntity(Capabilities.Item.BLOCK, EXPOSER_BE.get(), ExposerBlockEntity::resourceView);   // forwards, so it passes the transaction through
        e.registerBlockEntity(Capabilities.Item.BLOCK, EXTRACTOR_BE.get(), Caps.items((be, side) -> be.sided));
        e.registerBlockEntity(Capabilities.Energy.BLOCK, EXTRACTOR_BE.get(), Caps.energy((be, side) -> be.energy));
        e.registerBlockEntity(Capabilities.Item.BLOCK, MINER_BE.get(), Caps.items((be, side) -> be.sided));
        e.registerBlockEntity(Capabilities.Item.BLOCK, DISTRIBUTOR_BE.get(), Caps.items((be, side) -> be.view(side)));   // this one line is the whole AE2 integration
        e.registerBlockEntity(Capabilities.Energy.BLOCK, MINER_BE.get(), Caps.energy((be, side) -> be.energy));
        e.registerBlockEntity(Capabilities.Energy.BLOCK, ROUTER_BE.get(), Caps.energy((be, side) -> be.energy));
        e.registerBlockEntity(Capabilities.Fluid.BLOCK, ROUTER_BE.get(), Caps.fluids((be, side) -> be.fluid));
    }
}
