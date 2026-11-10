package dev.customsky;

import dev.luka.compat.GlStateManager;

/** The sky blend modes; factors and colour rules are ported from MCPatcher's BlendMethod (MIT, see LICENSE-customsky.txt). */
enum BlendMode {
    ALPHA(0x0302, 0x0303, false, true),
    ADD(0x0302, 1, false, true),
    SUBTRACT(0x0307, 0, true, false),
    MULTIPLY(0x0306, 0x0303, true, true),
    DODGE(1, 1, true, false),
    BURN(0, 0x0301, true, false),
    SCREEN(1, 0x0301, true, false),
    OVERLAY(0x0306, 0x0300, true, false),
    REPLACE(-1, -1, false, true);

    private final int source, destination;
    private final boolean rgbFromBrightness, alphaFromBrightness;

    BlendMode(int source, int destination, boolean rgbFromBrightness, boolean alphaFromBrightness) {
        this.source = source;
        this.destination = destination;
        this.rgbFromBrightness = rgbFromBrightness;
        this.alphaFromBrightness = alphaFromBrightness;
    }

    void apply(float brightness) {
        if (source < 0) {
            GlStateManager.disableBlend();
        } else {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(source, destination);
        }
        float rgb = rgbFromBrightness ? brightness : 1.0F;
        GlStateManager.color(rgb, rgb, rgb, alphaFromBrightness ? brightness : 1.0F);
    }

    static BlendMode parse(String text) {
        if (text == null) return ADD;
        try {
            return valueOf(text.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ADD;
        }
    }
}
