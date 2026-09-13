package com.mertokan.omnilogistics.router;

import java.util.Locale;

/** What a Logistics Card does when it sits in a router. Modular Routers modules + LaserIO cards, one item each. */
public enum CardKind {
    /** EXTRACT / INSERT items against the bound inventory, filtered. */
    ITEM(true, true),
    /** EXTRACT / INSERT FE against the bound block. */
    ENERGY(false, true),
    /** EXTRACT / INSERT fluid against the bound tank, filtered by the fluid in its reference containers. */
    FLUID(true, true),
    /** Destroys matching items that reach the router buffer. */
    VOID(true, false),
    /** Sucks matching dropped items around the router (or the bound block) into the buffer. */
    VACUUM(true, false),
    /** Right-clicks the bound block face with the buffer item, like a player. */
    ACTIVATOR(false, false),
    /** Breaks the bound block, drops go to the buffer. */
    BREAKER(false, false),
    /** Places the buffer item as a block at the bound position. */
    PLACER(false, false),
    /** Redstone 15 from the router while the bound inventory holds a matching item. */
    DETECTOR(true, false),
    /** Keeps the bound inventory topped up to one stack of what the router is carrying (x2 per overclock step). */
    STOCK(true, false);

    public final boolean hasFilter, hasMode;

    CardKind(boolean hasFilter, boolean hasMode) {
        this.hasFilter = hasFilter;
        this.hasMode = hasMode;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT) + "_card";
    }
}
