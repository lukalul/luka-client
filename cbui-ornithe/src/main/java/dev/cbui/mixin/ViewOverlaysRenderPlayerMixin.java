package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.Cbui;
import dev.cbui.feature.viewoverlays.ViewOverlaysModule;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Overlay Tweaks: draws your first-person arm see-through while you are invisible. Vanilla sets the colour to opaque
 * white at the start of these methods, so the fade goes around the model call itself and is undone in a finally block.
 */
@Mixin(RenderPlayer.class)
public abstract class ViewOverlaysRenderPlayerMixin {
  @WrapOperation(method = "renderRightArm",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelPlayer;renderRightArm()V"),
      require = 0)
  private void cbui$fadeRightArm(ModelPlayer model, Operation<Void> original) {
    ViewOverlaysModule module = cbui$startFade();
    try {
      original.call(model);
    } finally {
      cbui$endFade(module);
    }
  }

  @WrapOperation(method = "renderLeftArm",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelPlayer;renderLeftArm()V"),
      require = 0)
  private void cbui$fadeLeftArm(ModelPlayer model, Operation<Void> original) {
    ViewOverlaysModule module = cbui$startFade();
    try {
      original.call(model);
    } finally {
      cbui$endFade(module);
    }
  }

  private static ViewOverlaysModule cbui$startFade() {
    try {
      ViewOverlaysModule module = ViewOverlaysModule.active();
      if (module != null) module.armFadeOn();
      return module;
    } catch (Throwable error) {
      Cbui.reportOnce("Overlay Tweaks arm fade", error);
      return null;
    }
  }

  private static void cbui$endFade(ViewOverlaysModule module) {
    if (module == null) return;
    try {
      module.armFadeOff();
    } catch (Throwable error) {
      Cbui.reportOnce("Overlay Tweaks arm fade", error);
    }
  }
}
