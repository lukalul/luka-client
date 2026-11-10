package dev.cbui.mixin;

import dev.cbui.render.EntityCull;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skips the body of an entity behind solid blocks. Names still draw, as they do through walls in vanilla. */
@Mixin(RendererLivingEntity.class)
public abstract class EntityCullRenderMixin {
  @Shadow protected abstract void passSpecialRender(EntityLivingBase entity, double x, double y, double z);

  @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$cull(EntityLivingBase entity, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
    if (EntityCull.skip(entity)) {
      passSpecialRender(entity, x, y, z);
      ci.cancel();
    }
  }
}
