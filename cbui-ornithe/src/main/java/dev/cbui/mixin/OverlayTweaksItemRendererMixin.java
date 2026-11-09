package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cbui.feature.overlaytweaks.OverlayTweaksModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ItemRenderer.class)
public abstract class OverlayTweaksItemRendererMixin {
  @Inject(method = "renderWaterOverlayTexture", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$skipWaterTexture(float partialTicks, CallbackInfo ci) {
    OverlayTweaksModule module = OverlayTweaksModule.active();
    if (module != null && module.removeWaterOverlay.get()) ci.cancel();
  }

  @Inject(method = "renderFireInFirstPerson", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$skipFire(float partialTicks, CallbackInfo ci) {
    OverlayTweaksModule module = OverlayTweaksModule.active();
    if (module == null) return;
    try {
      if (module.hidesFire(Minecraft.getMinecraft().thePlayer)) ci.cancel();
    } catch (Throwable failure) {
      // leave vanilla's fire on screen
    }
  }

  @ModifyArg(
      method = "renderFireInFirstPerson",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V"),
      index = 1,
      require = 0)
  private float cbui$lowerFire(float vanillaY) {
    OverlayTweaksModule module = OverlayTweaksModule.active();
    return module == null ? vanillaY : vanillaY + module.fireOverlayHeight.get();
  }

  @ModifyArg(
      method = "renderFireInFirstPerson",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V", ordinal = 0),
      index = 3,
      require = 0)
  private float cbui$fadeFire(float vanillaAlpha) {
    OverlayTweaksModule module = OverlayTweaksModule.active();
    Float custom = module == null ? null : module.fireAlpha();
    return custom == null ? vanillaAlpha : custom;
  }

  @ModifyArgs(
      method = "renderBlockInHand",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V", ordinal = 0),
      require = 0)
  private void cbui$dimSuffocation(Args colour) {
    OverlayTweaksModule module = OverlayTweaksModule.active();
    if (module == null) return;
    float level = module.suffocationBrightness();
    colour.set(0, level);
    colour.set(1, level);
    colour.set(2, level);
    colour.set(3, 1F);
  }

  @ModifyExpressionValue(
      method = {"renderPlayerArms", "renderItemInFirstPerson"},
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/AbstractClientPlayer;isInvisible()Z"),
      require = 0)
  private boolean cbui$drawInvisibleHand(boolean invisible) {
    OverlayTweaksModule module = OverlayTweaksModule.active();
    return invisible && !(module != null && module.showsInvisibleHand());
  }
}
