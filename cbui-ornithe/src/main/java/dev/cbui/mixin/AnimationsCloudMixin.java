package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public abstract class AnimationsCloudMixin {
  /** Stops the vanilla cloud tick counter, freezing cloud drift without hiding the sky or clouds. */
  @Inject(method = "updateClouds", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$freezeClouds(CallbackInfo ci) {
    if (!GlobalOptions.animationEnabled("sky_clouds")) ci.cancel();
  }
}
