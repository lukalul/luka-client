package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** One switch for every drop shadow drawn with the vanilla font (drawStringWithShadow ends up here with dropShadow = true). */
@Mixin(FontRenderer.class)
public abstract class FontRendererMixin {
  @ModifyVariable(method = "drawString(Ljava/lang/String;IIIZ)I", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
  private boolean cbui$noShadow(boolean dropShadow) {
    return dropShadow && GlobalOptions.textShadow;
  }
}
