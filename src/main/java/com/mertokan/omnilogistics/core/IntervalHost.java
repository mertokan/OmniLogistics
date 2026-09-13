package com.mertokan.omnilogistics.core;

import net.minecraft.util.Mth;

/**
 * A machine whose clock the player types into the GUI: any value from {@link #MIN} to {@link #MAX} ticks, with nothing
 * to unlock first. Upgrades raise what one operation moves, never how often it happens.
 */
public interface IntervalHost {
    int MIN = 1, MAX = 200;

    /** Ticks per operation: what the player typed, or the machine's own default while they never did. */
    int interval();

    /** From {@link IntervalPayload}; implementations clamp with {@link #clamp}. */
    void setInterval(int ticks);

    static int clamp(int ticks) {
        return Mth.clamp(ticks, MIN, MAX);
    }
}
