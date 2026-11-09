package dev.cbui.mixin;

import dev.cbui.Cbui;
import dev.cbui.feature.Features;
import dev.cbui.feature.tntcountdown.TNTCountdownModule;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderTNTPrimed;
import net.minecraft.entity.item.EntityTNTPrimed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTNTPrimed.class)
public abstract class TNTCountdownRenderTNTPrimedMixin extends Render<EntityTNTPrimed> {
  private static boolean cbui$disabledAfterError;
  private TNTCountdownRenderTNTPrimedMixin(RenderManager manager) { super(manager); }

  @Inject(method = "doRender(Lnet/minecraft/entity/item/EntityTNTPrimed;DDDFF)V", at = @At("RETURN"), require = 0)
  private void cbui$drawFuseLabel(EntityTNTPrimed entity, double x, double y, double z, float yaw, float partialTicks, CallbackInfo callback) {
    if (cbui$disabledAfterError) return;
    try {
      TNTCountdownModule module = Features.get(TNTCountdownModule.class);
      if (module == null || !module.enabled()) return;
      TNTCountdownModule.Label label = module.labelFor(entity);
      if (label.ticks > 0f) renderLabelScaled(entity, label.text, label.rgb, module.scale.get(), x, y, z);
    } catch (Throwable failure) {
      cbui$disabledAfterError = true;
      Cbui.reportOnce("TNTCountdown render", failure);
    }
  }

  private void renderLabelScaled(EntityTNTPrimed entity, String text, int rgb, float scale, double x, double y, double z) {
    boolean transformed = scale != 1f;
    if (transformed) {
      double labelY = y + entity.height + .5d;
      GlStateManager.pushMatrix();
      GlStateManager.translate((float) x, (float) labelY, (float) z);
      GlStateManager.scale(scale, scale, scale);
      GlStateManager.translate((float) -x, (float) -labelY, (float) -z);
    }
    TNTCountdownModule.labelRgb = rgb;
    try { renderLivingLabel(entity, text, x, y, z, 64); }
    finally {
      TNTCountdownModule.labelRgb = -1;
      if (transformed) GlStateManager.popMatrix();
    }
  }
}
