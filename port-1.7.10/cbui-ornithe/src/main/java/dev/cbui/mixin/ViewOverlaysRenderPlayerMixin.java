package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.Cbui;
import dev.cbui.feature.viewoverlays.ViewOverlaysModule;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Overlay Tweaks: draws your bare first-person arm see-through while you are invisible. Vanilla sets the colour to
 * opaque white at the start of the method, so the fade goes around the model call itself and is undone in a finally
 * block.
 */
@Mixin(RenderPlayer.class)
public abstract class ViewOverlaysRenderPlayerMixin {
  @WrapOperation(method = "renderFirstPersonArm",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelRenderer;render(F)V"),
      require = 0)
  private void cbui$fadeArm(ModelRenderer arm, float unit, Operation<Void> original) {
    ViewOverlaysModule module = null;
    try {
      module = ViewOverlaysModule.active();
      if (module != null) module.armFadeOn();
    } catch (Throwable error) {
      Cbui.reportOnce("Overlay Tweaks arm fade", error);
    }
    try {
      original.call(arm, unit);
    } finally {
      if (module != null) {
        try {
          module.armFadeOff();
        } catch (Throwable error) {
          Cbui.reportOnce("Overlay Tweaks arm fade", error);
        }
      }
    }
  }
}
