package dev.cbui.mixin;

import dev.cbui.feature.nickhider.NickHiderModule;
import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Nick Hider: every text the game's font draws passes through renderStringAtPos (shadow and main pass, plain and split
 * strings), and getStringWidth measures it, so swapping the name in both keeps layouts that measure first (tab list,
 * nametags, scoreboard) the right width.
 */
@Mixin(FontRenderer.class)
public abstract class NickHiderFontRendererMixin {
  @ModifyVariable(method = "renderStringAtPos", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
  private String cbui$nickDrawn(String text) {
    return NickHiderModule.replace(text);
  }

  @ModifyVariable(method = "getStringWidth", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
  private String cbui$nickMeasured(String text) {
    return NickHiderModule.replace(text);
  }
}
