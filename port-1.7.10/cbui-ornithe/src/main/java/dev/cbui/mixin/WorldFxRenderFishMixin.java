package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.Cbui;
import dev.cbui.feature.worldfx.WorldEffectsModule;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderFish;
import net.minecraft.entity.projectile.EntityFishHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Render Tweaks: a cast fishing rod. The bobber is the first draw of the method, the line the second; only the line
 * gets the colour, thickness and blending, and the thickness is put back right after it. Vanilla gives the line an
 * opaque colour, so a see-through colour is set as RGBA instead.
 */
@Mixin(RenderFish.class)
public abstract class WorldFxRenderFishMixin {
  @Inject(method = "doRender(Lnet/minecraft/entity/projectile/EntityFishHook;DDDFF)V", at = @At("HEAD"),
      cancellable = true, require = 0)
  private void cbui$castShown(EntityFishHook hook, double x, double y, double z, float yaw, float partialTicks,
      CallbackInfo ci) {
    try {
      WorldEffectsModule module = WorldEffectsModule.active();
      if (module != null && module.skipCast()) ci.cancel();
    } catch (Throwable error) {
      Cbui.reportOnce("Render Tweaks fishing", error);
    }
  }

  @WrapOperation(method = "doRender(Lnet/minecraft/entity/projectile/EntityFishHook;DDDFF)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_I(I)V"),
      require = 0)
  private void cbui$lineColour(Tessellator tessellator, int rgb, Operation<Void> original) {
    try {
      WorldEffectsModule module = WorldEffectsModule.active();
      if (module != null && !module.lineIsVanilla()) {
        tessellator.setColorRGBA_I(module.lineRgb(), module.lineAlpha());
        return;
      }
    } catch (Throwable error) {
      Cbui.reportOnce("Render Tweaks fishing", error);
    }
    original.call(tessellator, rgb);
  }

  @WrapOperation(method = "doRender(Lnet/minecraft/entity/projectile/EntityFishHook;DDDFF)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()I", ordinal = 1),
      require = 0)
  private int cbui$lineDraw(Tessellator tessellator, Operation<Integer> original) {
    WorldEffectsModule module = null;
    try {
      module = WorldEffectsModule.active();
      if (module != null) module.beforeLine();
    } catch (Throwable error) {
      Cbui.reportOnce("Render Tweaks fishing", error);
    }
    try {
      return original.call(tessellator);
    } finally {
      if (module != null) {
        try {
          module.afterLine();
        } catch (Throwable error) {
          Cbui.reportOnce("Render Tweaks fishing", error);
        }
      }
    }
  }
}
