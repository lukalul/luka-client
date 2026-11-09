package dev.cbui.mixin;

import dev.cbui.render.EntityCull;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skips the body of an entity behind solid blocks. Names still draw, as they do through walls in vanilla; outline passes are left alone. */
@Mixin(RendererLivingEntity.class)
public abstract class EntityCullRenderMixin extends Render<EntityLivingBase> {
  protected EntityCullRenderMixin(RenderManager manager) {
    super(manager);
  }

  @Shadow protected boolean renderOutlines;

  @Shadow public abstract void renderName(EntityLivingBase entity, double x, double y, double z);

  @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("HEAD"), cancellable = true)
  private void cbui$cull(EntityLivingBase entity, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
    if (!this.renderOutlines && EntityCull.skip(entity)) {
      renderName(entity, x, y, z);
      ci.cancel();
    }
  }
}
