package dev.cbui.mixin;

import dev.cbui.gui.KeyText;
import net.minecraft.client.resources.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A language key no mod translated (key bindings, categories) is shown as words instead of the raw key. */
@Mixin(I18n.class)
public abstract class I18nMixin {
  @Inject(method = "format", at = @At("RETURN"), cancellable = true, require = 0)
  private static void cbui$readableKeys(String key, Object[] args, CallbackInfoReturnable<String> cir) {
    String result = cir.getReturnValue();
    if (result != null && result.equals(key) && KeyText.looksLikeKey(key)) cir.setReturnValue(KeyText.words(key));
  }
}
