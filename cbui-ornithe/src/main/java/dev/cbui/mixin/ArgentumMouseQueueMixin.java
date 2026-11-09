package dev.cbui.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps clicks from being lost on the OpenGL renderer. Its mouse layer queues one event per mouse report, the queue
 * holds 200 and throws away whatever arrives once it is full, clicks included, and the game empties it only once a
 * tick. A fast mouse moving during a fight fills it. In the world nothing reads motion events: aim comes from the
 * totals kept beside the queue, so skipping them changes nothing but the room left for clicks.
 */
@Pseudo
@Mixin(targets = "io.github.moehreag.legacylwjgl3.implementation.glfw.GLFWMouseImplementation", remap = false)
public abstract class ArgentumMouseQueueMixin {
  @Shadow(remap = false) private boolean grabbed;

  @Inject(method = "putMouseEventWithCoords", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
  private void cbui$skipMotionInWorld(byte button, byte state, double x, double y, int wheel, long nanos, CallbackInfo callback) {
    if (grabbed && button == -1 && wheel == 0) callback.cancel();
  }
}
