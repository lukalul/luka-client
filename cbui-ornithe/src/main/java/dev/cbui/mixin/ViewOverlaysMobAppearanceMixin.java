package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.Cbui;
import dev.cbui.feature.viewoverlays.ViewOverlaysModule;
import net.minecraft.client.particle.MobAppearance;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Overlay Tweaks: the elder guardian face that flashes over the screen (1.8.9 only). */
@Mixin(MobAppearance.class)
public abstract class ViewOverlaysMobAppearanceMixin {
  @Inject(method = "renderParticle", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$curseShown(WorldRenderer buffer, Entity viewer, float partialTicks, float rotX, float rotZ,
      float rotYZ, float rotXY, float rotXZ, CallbackInfo ci) {
    try {
      ViewOverlaysModule module = ViewOverlaysModule.active();
      if (module != null && module.skipCurseFlash()) ci.cancel();
    } catch (Throwable error) {
      Cbui.reportOnce("Overlay Tweaks guardian", error);
    }
  }

  @ModifyArg(method = "renderParticle",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V", ordinal = 0),
      index = 3, require = 0)
  private float cbui$curseAlpha(float alpha) {
    try {
      ViewOverlaysModule module = ViewOverlaysModule.active();
      return module == null ? alpha : module.curseAlpha(alpha);
    } catch (Throwable error) {
      Cbui.reportOnce("Overlay Tweaks guardian", error);
      return alpha;
    }
  }

  @WrapOperation(method = "renderParticle",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;scale(FFF)V", ordinal = 0),
      require = 0)
  private void cbui$curseSize(float x, float y, float z, Operation<Void> original) {
    float sx = x, sy = y, sz = z;
    try {
      ViewOverlaysModule module = ViewOverlaysModule.active();
      if (module != null) {
        sx = module.curseScale(x);
        sy = module.curseScale(y);
        sz = module.curseScale(z);
      }
    } catch (Throwable error) {
      Cbui.reportOnce("Overlay Tweaks guardian", error);
      sx = x; sy = y; sz = z;
    }
    original.call(sx, sy, sz);
  }
}
