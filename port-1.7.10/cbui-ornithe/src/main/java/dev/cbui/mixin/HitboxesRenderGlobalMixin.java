package dev.cbui.mixin;

import dev.cbui.feature.hitboxes.HitboxPass;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hitboxes: once the frame's entities and block entities are drawn, draw all hitboxes in one batch. */
@Mixin(RenderGlobal.class)
public abstract class HitboxesRenderGlobalMixin {
  @Inject(method = "renderEntities", at = @At("TAIL"), require = 0)
  private void cbui$hitboxPass(EntityLivingBase viewer, ICamera camera, float partialTicks, CallbackInfo ci) {
    HitboxPass.run(camera, partialTicks);
  }
}
