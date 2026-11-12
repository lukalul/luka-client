package dev.cbui.mixin;

import dev.cbui.config.Resolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Resolution setting in borderless fullscreen: pylon's window reports the chosen size to the game. pylon maps the mouse
 * from the real window onto that size, so menus line up, and the Vulkan renderer stretches the picture over the real window
 * (its swapchain takes the real size from SDL, not from here). Exclusive fullscreen and a normal window are left alone.
 */
@Pseudo
@Mixin(targets = "pl.tomgirl.pylon.window.DisplaySdl", remap = false)
public abstract class PylonDisplayMixin {
  @Shadow(remap = false) public abstract boolean isFullscreen();
  @Shadow(remap = false) public abstract boolean isExclusiveFullscreen();

  private boolean cbui$scaled() {
    return Resolution.borderlessWidth > 0 && isFullscreen() && !isExclusiveFullscreen();
  }

  @Inject(method = "getWidth", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
  private void cbui$width(CallbackInfoReturnable<Integer> cir) {
    if (cbui$scaled()) cir.setReturnValue(Resolution.borderlessWidth);
  }

  @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
  private void cbui$height(CallbackInfoReturnable<Integer> cir) {
    if (cbui$scaled()) cir.setReturnValue(Resolution.borderlessHeight);
  }

  @Inject(method = "getDisplayMode", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
  private void cbui$mode(CallbackInfoReturnable<org.lwjgl.opengl.DisplayMode> cir) {
    // the game reads only the size from it (entering fullscreen)
    if (cbui$scaled()) cir.setReturnValue(new org.lwjgl.opengl.DisplayMode(Resolution.borderlessWidth, Resolution.borderlessHeight));
  }
}
