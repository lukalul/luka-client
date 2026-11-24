package dev.envoy.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.envoy.module.icon.IconTextRenderer;
import dev.envoy.module.icon.LunarFontBridge;
import net.minecraft.client.gui.FontRenderer;

/** Draws and measures server icons (":name:") inside text. */
@Mixin(FontRenderer.class)
public class MixinFontRenderer {

    @Inject(
        method = "renderString(Ljava/lang/String;FFIZ)I",
        at = @At("HEAD"), cancellable = true, require = 0)
    private void envoy$renderIconText(String text, float x, float y, int color, boolean shadowPass,
            CallbackInfoReturnable<Integer> result) {
        if (text == null || !IconTextRenderer.contains(text)) return;
        FontRenderer self = (FontRenderer) (Object) this;
        Object lunarContext = LunarFontBridge.current(self);
        int adjustedColor = adjustedColor(self, lunarContext, x, y, color);
        if (adjustedColor == Integer.MIN_VALUE) return;
        try {
            result.setReturnValue(IconTextRenderer.draw(self, text, x, y, adjustedColor, false, shadowPass));
        } finally {
            LunarFontBridge.restore(self, lunarContext);
        }
    }

    private static int adjustedColor(FontRenderer renderer, Object context, float x, float y, int original) {
        if (context == null) return original;
        int alpha = LunarFontBridge.alphaOf(context, x, y, original >>> 24 & 255);
        if (alpha < 4) return Integer.MIN_VALUE;
        LunarFontBridge.suspend(renderer);
        return alpha << 24 | original & 0xFFFFFF;
    }

    @Inject(method = "getStringWidth", at = @At("HEAD"), cancellable = true, require = 0)
    private void envoy$measureIconText(String text, CallbackInfoReturnable<Integer> result) {
        if (text == null || !IconTextRenderer.contains(text)) return;
        FontRenderer renderer = (FontRenderer) (Object) this;
        result.setReturnValue(IconTextRenderer.width(renderer, text));
    }
}
