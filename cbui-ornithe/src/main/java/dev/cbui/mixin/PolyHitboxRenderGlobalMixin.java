package dev.cbui.mixin;

import dev.cbui.Cbui;
import dev.cbui.feature.polyhitbox.HitboxRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs the hitbox pass at the very end of entity rendering, after every entity and tile entity is on screen. */
@Mixin(RenderGlobal.class)
public abstract class PolyHitboxRenderGlobalMixin {
  @Inject(method = "renderEntities", at = @At("TAIL"), require = 0)
  private void cbui$afterEntities(Entity viewer, ICamera frustum, float partialTicks, CallbackInfo ci) {
    try {
      HitboxRenderer.render(frustum, partialTicks);
    } catch (Throwable failure) {
      Cbui.reportOnce("hitbox render hook", failure);
    }
  }
}
