package dev.cbui.mixin;

import dev.cbui.feature.overlaytweaks.OverlayTweaksModule;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngame.class)
public abstract class OverlayTweaksGuiIngameMixin {
  @Inject(method = "renderPumpkinOverlay", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$skipPumpkin(ScaledResolution resolution, CallbackInfo ci) {
    OverlayTweaksModule module = OverlayTweaksModule.active();
    if (module != null && module.pumpkinAlpha() <= 0F) ci.cancel();
  }

  @ModifyArg(
      method = "renderPumpkinOverlay",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V", ordinal = 0),
      index = 3,
      require = 0)
  private float cbui$pumpkinAlpha(float vanillaAlpha) {
    OverlayTweaksModule module = OverlayTweaksModule.active();
    return module == null ? vanillaAlpha : module.pumpkinAlpha();
  }

  @Inject(method = "renderPortal", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$skipPortal(float strength, ScaledResolution resolution, CallbackInfo ci) {
    OverlayTweaksModule module = OverlayTweaksModule.active();
    if (module != null && module.portalAlphaScale() <= 0F) ci.cancel();
  }

  @ModifyArg(
      method = "renderPortal",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V", ordinal = 0),
      index = 3,
      require = 0)
  private float cbui$portalAlpha(float vanillaAlpha) {
    OverlayTweaksModule module = OverlayTweaksModule.active();
    return module == null ? vanillaAlpha : vanillaAlpha * module.portalAlphaScale();
  }
}
