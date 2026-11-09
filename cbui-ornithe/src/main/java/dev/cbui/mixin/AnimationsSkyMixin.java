package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.config.GlobalOptions;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The sun and moon are the second and third things renderSky draws with the tessellator (the first is the sunrise glow). */
@Mixin(RenderGlobal.class)
public abstract class AnimationsSkyMixin {
  @WrapOperation(method = "renderSky(FI)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()V", ordinal = 1), require = 0)
  private void cbui$sun(Tessellator tessellator, Operation<Void> vanilla) {
    if (GlobalOptions.animationEnabled("sun_moon")) { vanilla.call(tessellator); return; }
    tessellator.getWorldRenderer().finishDrawing();
    tessellator.getWorldRenderer().reset();
  }

  @WrapOperation(method = "renderSky(FI)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()V", ordinal = 2), require = 0)
  private void cbui$moon(Tessellator tessellator, Operation<Void> vanilla) {
    if (GlobalOptions.animationEnabled("sun_moon")) { vanilla.call(tessellator); return; }
    tessellator.getWorldRenderer().finishDrawing();
    tessellator.getWorldRenderer().reset();
  }
}
