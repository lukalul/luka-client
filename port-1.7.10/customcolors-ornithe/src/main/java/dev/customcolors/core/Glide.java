package dev.customcolors.core;

/**
 * Moves a colour smoothly toward a changing target in real time; settles in about three seconds.
 * The first value after {@link #reset()} is taken at once.
 */
public final class Glide {
    /** Time constant in seconds; after three of them the colour is within 5% of the target. */
    private static final double SECONDS = 1.0;

    private float r;
    private float g;
    private float b;
    private long last;
    private boolean primed;

    public void reset() {
        primed = false;
    }

    public int toward(int target, long nowNanos) {
        float tr = (target >> 16) & 255;
        float tg = (target >> 8) & 255;
        float tb = target & 255;
        if (!primed) {
            r = tr;
            g = tg;
            b = tb;
            primed = true;
        } else {
            double dt = (nowNanos - last) / 1.0e9;
            if (dt > 0.0) {
                float k = (float) (1.0 - Math.exp(-dt / SECONDS));
                r += (tr - r) * k;
                g += (tg - g) * k;
                b += (tb - b) * k;
            }
        }
        last = nowNanos;
        return (Math.round(r) << 16) | (Math.round(g) << 8) | Math.round(b);
    }
}
