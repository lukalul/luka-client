package dev.customcolors.mixin;

import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Modern Java puts a narrow no-break space in dates ("8:56 AM"); the 1.7.10 font draws that character as a box. */
@Mixin(FontRenderer.class)
public abstract class FontRendererSpaceMixin {
    @ModifyVariable(method = "renderStringAtPos", at = @At("HEAD"), argsOnly = true, require = 0)
    private String customcolors$space(String text) {
        return text.indexOf('\u202F') >= 0 ? text.replace('\u202F', ' ') : text;
    }

    @ModifyVariable(method = "getStringWidth", at = @At("HEAD"), argsOnly = true, require = 0)
    private String customcolors$spaceWidth(String text) {
        return text != null && text.indexOf('\u202F') >= 0 ? text.replace('\u202F', ' ') : text;
    }
}
