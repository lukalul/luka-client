package dev.cbui.mixin;

import dev.cbui.feature.lookaround.LookAroundModule;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Freelook: chunk visibility is worked out for where the camera looks, not where the player faces. The player carries
 * the camera angles for the length of these methods (nested calls are counted by the module).
 */
@Mixin(RenderGlobal.class)
public abstract class LookAroundRenderGlobalMixin {
  @Inject(method = "setupTerrain", at = @At("HEAD"), require = 0)
  private void cbui$lookAroundLendTerrain(CallbackInfo ci) {
    LookAroundModule look = LookAroundModule.instance();
    if (look != null) look.lendAngles();
  }

  @Inject(method = "setupTerrain", at = @At("RETURN"), require = 0)
  private void cbui$lookAroundGiveBackTerrain(CallbackInfo ci) {
    LookAroundModule look = LookAroundModule.instance();
    if (look != null) look.returnAngles();
  }

  @Inject(method = "getViewVector", at = @At("HEAD"), require = 0)
  private void cbui$lookAroundLendView(CallbackInfoReturnable<?> cir) {
    LookAroundModule look = LookAroundModule.instance();
    if (look != null) look.lendAngles();
  }

  @Inject(method = "getViewVector", at = @At("RETURN"), require = 0)
  private void cbui$lookAroundGiveBackView(CallbackInfoReturnable<?> cir) {
    LookAroundModule look = LookAroundModule.instance();
    if (look != null) look.returnAngles();
  }
}
