package dev.envoy.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.envoy.module.chat.HexTextRenderer;
import dev.envoy.EnvoySettings;
import dev.envoy.module.chat.LunarFontBridge;
import dev.envoy.module.icon.IconTextRenderer;
import net.minecraft.client.gui.FontRenderer;

@Mixin(FontRenderer.class)
public class MixinFontRenderer {

    private static final char SECTION = '§';

    @Inject(
        method = "renderString(Ljava/lang/String;FFIZ)I",
        at = @At("HEAD"), cancellable = true, require = 0)
    private void envoy$renderExtendedFormatting(String text, float x, float y, int color, boolean shadowPass,
            CallbackInfoReturnable<Integer> result) {
        if (!EnvoySettings.chat || text == null) return;
        FontRenderer self = (FontRenderer) (Object) this;
        boolean hasIcon = IconTextRenderer.contains(text);
        if (!hasIcon && !hasRgbSequence(text)) return;
        Object lunarContext = LunarFontBridge.current(self);
        int adjustedColor = adjustedColor(self, lunarContext, x, y, color);
        if (adjustedColor == Integer.MIN_VALUE) return;
        try {
            int rightEdge = hasIcon
                ? IconTextRenderer.draw(self, text, x, y, adjustedColor, false, shadowPass)
                : HexTextRenderer.draw(self, text, x, y, adjustedColor, false, shadowPass);
            result.setReturnValue(rightEdge);
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
        if (!EnvoySettings.chat || text == null || !IconTextRenderer.contains(text)) return;
        FontRenderer renderer = (FontRenderer) (Object) this;
        result.setReturnValue(IconTextRenderer.width(renderer, text));
    }

    private static boolean hasRgbSequence(String text) {
        for (int marker = text.indexOf(SECTION); marker >= 0 && marker + 1 < text.length();
             marker = text.indexOf(SECTION, marker + 1)) {
            if (Character.toLowerCase(text.charAt(marker + 1)) == 'x') return true;
        }
        return false;
    }

    @Inject(method = "getFormatFromString", at = @At("HEAD"), cancellable = true, require = 0)
    private static void envoy$preserveRgbFormat(String text, CallbackInfoReturnable<String> result) {
        if (!EnvoySettings.chat || text == null || text.indexOf(SECTION + "x") < 0) return;
        result.setReturnValue(lastActiveFormat(text));
    }

    private static String lastActiveFormat(String text) {
        StringBuilder active = new StringBuilder();
        for (int marker = text.indexOf(SECTION); marker >= 0 && marker + 1 < text.length();
             marker = text.indexOf(SECTION, marker + 1)) {
            char code = text.charAt(marker + 1);
            if (Character.toLowerCase(code) == 'x' && marker + 13 < text.length()) {
                active.replace(0, active.length(), text.substring(marker, marker + 14));
                marker += 13;
            } else if (isColor(code)) {
                active.setLength(0);
                active.append(SECTION).append(code);
            } else if (isSpecial(code)) {
                active.append(SECTION).append(code);
            }
        }
        return active.toString();
    }

    private static boolean isColor(char c) {
        char l = Character.toLowerCase(c);
        return (l >= '0' && l <= '9') || (l >= 'a' && l <= 'f');
    }

    private static boolean isSpecial(char c) {
        char l = Character.toLowerCase(c);
        return (l >= 'k' && l <= 'o') || l == 'r';
    }
}
