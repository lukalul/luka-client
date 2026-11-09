package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.cbui.feature.shaketweaks.ShakeTweaksModule;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(EntityRenderer.class)
public abstract class ShakeTweaksEntityRendererMixin {
  @WrapWithCondition(
      method = "setupCameraTransform",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;setupViewBobbing(F)V"),
      require = 0)
  private boolean cbui$cameraBob(EntityRenderer renderer, float partialTicks) {
    ShakeTweaksModule module = ShakeTweaksModule.active();
    return module == null || module.allowScreenBobbing();
  }

  @WrapWithCondition(
      method = "renderHand",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;setupViewBobbing(F)V"),
      require = 0)
  private boolean cbui$handBob(EntityRenderer renderer, float partialTicks) {
    ShakeTweaksModule module = ShakeTweaksModule.active();
    if (module == null) return true;
    try {
      return module.allowHandBobbing();
    } catch (Throwable failure) {
      return true;
    }
  }

  @WrapWithCondition(
      method = "setupCameraTransform",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;hurtCameraEffect(F)V"),
      require = 0)
  private boolean cbui$cameraHurtTilt(EntityRenderer renderer, float partialTicks) {
    ShakeTweaksModule module = ShakeTweaksModule.active();
    return module == null || module.allowScreenDamageTilt();
  }

  @WrapWithCondition(
      method = "renderHand",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;hurtCameraEffect(F)V"),
      require = 0)
  private boolean cbui$handHurtTilt(EntityRenderer renderer, float partialTicks) {
    ShakeTweaksModule module = ShakeTweaksModule.active();
    return module == null || module.allowHandDamageTilt();
  }

  @ModifyArg(
      method = "setupViewBobbing",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V"),
      index = 0,
      require = 0)
  private float cbui$bobSidewaysShift(float shift) {
    ShakeTweaksModule module = ShakeTweaksModule.active();
    return module != null && module.flattenSidewaysBob() ? 0f : shift;
  }

  @ModifyArg(
      method = "setupViewBobbing",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;rotate(FFFF)V", ordinal = 0),
      index = 0,
      require = 0)
  private float cbui$bobSidewaysRoll(float degrees) {
    ShakeTweaksModule module = ShakeTweaksModule.active();
    return module != null && module.flattenSidewaysBob() ? 0f : degrees;
  }
}
