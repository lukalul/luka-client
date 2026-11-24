package dev.cbui.mixin;

import dev.cbui.Cbui;
import dev.cbui.feature.viewangle.ViewAngleModule;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** FOV Changer: only the underwater step inside the FOV method changes; its return value is left for zoom. */
@Mixin(EntityRenderer.class)
public abstract class ViewAngleEntityRendererMixin {
  @ModifyConstant(method = "getFOVModifier", constant = @Constant(floatValue = 60.0F), require = 0)
  private float cbui$viewAngleWater(float vanilla) {
    ViewAngleModule module = ViewAngleModule.active();
    if (module == null) return vanilla;
    try {
      return module.waterNumerator(vanilla);
    } catch (Throwable error) {
      Cbui.reportOnce("FOV changer water", error);
      return vanilla;
    }
  }
}
