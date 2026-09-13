package com.mertokan.omnilogistics.core;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/omnilogistics-common.toml. Read at use time, never cached in statics. */
public final class OmniConfig {
    private OmniConfig() {}

    /** True when that module is on - and true while the config has not loaded yet, because recipes are read early and a
     *  half-built mod is worse than an extra recipe. */
    public static boolean moduleEnabled(String module) {
        if (!SPEC.isLoaded()) return true;
        return switch (module) {
            case "mining" -> ENABLE_MINING.get();
            case "transport" -> ENABLE_TRANSPORT.get();
            default -> true;
        };
    }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue EXTRACTOR_COST, EXTRACTOR_TIME, EXTRACTOR_CAPACITY;
    public static final ModConfigSpec.IntValue ROUTER_RANGE, ROUTER_ITEMS, ROUTER_ENERGY_RATE, ROUTER_ENERGY_CAPACITY, ROUTER_FLUID_RATE, ROUTER_FLUID_CAPACITY, VACUUM_RADIUS, ROUTER_CROSSDIM_FE, ROUTER_CHUNKS;
    public static final ModConfigSpec.IntValue MINER_TIME, MINER_COST;
    /** Module flags: a pack can ship the mod without half of it. Read through {@link #moduleEnabled}. */
    public static final ModConfigSpec.BooleanValue ENABLE_TRANSPORT, ENABLE_MINING;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("extractor");
        EXTRACTOR_COST = b.comment("FE per operation").defineInRange("costFE", 4000, 0, 10_000_000);
        EXTRACTOR_TIME = b.comment("Ticks per operation until the player types one into the GUI (1..200)").defineInRange("timeTicks", 100, 1, 20_000);
        EXTRACTOR_CAPACITY = b.comment("Internal FE buffer").defineInRange("capacityFE", 200_000, 1000, 1_000_000_000);
        b.pop();
        b.push("router");
        ROUTER_RANGE = b.comment("Card range in blocks before Range Upgrades").defineInRange("baseRange", 64, 1, 4096);
        ROUTER_ITEMS = b.comment("Items per move for a card without speed").defineInRange("itemsPerMove", 16, 1, 4096);
        ROUTER_ENERGY_RATE = b.comment("FE per move for an Energy Card without speed").defineInRange("energyPerMove", 20_000, 1, 1_000_000_000);
        ROUTER_ENERGY_CAPACITY = b.comment("Router FE buffer").defineInRange("energyCapacity", 400_000, 1000, 1_000_000_000);
        ROUTER_FLUID_RATE = b.comment("mB per move for a Fluid Card without speed").defineInRange("fluidPerMove", 4000, 1, 1_000_000_000);
        ROUTER_FLUID_CAPACITY = b.comment("Router fluid tank in mB").defineInRange("fluidCapacity", 32_000, 1000, 1_000_000_000);
        VACUUM_RADIUS = b.comment("Vacuum Card pickup radius in blocks").defineInRange("vacuumRadius", 6, 1, 64);
        ROUTER_CROSSDIM_FE = b.comment("FE per action for a card bound in another dimension (0 = free). Range does not apply across dimensions")
            .defineInRange("crossDimensionFE", 1000, 0, 1_000_000_000);
        ROUTER_CHUNKS = b.comment("How many chunks one router with a Chunk Loader Upgrade may keep loaded (its own chunk counts)")
            .defineInRange("loadedChunks", 9, 1, 64);
        b.pop();
        b.push("miner");
        MINER_TIME = b.comment("Ticks per cycle for the Basic Void Miner until the player types one into the GUI (1..200); every tier halves it").defineInRange("timeTicks", 200, 4, 20_000);
        MINER_COST = b.comment("FE per cycle for the Basic Void Miner; every tier multiplies it by 4, every Speed Upgrade doubles it. Buffer = 25 base cycles").defineInRange("costFE", 2000, 0, 10_000_000);
        b.pop();
        b.push("modules");
        ENABLE_TRANSPORT = b.comment("Conduits, router, exposer, extractor, distributor, monitor and the cards").define("transport", true);
        ENABLE_MINING = b.comment("Void Miners").define("mining", true);
        b.pop();
        SPEC = b.build();
    }
}
