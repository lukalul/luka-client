package dev.motionblur;

/** Shared math of the between-tick smoothing mixins (formula from framejob by Z1xus, MIT licence). */
public final class Smoothing {
    private Smoothing() { }

    /**
     * Vanilla moves a value a fixed fraction {@code rate} of the way to its target every tick. Drawn frames in between the
     * ticks are then on that curve rather than on the straight line vanilla uses:
     * {@code x(delta) = x0 + (x1 - x0) * (1 - (1 - rate)^delta) / rate}. Returns the matching interpolation factor.
     */
    public static float exponentialTickDelta(float tickDelta, float rate) {
        return (1 - (float) Math.pow(1 - rate, tickDelta)) / rate;
    }
}
