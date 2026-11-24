package dev.customcolors.font;

import net.minecraft.util.ResourceLocation;

/** A pack's high-resolution replacement of one font sheet, with its measured advances. */
public final class HdFontFace {
    public final ResourceLocation sheet;
    /** Advance of each of the 256 cells in vanilla units; entry 32 is the space. */
    private final float[] advances;
    public final float space;
    public final int roundedSpace;

    HdFontFace(ResourceLocation sheet, float[] advances) {
        this.sheet = sheet;
        this.advances = advances;
        this.space = advances[32];
        this.roundedSpace = Math.round(space);
    }

    public float advance(int cell) {
        return advances[cell & 255];
    }

    /** Puts the rounded advances into vanilla's integer width table. */
    public void fillWidths(int[] charWidth) {
        int n = Math.min(charWidth.length, 256);
        for (int i = 0; i < n; i++) {
            charWidth[i] = Math.round(advances[i]);
        }
    }
}
