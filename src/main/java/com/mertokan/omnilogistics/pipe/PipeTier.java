package com.mertokan.omnilogistics.pipe;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** Mekanism-style tiers. items per op / ticks between ops; energy and fluid rates derive from the item rate. INFINITY is creative-only. */
public enum PipeTier implements StringRepresentable {
    BASIC(8, 8), ADVANCED(32, 6), ELITE(128, 4), ULTIMATE(512, 2), INFINITY(65536, 1);

    public static final Codec<PipeTier> CODEC = StringRepresentable.fromEnum(PipeTier::values);
    public final int items, interval;

    PipeTier(int items, int interval) {
        this.items = items;
        this.interval = interval;
    }

    /** FE per tick: 2k / 8k / 32k / 128k / 16M */
    public int energy() { return items * 250; }

    /** mB per op: 1k / 4k / 16k / 64k / 8M */
    public int fluid() { return items * 125; }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
