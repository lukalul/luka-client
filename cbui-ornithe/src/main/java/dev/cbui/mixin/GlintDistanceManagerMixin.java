package dev.cbui.mixin;

import dev.cbui.render.GlintDistance;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderManager.class)
public abstract class GlintDistanceManagerMixin {
  @Inject(method = "doRenderEntity", at = @At("HEAD"))
  private void cbui$begin(Entity entity, double x, double y, double z, float yaw, float partialTicks, boolean hideBox, CallbackInfoReturnable<Boolean> cir) {
    GlintDistance.begin(entity);
  }

  @Inject(method = "doRenderEntity", at = @At("RETURN"))
  private void cbui$end(Entity entity, double x, double y, double z, float yaw, float partialTicks, boolean hideBox, CallbackInfoReturnable<Boolean> cir) {
    GlintDistance.end();
  }
}
