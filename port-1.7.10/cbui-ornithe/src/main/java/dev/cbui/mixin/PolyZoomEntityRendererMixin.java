package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.cbui.feature.Features;
import dev.cbui.feature.polyzoom.PolyZoomModule;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Applies luka's zoom state after the renderer has computed its normal camera values. */
@Mixin(EntityRenderer.class)
public abstract class PolyZoomEntityRendererMixin {
  @ModifyReturnValue(method = "getFOVModifier", at = @At("RETURN"), require = 0)
  private float cbui$pzFov(float fov, float partialTicks, boolean useFovSetting) {
    try {
      PolyZoomModule m = Features.get(PolyZoomModule.class);
      return m == null ? fov : m.zoomFov(fov, partialTicks, useFovSetting);
    } catch (Throwable ignored) { return fov; }
  }

  @ModifyExpressionValue(
      method = {"updateCameraAndRender", "updateRenderer"},
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;smoothCamera:Z", opcode = 180),
      require = 0)
  private boolean cbui$pzCinematic(boolean vanilla) {
    try {
      PolyZoomModule m = Features.get(PolyZoomModule.class);
      return vanilla || m != null && m.useCinematicCamera();
    } catch (Throwable ignored) { return vanilla; }
  }

  @ModifyExpressionValue(
      method = {"updateCameraAndRender", "updateRenderer"},
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;mouseSensitivity:F", opcode = 180),
      require = 0)
  private float cbui$pzSensitivity(float vanilla) {
    try {
      PolyZoomModule m = Features.get(PolyZoomModule.class);
      return m == null ? vanilla : m.relativeSensitivity(vanilla);
    } catch (Throwable ignored) { return vanilla; }
  }

  @ModifyArg(
      method = "updateRenderer",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MouseFilter;smooth(FF)F"),
      index = 1,
      require = 0)
  private float cbui$pzSmoothness(float smoother) {
    try {
      PolyZoomModule m = Features.get(PolyZoomModule.class);
      return m == null ? smoother : m.cinematicSmoothness(smoother);
    } catch (Throwable ignored) { return smoother; }
  }

  /** The bob amplitude local (cameraYaw lerp) in setupViewBobbing: float locals are partialTicks, f, f1, f2 (ordinal 3), f3. */
  @ModifyVariable(method = "setupViewBobbing", at = @At("STORE"), ordinal = 3, require = 0)
  private float cbui$pzBobbing(float bob) {
    try {
      PolyZoomModule m = Features.get(PolyZoomModule.class);
      return m == null ? bob : m.relativeViewBobbing(bob);
    } catch (Throwable ignored) { return bob; }
  }

  @ModifyExpressionValue(
      method = "updateCameraAndRender",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;hideGUI:Z", opcode = 180),
      require = 0)
  private boolean cbui$pzHideHud(boolean vanilla) {
    try {
      PolyZoomModule m = Features.get(PolyZoomModule.class);
      return vanilla || m != null && m.hideHud();
    } catch (Throwable ignored) { return vanilla; }
  }
}
