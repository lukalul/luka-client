package dev.customcolors.core;

/** Cuts a vertical strip of animation frames and fits each frame to the animated area. */
public final class FrameCutter {
    private FrameCutter() {
    }

    /**
     * Frames of the strip, each {@code w*h} ARGB pixels. A frame has the strip's width and the area's
     * aspect ratio; rows left over at the bottom are dropped; a strip shorter than one frame is one frame.
     */
    public static int[][] cut(int[] argb, int stripWidth, int stripHeight, int w, int h) {
        int frameHeight = Math.max(1, (int) Math.round((double) stripWidth * h / w));
        int count = stripHeight / frameHeight;
        if (count < 1) {
            return new int[][] {fit(argb, stripWidth, 0, stripWidth, stripHeight, w, h)};
        }
        int[][] out = new int[count][];
        for (int i = 0; i < count; i++) {
            out[i] = fit(argb, stripWidth, i * frameHeight, stripWidth, frameHeight, w, h);
        }
        return out;
    }

    /** Box-filters the region (sw x sh starting at row y0) down or up to w x h. */
    static int[] fit(int[] argb, int stride, int y0, int sw, int sh, int w, int h) {
        int[] out = new int[w * h];
        for (int ty = 0; ty < h; ty++) {
            int sy0 = ty * sh / h;
            int sy1 = Math.max(sy0 + 1, (ty + 1) * sh / h);
            for (int tx = 0; tx < w; tx++) {
                int sx0 = tx * sw / w;
                int sx1 = Math.max(sx0 + 1, (tx + 1) * sw / w);
                long a = 0;
                long r = 0;
                long g = 0;
                long b = 0;
                int n = 0;
                for (int sy = sy0; sy < sy1; sy++) {
                    for (int sx = sx0; sx < sx1; sx++) {
                        int p = argb[(y0 + sy) * stride + sx];
                        int pa = (p >>> 24) & 255;
                        a += pa;
                        r += ((p >> 16) & 255) * pa;
                        g += ((p >> 8) & 255) * pa;
                        b += (p & 255) * pa;
                        n++;
                    }
                }
                int pixel;
                if (a == 0) {
                    pixel = 0;
                } else {
                    pixel = ((int) (a / n) << 24) | ((int) (r / a) << 16) | ((int) (g / a) << 8) | (int) (b / a);
                }
                out[ty * w + tx] = pixel;
            }
        }
        return out;
    }
}
