package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.cbui.cosmetics.CapeLayer;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Cosmetics: the cape the player chose replaces the game's own cape. RenderPlayer asks hasCape right before drawing it. */
@Mixin(RenderPlayer.class)
public abstract class CosmeticsCapeMixin {
  @ModifyExpressionValue(
      method = "renderEquippedItems(Lnet/minecraft/client/entity/AbstractClientPlayer;F)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/AbstractClientPlayer;hasCape()Z"),
      require = 0)
  private boolean cbui$cape(boolean hasCape, @Local(argsOnly = true) AbstractClientPlayer player, @Local(argsOnly = true) float partialTicks) {
    try {
      if (CapeLayer.render(player, partialTicks)) return false;
    } catch (Throwable ignored) {
      // the game's own cape stays
    }
    return hasCape;
  }
}
