package com.mertokan.omnilogistics.miner;

import com.mojang.serialization.Codec;
import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.core.OmniConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Locale;

/** Same four tiers and colours as the conduits. Each tier halves the time and quadruples the FE per item; the loot table is per tier. */
public enum MinerTier implements StringRepresentable {
    BASIC(0x778290), ADVANCED(0x4a80b8), ELITE(0x8a5fd0), ULTIMATE(0xd9a832);

    public static final Codec<MinerTier> CODEC = StringRepresentable.fromEnum(MinerTier::values);
    public final int color;

    MinerTier(int color) {
        this.color = color;
    }

    /** Ticks per cycle: the default the GUI tick field starts at. */
    public int time() {
        return Math.max(1, OmniConfig.MINER_TIME.get() >> ordinal());
    }

    /** FE per item. */
    public int cost() {
        return OmniConfig.MINER_COST.get() << 2 * ordinal();
    }

    /** Internal buffer: 25 items worth. */
    public int capacity() {
        return (int) Math.min(Integer.MAX_VALUE, 25L * cost());
    }

    /** data/omnilogistics/loot_table/miner/[tier].json, datapack-editable. */
    public ResourceKey<LootTable> loot() {
        return ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(OmniLogistics.MODID, "miner/" + getSerializedName()));
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
