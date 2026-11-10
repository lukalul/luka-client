package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngame.class)
public abstract class VideoVignetteMixin {
  @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$vignette(CallbackInfo ci) {
    if (!GlobalOptions.vignette) ci.cancel();
  }
}
