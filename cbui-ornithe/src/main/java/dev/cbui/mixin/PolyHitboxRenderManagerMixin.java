package dev.cbui.mixin;

import dev.cbui.Cbui;
import dev.cbui.feature.polyhitbox.HitboxRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skips vanilla's debug bounding box while the module is active, since the module draws its own. */
@Mixin(RenderManager.class)
public abstract class PolyHitboxRenderManagerMixin {
  @Inject(method = "renderDebugBoundingBox", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$skipVanillaBox(Entity entity, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
    boolean replaced = false;
    try {
      replaced = HitboxRenderer.replacing();
    } catch (Throwable failure) {
      Cbui.reportOnce("hitbox vanilla suppress hook", failure);
    }
    if (replaced) ci.cancel();
  }
}
