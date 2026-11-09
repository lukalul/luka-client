package dev.cbui.mixin;

import dev.cbui.feature.Features;
import dev.cbui.feature.betterhurtcam.BetterHurtCamModule;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class BetterHurtCamEntityRendererMixin {
  @Inject(method = "hurtCameraEffect", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$betterHurtCamToggle(float partialTicks, CallbackInfo ci) {
    try {
      BetterHurtCamModule module = Features.get(BetterHurtCamModule.class);
      if (module != null && module.enabled() && !module.enableHurtCam.get()) ci.cancel();
    } catch (Throwable ignored) { }
  }

  @Redirect(
      method = "hurtCameraEffect",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;rotate(FFFF)V"),
      require = 0)
  private void cbui$betterHurtCamRotate(float angle, float x, float y, float z) {
    try {
      BetterHurtCamModule module = Features.get(BetterHurtCamModule.class);
      if (module == null || !module.enabled() || !module.enableHurtCam.get()) {
        GlStateManager.rotate(angle, x, y, z);
        return;
      }
      if (y != 0f && (module.oldTypeIn18.get() || "OLD".equals(module.type.get()))) angle = 0f;
      GlStateManager.rotate((float) (angle * (double) module.multiplier.get()), x, y, z);
    } catch (Throwable ignored) {
      GlStateManager.rotate(angle, x, y, z);
    }
  }
}
