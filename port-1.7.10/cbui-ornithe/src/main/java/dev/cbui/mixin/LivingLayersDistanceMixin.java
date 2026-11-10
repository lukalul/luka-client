package dev.cbui.mixin;

import dev.cbui.render.EntityCull;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "Entity detail distance" setting: players farther than this keep their body but lose the extra layers (armour, held items, capes,
 * hats). At that range they are a few pixels tall, and a crowd of them costs a lot of frame time. RenderPlayer draws them in
 * shouldRenderPass (armour) and renderEquippedItems (everything else).
 */
@Mixin(RenderPlayer.class)
public abstract class LivingLayersDistanceMixin {
  @Inject(method = "shouldRenderPass(Lnet/minecraft/client/entity/AbstractClientPlayer;IF)I", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$farArmor(AbstractClientPlayer player, int pass, float partialTicks, CallbackInfoReturnable<Integer> cir) {
    if (EntityCull.farLayers(player)) cir.setReturnValue(-1);
  }

  @Inject(method = "renderEquippedItems(Lnet/minecraft/client/entity/AbstractClientPlayer;F)V", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$farEquipment(AbstractClientPlayer player, float partialTicks, CallbackInfo ci) {
    if (EntityCull.farLayers(player)) ci.cancel();
  }
}
