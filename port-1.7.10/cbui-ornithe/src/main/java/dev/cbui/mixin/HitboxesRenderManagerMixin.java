package dev.cbui.mixin;

import dev.cbui.feature.hitboxes.HitboxesModule;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hitboxes: the game's own debug boxes stay hidden while luka draws its own. */
@Mixin(RenderManager.class)
public abstract class HitboxesRenderManagerMixin {
  @Inject(method = "renderDebugBoundingBox", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$hideGameBoxes(Entity entity, double x, double y, double z, float yaw, float partialTicks,
      CallbackInfo ci) {
    if (HitboxesModule.hidesGameBoxes()) ci.cancel();
  }
}
