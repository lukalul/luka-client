package dev.customcolors.core;

/** Steps through an animation sequence, one call per client tick. */
public final class FrameClock {
    private final int[] frames;
    private final int[] ticks;
    private int step;
    private int elapsed;

    public FrameClock(int[] frames, int[] ticks) {
        this.frames = frames;
        this.ticks = ticks;
    }

    /** Advances one tick; true when the step changed. */
    public boolean tick() {
        if (frames.length <= 1) {
            return false;
        }
        elapsed++;
        if (elapsed < ticks[step]) {
            return false;
        }
        elapsed = 0;
        step = (step + 1) % frames.length;
        return true;
    }

    public int frame() {
        return frames[step];
    }

    public int step() {
        return step;
    }
}
