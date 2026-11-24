package dev.customcolors.mixin;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.Switches;
import dev.customcolors.font.FontPack;
import dev.customcolors.font.HdFontFace;
import dev.customcolors.font.HdText;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * HD font sheets from the pack (drawn and measured with fractional advances), pack unicode pages,
 * and U+202F drawn and measured as a plain space.
 */
@Mixin(FontRenderer.class)
public abstract class CcFontRendererMixin {
    @Shadow @Final @Mutable private ResourceLocation locationFontTexture;
    @Shadow private int[] charWidth;
    @Shadow private float posX;
    @Shadow private float posY;
    @Shadow private boolean unicodeFlag;
    @Shadow @Final private TextureManager renderEngine;

    /** The vanilla sheet this renderer was made with. */
    @Unique private ResourceLocation customcolors$vanillaSheet;
    /** The pack's HD replacement in use, or null. */
    @Unique private HdFontFace customcolors$face;
    /**
     * Vanilla moves the pen by the whole part of each glyph's advance only; the fractions of an HD face are carried
     * here from glyph to glyph within one string, so the text is as wide as its measured width.
     */
    @Unique private float customcolors$carry;
    @Unique private float customcolors$carryBefore;
    @Unique private int customcolors$lastCell = -1;
    @Unique private float customcolors$lastX;

    @Inject(method = "renderStringAtPos", at = @At("HEAD"), require = 0)
    private void customcolors$newString(CallbackInfo ci) {
        customcolors$carry = 0.0F;
        customcolors$lastCell = -1;
    }

    /** The second draw of a bold glyph: the same cell again, one unit to the right, whose advance vanilla ignores. */
    @Unique
    private boolean customcolors$boldEcho(int cell) {
        return cell == customcolors$lastCell && Math.abs(posX - customcolors$lastX - 1.0F) < 0.001F;
    }

    /** Takes the exact advance, returns the whole step vanilla adds now and keeps the rest for the next glyph. */
    @Unique
    private float customcolors$step(int cell, float advance) {
        customcolors$carryBefore = customcolors$carry;
        customcolors$lastCell = cell;
        customcolors$lastX = posX;
        float exact = customcolors$carry + advance;
        int whole = (int) exact;
        customcolors$carry = exact - whole;
        return (float) whole;
    }

    @Inject(method = "readFontTexture", at = @At("HEAD"), require = 0)
    private void customcolors$chooseSheet(CallbackInfo ci) {
        try {
            if (customcolors$vanillaSheet == null) {
                customcolors$vanillaSheet = locationFontTexture;
            }
            HdFontFace face = FontPack.prepare(customcolors$vanillaSheet);
            customcolors$face = face;
            locationFontTexture = face != null ? face.sheet : customcolors$vanillaSheet;
        } catch (Throwable t) {
            customcolors$face = null;
            if (customcolors$vanillaSheet != null) {
                locationFontTexture = customcolors$vanillaSheet;
            }
            CustomColorsMod.reportOnce("HD font", t);
        }
    }

    @Inject(method = "readFontTexture", at = @At("RETURN"), require = 0)
    private void customcolors$applyWidths(CallbackInfo ci) {
        HdFontFace face = customcolors$face;
        if (face != null) {
            face.fillWidths(charWidth);
        }
    }

    @Inject(method = "renderCharAtPos", at = @At("HEAD"), cancellable = true, require = 0)
    private void customcolors$space(int cell, char ch, boolean italic, CallbackInfoReturnable<Float> cir) {
        if (ch != ' ' && ch != HdText.NARROW_SPACE) {
            return;
        }
        HdFontFace face = customcolors$face;
        if (face != null && !unicodeFlag) {
            cir.setReturnValue(customcolors$boldEcho(32) ? face.space : customcolors$step(32, face.space));
        } else if (ch == HdText.NARROW_SPACE && Switches.active()) {
            cir.setReturnValue(4.0F);
        }
    }

    @Inject(method = "renderDefaultChar", at = @At("HEAD"), cancellable = true, require = 0)
    private void customcolors$glyph(int cell, boolean italic, CallbackInfoReturnable<Float> cir) {
        HdFontFace face = customcolors$face;
        if (face == null) {
            return;
        }
        try {
            boolean echo = customcolors$boldEcho(cell);
            float x = posX + (echo ? customcolors$carryBefore : customcolors$carry);
            float advance = face.advance(cell);
            HdText.drawGlyph(renderEngine, locationFontTexture, cell, italic, x, posY, advance);
            cir.setReturnValue(echo ? advance : customcolors$step(cell, advance));
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("HD font drawing", t);
        }
    }

    @Inject(method = "getCharWidth", at = @At("HEAD"), cancellable = true, require = 0)
    private void customcolors$charWidth(char ch, CallbackInfoReturnable<Integer> cir) {
        if (ch != ' ' && ch != HdText.NARROW_SPACE) {
            return;
        }
        HdFontFace face = customcolors$face;
        if (face != null && !unicodeFlag) {
            cir.setReturnValue(face.roundedSpace);
        } else if (ch == HdText.NARROW_SPACE && Switches.active()) {
            cir.setReturnValue(4);
        }
    }

    @Inject(method = "getStringWidth", at = @At("HEAD"), cancellable = true, require = 0)
    private void customcolors$stringWidth(String text, CallbackInfoReturnable<Integer> cir) {
        HdFontFace face = customcolors$face;
        if (face == null || unicodeFlag) {
            return;
        }
        try {
            cir.setReturnValue(HdText.width((FontRenderer) (Object) this, face, text));
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("HD font measuring", t);
        }
    }

    @Inject(method = "getUnicodePageLocation", at = @At("HEAD"), cancellable = true, require = 0)
    private void customcolors$page(int page, CallbackInfoReturnable<ResourceLocation> cir) {
        try {
            ResourceLocation loc = FontPack.unicodePage(page);
            if (loc != null) {
                cir.setReturnValue(loc);
            }
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("unicode page", t);
        }
    }

    @ModifyVariable(method = "renderStringAtPos", at = @At("HEAD"), argsOnly = true, require = 0)
    private String customcolors$drawPlainSpaces(String text) {
        return Switches.active() ? HdText.plainSpaces(text) : text;
    }

    @ModifyVariable(method = "getStringWidth", at = @At("HEAD"), argsOnly = true, require = 0)
    private String customcolors$measurePlainSpaces(String text) {
        return Switches.active() ? HdText.plainSpaces(text) : text;
    }
}
