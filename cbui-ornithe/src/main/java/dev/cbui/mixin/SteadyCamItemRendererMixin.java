package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.cbui.Cbui;
import dev.cbui.feature.steadycam.SteadyCamModule;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** the steady camera card: skips the rotation that makes the held item trail behind the view. */
@Mixin(ItemRenderer.class)
public abstract class SteadyCamItemRendererMixin {
  @WrapWithCondition(method = "renderItemInFirstPerson",
      at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/renderer/ItemRenderer;rotateWithPlayerRotations(Lnet/minecraft/client/entity/EntityPlayerSP;F)V"),
      require = 0)
  private boolean cbui$steadyHandLag(ItemRenderer self, EntityPlayerSP player, float partialTicks) {
    try {
      SteadyCamModule module = SteadyCamModule.active();
      return module == null || module.keepsHandLag();
    } catch (Throwable error) {
      Cbui.reportOnce("shake tweaks hand lag", error);
      return true;
    }
  }
}
