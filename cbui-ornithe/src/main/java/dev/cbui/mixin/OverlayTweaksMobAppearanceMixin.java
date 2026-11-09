package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.feature.overlaytweaks.OverlayTweaksModule;
import net.minecraft.client.particle.MobAppearance;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The elder guardian's jump-scare is a particle; these hooks let it be hidden, faded or resized. */
@Mixin(MobAppearance.class)
public abstract class OverlayTweaksMobAppearanceMixin {
  @Inject(method = "renderParticle", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$skipGuardian(WorldRenderer renderer, Entity entity, float partialTicks,
      float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ, CallbackInfo ci) {
    OverlayTweaksModule module = OverlayTweaksModule.active();
    if (module != null && module.guardianAlphaScale() <= 0F) ci.cancel();
  }

  @ModifyArg(
      method = "renderParticle",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V"),
      index = 3,
      require = 0)
  private float cbui$guardianAlpha(float vanillaAlpha) {
    OverlayTweaksModule module = OverlayTweaksModule.active();
    // vanilla peaks at 0.55, so dividing keeps "100%" meaning vanilla's own strength
    return module == null ? vanillaAlpha : vanillaAlpha * module.guardianAlphaScale() / .55F;
  }

  @WrapOperation(
      method = "renderParticle",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;scale(FFF)V"),
      require = 0)
  private void cbui$guardianSize(float x, float y, float z, Operation<Void> vanilla) {
    OverlayTweaksModule module = OverlayTweaksModule.active();
    if (module == null) {
      vanilla.call(x, y, z);
      return;
    }
    float size = module.elderGuardianScale.get();
    vanilla.call(size, size, size);
  }
}
