package dev.cbui.mixin;

import dev.cbui.Cbui;
import dev.cbui.feature.fusetimer.FuseTimerModule;
import net.minecraft.client.renderer.entity.RenderTNTPrimed;
import net.minecraft.entity.item.EntityTNTPrimed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TNTCountdown: once vanilla has drawn a primed TNT, the module draws its countdown label at the same spot. */
@Mixin(RenderTNTPrimed.class)
public abstract class FuseTimerRenderTNTPrimedMixin {
  @Inject(method = "doRender(Lnet/minecraft/entity/item/EntityTNTPrimed;DDDFF)V", at = @At("RETURN"), require = 0)
  private void cbui$countdown(EntityTNTPrimed tnt, double x, double y, double z, float yaw, float partialTicks,
      CallbackInfo ci) {
    try {
      FuseTimerModule module = FuseTimerModule.active();
      if (module != null && tnt != null) module.drawLabel(tnt, x, y, z);
    } catch (Throwable error) {
      Cbui.reportOnce("TNTCountdown label", error);
    }
  }
}
