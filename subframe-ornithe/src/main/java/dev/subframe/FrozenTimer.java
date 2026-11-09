package dev.subframe;

import net.minecraft.util.Timer;

/** A timer that never advances on its own: the replay decides when ticks run and what the partial tick is. */
final class FrozenTimer extends Timer {
    FrozenTimer() {
        super(20.0F);
    }

    @Override
    public void updateTimer() {
        elapsedTicks = 0;
    }
}
