package dev.cbui.mixin;

import dev.cbui.render.EntityCull;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.EntityLiving;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The same "Entity detail distance" cut as {@link LivingLayersDistanceMixin} for zombies, skeletons and other biped mobs. */
@Mixin(RenderBiped.class)
public abstract class LivingLayersDistanceBipedMixin {
  @Inject(method = "shouldRenderPass(Lnet/minecraft/entity/EntityLiving;IF)I", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$farArmor(EntityLiving entity, int pass, float partialTicks, CallbackInfoReturnable<Integer> cir) {
    if (EntityCull.farLayers(entity)) cir.setReturnValue(-1);
  }

  @Inject(method = "renderEquippedItems(Lnet/minecraft/entity/EntityLiving;F)V", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$farEquipment(EntityLiving entity, float partialTicks, CallbackInfo ci) {
    if (EntityCull.farLayers(entity)) ci.cancel();
  }
}
