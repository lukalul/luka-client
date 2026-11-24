package dev.cbui.mixin;

import dev.cbui.Cbui;
import dev.cbui.feature.viewoverlays.ViewOverlaysModule;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Overlay Tweaks: strength of the portal swirl and of the pumpkin mask. */
@Mixin(GuiIngame.class)
public abstract class ViewOverlaysGuiIngameMixin {
  @Inject(method = "renderPortal", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$portalShown(float timeInPortal, ScaledResolution resolution, CallbackInfo ci) {
    try {
      ViewOverlaysModule module = ViewOverlaysModule.active();
      if (module != null && module.skipPortal()) ci.cancel();
    } catch (Throwable error) {
      Cbui.reportOnce("Overlay Tweaks portal", error);
    }
  }

  @ModifyArg(method = "renderPortal",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V", ordinal = 0),
      index = 3, require = 0)
  private float cbui$portalAlpha(float alpha) {
    try {
      ViewOverlaysModule module = ViewOverlaysModule.active();
      return module == null ? alpha : module.portalAlpha(alpha);
    } catch (Throwable error) {
      Cbui.reportOnce("Overlay Tweaks portal", error);
      return alpha;
    }
  }

  @Inject(method = "renderPumpkinOverlay", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$pumpkinShown(ScaledResolution resolution, CallbackInfo ci) {
    try {
      ViewOverlaysModule module = ViewOverlaysModule.active();
      if (module != null && module.skipPumpkin()) ci.cancel();
    } catch (Throwable error) {
      Cbui.reportOnce("Overlay Tweaks pumpkin", error);
    }
  }

  @ModifyArg(method = "renderPumpkinOverlay",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V", ordinal = 0),
      index = 3, require = 0)
  private float cbui$pumpkinAlpha(float alpha) {
    try {
      ViewOverlaysModule module = ViewOverlaysModule.active();
      return module == null ? alpha : module.pumpkinAlpha(alpha);
    } catch (Throwable error) {
      Cbui.reportOnce("Overlay Tweaks pumpkin", error);
      return alpha;
    }
  }
}
