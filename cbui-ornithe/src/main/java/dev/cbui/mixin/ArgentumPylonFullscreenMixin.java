package dev.cbui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Argentum's video settings screen builds an exclusive-fullscreen group through Pylon, which constructs
 * LWJGL DisplayMode objects. The LWJGL 3 compatibility layer makes that constructor inaccessible, so
 * opening Video Settings crashed with an IllegalAccessError. Reporting exclusive fullscreen as unsupported
 * drops that one option group and leaves the rest of the screen intact.
 */
@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.compat.PylonFullscreen", remap = false)
public abstract class ArgentumPylonFullscreenMixin {
  @Shadow @Mutable public static boolean SUPPORTED;

  @Inject(method = "<clinit>", at = @At("TAIL"), require = 0)
  private static void cbui$disableExclusiveFullscreen(CallbackInfo ci) {
    SUPPORTED = false;
  }
}
