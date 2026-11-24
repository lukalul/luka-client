package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.cbui.Cbui;
import dev.cbui.feature.steadycam.SteadyCamModule;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * the steady camera card: in 1.7.10 the held-item lag is the third and fourth glRotatef of renderItemInFirstPerson (driven by
 * the arm pitch/yaw the player trails behind the view). The first two belong to the item lighting and stay.
 */
@Mixin(ItemRenderer.class)
public abstract class SteadyCamItemRendererMixin {
  @WrapWithCondition(method = "renderItemInFirstPerson", at = {
      @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 2, remap = false),
      @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 3, remap = false)},
      require = 0)
  private boolean cbui$steadyHandLag(float angle, float x, float y, float z) {
    try {
      SteadyCamModule module = SteadyCamModule.active();
      return module == null || module.keepsHandLag();
    } catch (Throwable error) {
      Cbui.reportOnce("shake tweaks hand lag", error);
      return true;
    }
  }
}
