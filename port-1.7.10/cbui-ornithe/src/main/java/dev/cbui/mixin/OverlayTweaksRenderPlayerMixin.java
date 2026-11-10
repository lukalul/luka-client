package dev.cbui.mixin;

import dev.cbui.feature.overlaytweaks.OverlayTweaksModule;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fades your own arm while invisible. RenderPlayer sets the colour back to opaque white just before the arm is drawn, so
 * the fade has to be applied right at the model call and cleared once the arm method returns.
 */
@Mixin(RenderPlayer.class)
public abstract class OverlayTweaksRenderPlayerMixin {
  @Inject(method = "renderFirstPersonArm", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/model/ModelRenderer;render(F)V"), require = 0)
  private void cbui$fadeArm(EntityPlayer player, CallbackInfo ci) {
    if (player instanceof AbstractClientPlayer) OverlayTweaksModule.beginArmFade((AbstractClientPlayer) player);
  }

  @Inject(method = "renderFirstPersonArm", at = @At("RETURN"), require = 0)
  private void cbui$clearArmFade(EntityPlayer player, CallbackInfo ci) {
    OverlayTweaksModule.endArmFade();
  }
}
