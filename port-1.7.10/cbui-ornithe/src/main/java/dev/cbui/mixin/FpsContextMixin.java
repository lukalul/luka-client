package dev.cbui.mixin;

import dev.cbui.config.FpsContexts;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Per-screen frame rate caps ({@link FpsContexts}). Priority 2000 puts this after the other frame-limit hooks (luka's unfocused
 * limit, Dynamic FPS), so the screen cap is the last word while the window has focus; in the background it does nothing.
 */
@Mixin(value = Minecraft.class, priority = 2000)
public abstract class FpsContextMixin {
  @Inject(method = "getLimitFramerate", at = @At("RETURN"), cancellable = true, require = 0)
  private void cbui$screenFpsCap(CallbackInfoReturnable<Integer> cir) {
    try {
      if (!Display.isActive()) return;
      Minecraft mc = (Minecraft) (Object) this;
      int cap = FpsContexts.cap(mc);
      if (cap > 0) {
        cir.setReturnValue(cap);
      } else if (mc.theWorld == null && mc.currentScreen != null && cir.getReturnValue() == 30) {
        // Minecraft's own 30 FPS for screens outside a world (see FpsContexts.outsideWorld)
        cir.setReturnValue(FpsContexts.outsideWorld(mc));
      }
    } catch (Throwable ignored) {
      // keep the normal limit
    }
  }
}
