package com.mertokan.omnilogistics.core;

/** Which screen variant a FilterMenu shows: {@code sides} buttons cycling through {@code modeNames} (lang keys). */
public enum FilterLayout {
    EXPOSER(6, new String[]{"gui.omnilogistics.card_mode.off", "gui.omnilogistics.card_mode.target"}),
    CARD(1, new String[]{"gui.omnilogistics.card_mode.extract", "gui.omnilogistics.card_mode.insert"}),
    /** Cards without a direction (void, vacuum, detector) and cards sitting in a pipe: filter only. */
    CARD_NOMODE(0, new String[0]);

    public final int sides;
    public final String[] modeNames;

    FilterLayout(int sides, String[] modeNames) {
        this.sides = sides;
        this.modeNames = modeNames;
    }

    /** Length of the modes array exchanged with the host. */
    public int modeCount() {
        return sides;
    }
}
