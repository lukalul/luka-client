package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.Cbui;
import dev.cbui.feature.viewoverlays.ViewOverlaysModule;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Overlay Tweaks: invisible arm, underwater texture, on-screen flames and the in-block view. */
@Mixin(ItemRenderer.class)
public abstract class ViewOverlaysItemRendererMixin {
  @ModifyExpressionValue(method = "renderItemInFirstPerson",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/AbstractClientPlayer;isInvisible()Z"),
      require = 0)
  private boolean cbui$armWhileInvisible(boolean invisible) {
    try {
      ViewOverlaysModule module = ViewOverlaysModule.active();
      return module == null ? invisible : module.armPassSeesInvisible(invisible);
    } catch (Throwable error) {
      Cbui.reportOnce("Overlay Tweaks arm", error);
      return invisible;
    }
  }

  @Inject(method = "renderWaterOverlayTexture", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$waterTexture(float partialTicks, CallbackInfo ci) {
    try {
      ViewOverlaysModule module = ViewOverlaysModule.active();
      if (module != null && module.skipWaterTexture()) ci.cancel();
    } catch (Throwable error) {
      Cbui.reportOnce("Overlay Tweaks water", error);
    }
  }

  @Inject(method = "renderFireInFirstPerson", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$flamesShown(float partialTicks, CallbackInfo ci) {
    try {
      ViewOverlaysModule module = ViewOverlaysModule.active();
      if (module != null && module.skipFlames()) ci.cancel();
    } catch (Throwable error) {
      Cbui.reportOnce("Overlay Tweaks fire", error);
    }
  }

  @ModifyArg(method = "renderFireInFirstPerson",
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V", ordinal = 0),
      index = 3, require = 0)
  private float cbui$flameAlpha(float alpha) {
    try {
      ViewOverlaysModule module = ViewOverlaysModule.active();
      return module == null ? alpha : module.flameAlpha(alpha);
    } catch (Throwable error) {
      Cbui.reportOnce("Overlay Tweaks fire", error);
      return alpha;
    }
  }

  @ModifyArg(method = "renderFireInFirstPerson",
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V"),
      index = 1, require = 0)
  private float cbui$flameHeight(float y) {
    try {
      ViewOverlaysModule module = ViewOverlaysModule.active();
      return module == null ? y : module.flameHeight(y);
    } catch (Throwable error) {
      Cbui.reportOnce("Overlay Tweaks fire", error);
      return y;
    }
  }

  @WrapOperation(method = "renderInsideOfBlock",
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V", ordinal = 0),
      require = 0)
  private void cbui$blockShade(float red, float green, float blue, float alpha, Operation<Void> original) {
    float r = red, g = green, b = blue;
    try {
      ViewOverlaysModule module = ViewOverlaysModule.active();
      if (module != null) {
        r = module.blockShade(red);
        g = module.blockShade(green);
        b = module.blockShade(blue);
      }
    } catch (Throwable error) {
      Cbui.reportOnce("Overlay Tweaks block view", error);
      r = red; g = green; b = blue;
    }
    original.call(r, g, b, alpha);
  }
}
