package com.mertokan.omnilogistics.core;

import net.neoforged.neoforge.energy.EnergyStorage;

/** Receive-only FE buffer with an internal consume(); machines expose it on every face. */
public class Battery extends EnergyStorage {
    public Battery(int capacity) {
        super(capacity, Integer.MAX_VALUE, 0);
    }

    public void consume(int n) {
        energy = Math.max(0, energy - n);
    }
}
