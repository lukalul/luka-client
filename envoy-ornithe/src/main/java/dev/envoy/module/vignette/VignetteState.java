package dev.envoy.module.vignette;

import net.minecraft.util.ResourceLocation;

public final class VignetteState {
    private static final Frame NONE = new Frame(null, 0.0F);
    private static volatile Frame current = NONE;

    private VignetteState() { }

    public static void display(ResourceLocation texture, float opacity) {
        current = new Frame(texture, opacity);
    }

    public static void reset() {
        current = NONE;
    }

    public static ResourceLocation texture() {
        return current.texture;
    }

    public static float opacity() {
        return current.opacity;
    }

    private static final class Frame {
        private final ResourceLocation texture;
        private final float opacity;
        private Frame(ResourceLocation texture, float opacity) { this.texture = texture; this.opacity = opacity; }
    }
}
