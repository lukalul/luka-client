package dev.cbui.mixin;

import dev.cbui.cosmetics.CapeLayer;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.layers.LayerCape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cosmetics: the cape the player chose replaces the game's cape layer for the local player. */
@Mixin(LayerCape.class)
public abstract class CosmeticsLayerCapeMixin {
  @Inject(method = "doRenderLayer(Lnet/minecraft/client/entity/AbstractClientPlayer;FFFFFFF)V", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$cape(AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
      float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
    try {
      if (CapeLayer.render(player, partialTicks)) ci.cancel();
    } catch (Throwable ignored) {
      // the game's own cape stays
    }
  }
}
