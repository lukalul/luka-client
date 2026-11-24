package dev.cbui.mixin;

import dev.cbui.feature.lookaround.LookAroundModule;
import net.minecraft.client.renderer.entity.RenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freelook: nametags and other entity billboards face the freelook camera (the front view stays turned round).
 * The player carries the camera angles while the view angles are cached.
 */
@Mixin(RenderManager.class)
public abstract class LookAroundRenderManagerMixin {
  @Inject(method = "cacheActiveRenderInfo", at = @At("HEAD"), require = 0)
  private void cbui$lookAroundLend(CallbackInfo ci) {
    LookAroundModule look = LookAroundModule.instance();
    if (look != null) look.lendAngles();
  }

  @Inject(method = "cacheActiveRenderInfo", at = @At("RETURN"), require = 0)
  private void cbui$lookAroundGiveBack(CallbackInfo ci) {
    LookAroundModule look = LookAroundModule.instance();
    if (look != null) look.returnAngles();
  }
}
