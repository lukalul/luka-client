package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.config.GlobalOptions;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** renderSky's Tessellator draws in order: the End sky, the sunrise glow, the sun, the moon, the dark half of the sky. */
@Mixin(RenderGlobal.class)
public abstract class AnimationsSkyMixin {
  private static int cbui$discard() {
    dev.luka.compat.Compat.discardTessellator();
    return 0;
  }

  @WrapOperation(method = "renderSky(F)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()I", ordinal = 2), require = 0)
  private int cbui$sun(Tessellator tessellator, Operation<Integer> vanilla) {
    return GlobalOptions.animationEnabled("sun_moon") ? vanilla.call(tessellator) : cbui$discard();
  }

  @WrapOperation(method = "renderSky(F)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()I", ordinal = 3), require = 0)
  private int cbui$moon(Tessellator tessellator, Operation<Integer> vanilla) {
    return GlobalOptions.animationEnabled("sun_moon") ? vanilla.call(tessellator) : cbui$discard();
  }
}
