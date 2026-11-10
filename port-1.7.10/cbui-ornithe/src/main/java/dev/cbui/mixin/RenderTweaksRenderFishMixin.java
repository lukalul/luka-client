package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.feature.rendertweaks.RenderTweaksModule;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderFish;
import net.minecraft.entity.projectile.EntityFishHook;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderFish.class)
public abstract class RenderTweaksRenderFishMixin {
  @Inject(method = "doRender(Lnet/minecraft/entity/projectile/EntityFishHook;DDDFF)V", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$skipWholeHook(EntityFishHook hook, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
    try {
      RenderTweaksModule module = RenderTweaksModule.active();
      if (module != null && module.hidesFishingLine()) ci.cancel();
    } catch (Throwable failure) {
      // draw the line as vanilla would
    }
  }

  @WrapOperation(
      method = "doRender(Lnet/minecraft/entity/projectile/EntityFishHook;DDDFF)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_I(I)V"),
      require = 0)
  private void cbui$tintLine(Tessellator tessellator, int rgb, Operation<Void> vanilla) {
    RenderTweaksModule module = RenderTweaksModule.active();
    if (module == null) {
      vanilla.call(tessellator, rgb);
      return;
    }
    int argb = module.fishingLineColor.get();
    tessellator.setColorRGBA(RenderTweaksModule.component(argb, 16), RenderTweaksModule.component(argb, 8),
        RenderTweaksModule.component(argb, 0), RenderTweaksModule.component(argb, 24));
  }

  @Inject(
      method = "doRender(Lnet/minecraft/entity/projectile/EntityFishHook;DDDFF)V",
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPopMatrix()V", shift = At.Shift.AFTER, remap = false),
      require = 0)
  private void cbui$applyLineWidth(EntityFishHook hook, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
    try {
      RenderTweaksModule module = RenderTweaksModule.active();
      if (module != null) GL11.glLineWidth(module.fishingLineWidthMultiplier.get());
    } catch (Throwable failure) {
      // keep the default width
    }
  }

  @Inject(method = "doRender(Lnet/minecraft/entity/projectile/EntityFishHook;DDDFF)V", at = @At("RETURN"), require = 0)
  private void cbui$restoreLineWidth(EntityFishHook hook, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
    try {
      GL11.glLineWidth(1F);
    } catch (Throwable failure) {
      // nothing to restore
    }
  }
}
