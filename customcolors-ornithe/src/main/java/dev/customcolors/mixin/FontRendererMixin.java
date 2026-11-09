package dev.customcolors.mixin;

import dev.customcolors.HdFonts;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FontRenderer.class)
public abstract class FontRendererMixin {
    @Shadow @Final @Mutable private ResourceLocation locationFontTexture;
    @Shadow private int[] charWidth;
    @Shadow private boolean unicodeFlag;

    @Shadow public abstract int getCharWidth(char character);

    @Unique private ResourceLocation customcolors$defaultFont;
    @Unique private float[] customcolors$widths;
    @Unique private ResourceLocation[] customcolors$pages;

    @Inject(method = "readFontTexture", at = @At("HEAD"))
    private void customcolors$chooseFont(CallbackInfo ci) {
        if (customcolors$defaultFont == null) customcolors$defaultFont = locationFontTexture;
        ResourceLocation hd = HdFonts.replacement(customcolors$defaultFont);
        locationFontTexture = hd != null ? hd : customcolors$defaultFont;
        customcolors$widths = null;
        customcolors$pages = null;
    }

    @Inject(method = "readFontTexture", at = @At("RETURN"))
    private void customcolors$measure(CallbackInfo ci) {
        if (locationFontTexture != customcolors$defaultFont) {
            customcolors$widths = HdFonts.widths(locationFontTexture, charWidth);
        }
    }

    @ModifyConstant(method = "renderChar", constant = @Constant(floatValue = 4.0F))
    private float customcolors$space(float original) {
        return customcolors$widths != null ? customcolors$widths[32] : original;
    }

    @ModifyConstant(method = "renderDefaultChar", constant = @Constant(floatValue = 1.0F))
    private float customcolors$glyphGap(float original) {
        return customcolors$widths != null ? 0.0F : original;
    }

    @Inject(method = "renderDefaultChar", at = @At("RETURN"), cancellable = true)
    private void customcolors$advance(int ch, boolean italic, CallbackInfoReturnable<Float> cir) {
        if (customcolors$widths != null) cir.setReturnValue(customcolors$widths[ch]);
    }

    @Inject(method = "getUnicodePageLocation", at = @At("RETURN"), cancellable = true)
    private void customcolors$page(int page, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation[] pages = customcolors$pages;
        if (pages == null) pages = customcolors$pages = new ResourceLocation[256];
        ResourceLocation replacement = pages[page];
        if (replacement == null) replacement = pages[page] = HdFonts.unicodePage(cir.getReturnValue());
        cir.setReturnValue(replacement);
    }

    @Inject(method = "getStringWidth", at = @At("HEAD"), cancellable = true)
    private void customcolors$stringWidth(String text, CallbackInfoReturnable<Integer> cir) {
        float[] widths = customcolors$widths;
        if (widths == null || unicodeFlag || text == null) return;
        float total = 0.0F;
        boolean bold = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            float width = getCharWidth(c);
            if (width < 0.0F && i < text.length() - 1) {
                c = text.charAt(++i);
                if (c == 'l' || c == 'L') bold = true;
                else if (c == 'r' || c == 'R') bold = false;
                width = 0.0F;
            } else if (c < 128) {
                width = widths[c];
            }
            total += width;
            if (bold && width > 0.0F) total++;
        }
        cir.setReturnValue((int) total);
    }
}
