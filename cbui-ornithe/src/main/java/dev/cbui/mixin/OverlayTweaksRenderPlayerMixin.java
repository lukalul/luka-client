package dev.cbui.mixin;

import dev.cbui.feature.overlaytweaks.OverlayTweaksModule;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fades your own arm while invisible. RenderPlayer sets the colour back to opaque white just before the arm model is drawn, so
 * the fade has to be applied right at the model call and cleared once the arm method returns.
 */
@Mixin(RenderPlayer.class)
public abstract class OverlayTweaksRenderPlayerMixin {
  @Inject(method = "renderRightArm", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/model/ModelPlayer;renderRightArm()V"), require = 0)
  private void cbui$fadeRightArm(AbstractClientPlayer player, CallbackInfo ci) {
    OverlayTweaksModule.beginArmFade(player);
  }

  @Inject(method = "renderLeftArm", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/model/ModelPlayer;renderLeftArm()V"), require = 0)
  private void cbui$fadeLeftArm(AbstractClientPlayer player, CallbackInfo ci) {
    OverlayTweaksModule.beginArmFade(player);
  }

  @Inject(method = {"renderRightArm", "renderLeftArm"}, at = @At("RETURN"), require = 0)
  private void cbui$clearArmFade(AbstractClientPlayer player, CallbackInfo ci) {
    OverlayTweaksModule.endArmFade();
  }
}
