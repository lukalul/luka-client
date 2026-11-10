package dev.cbui.mixin;

import dev.cbui.feature.overlaytweaks.OverlayTweaksModule;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngame.class)
public abstract class OverlayTweaksGuiIngameMixin {
  private static final String COLOR = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V";
  /** The portal overlay; this method has no name in the mappings. */
  private static final String PORTAL = "m_56920776(FII)V";

  @Inject(method = "renderPumpkinBlur", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$skipPumpkin(int width, int height, CallbackInfo ci) {
    OverlayTweaksModule module = OverlayTweaksModule.active();
    if (module != null && module.pumpkinAlpha() <= 0F) ci.cancel();
  }

  @ModifyArg(method = "renderPumpkinBlur", at = @At(value = "INVOKE", target = COLOR, ordinal = 0, remap = false), index = 3, require = 0)
  private float cbui$pumpkinAlpha(float vanillaAlpha) {
    OverlayTweaksModule module = OverlayTweaksModule.active();
    return module == null ? vanillaAlpha : module.pumpkinAlpha();
  }

  @Inject(method = PORTAL, at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$skipPortal(float strength, int width, int height, CallbackInfo ci) {
    OverlayTweaksModule module = OverlayTweaksModule.active();
    if (module != null && module.portalAlphaScale() <= 0F) ci.cancel();
  }

  @ModifyArg(method = PORTAL, at = @At(value = "INVOKE", target = COLOR, ordinal = 0, remap = false), index = 3, require = 0)
  private float cbui$portalAlpha(float vanillaAlpha) {
    OverlayTweaksModule module = OverlayTweaksModule.active();
    return module == null ? vanillaAlpha : vanillaAlpha * module.portalAlphaScale();
  }
}
