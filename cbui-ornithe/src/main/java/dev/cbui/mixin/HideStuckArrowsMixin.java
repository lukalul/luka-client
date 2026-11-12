package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.client.renderer.entity.layers.LayerArrow;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide stuck arrows (Performance tab): arrows stuck in players and mobs are not drawn. */
@Mixin(LayerArrow.class)
public abstract class HideStuckArrowsMixin {
  @Inject(method = "doRenderLayer", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$hideStuckArrows(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
      float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
    if (GlobalOptions.hideStuckArrows) ci.cancel();
  }
}
