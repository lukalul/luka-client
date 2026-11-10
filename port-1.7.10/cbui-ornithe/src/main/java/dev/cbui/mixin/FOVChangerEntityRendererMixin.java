package dev.cbui.mixin;

import dev.cbui.feature.fovchanger.FOVChangerModule;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Swaps the underwater FOV base number inside EntityRenderer's FOV calculation. */
@Mixin(EntityRenderer.class)
public abstract class FOVChangerEntityRendererMixin {
  @ModifyConstant(method = "getFOVModifier", constant = @Constant(floatValue = 60f), require = 0)
  private float cbui$waterBase(float vanillaBase) {
    FOVChangerModule module = FOVChangerModule.active();
    if (module == null) return vanillaBase;
    try {
      return module.submergedConstant(vanillaBase);
    } catch (Throwable failure) {
      return vanillaBase;
    }
  }
}
