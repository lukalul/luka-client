package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.cbui.Cbui;
import dev.cbui.feature.skyhorizon.SkyHorizonModule;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;

/**
 * the sky horizon card: no sky at all once the camera is below the world. The whole method (with every other hook inside it,
 * the GPU profiler's start and end markers included) is skipped, so the profiler simply records no sky time then.
 */
@Mixin(RenderGlobal.class)
public abstract class SkyHorizonVoidMixin {
  @WrapMethod(method = "renderSky(F)V", require = 0)
  private void cbui$skyVoid(float partialTicks, Operation<Void> original) {
    boolean skip = false;
    try {
      SkyHorizonModule module = SkyHorizonModule.active();
      skip = module != null && module.skipsWholeSky(partialTicks);
    } catch (Throwable error) {
      Cbui.reportOnce("smooth skies void", error);
    }
    if (!skip) original.call(partialTicks);
  }
}
