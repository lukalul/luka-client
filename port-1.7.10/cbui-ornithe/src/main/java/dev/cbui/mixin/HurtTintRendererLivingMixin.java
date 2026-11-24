package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.cbui.Cbui;
import dev.cbui.feature.hurttint.HurtTintModule;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Damage Tint: on this version the hurt flash is a second translucent pass over the model and its armour, coloured
 * {@code (light, 0, 0, 0.4)}. Only those colour calls are swapped for the chosen colour, still darkened by the light.
 */
@Mixin(RendererLivingEntity.class)
public abstract class HurtTintRendererLivingMixin {
  @WrapOperation(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V", remap = false),
      require = 0)
  private void cbui$hurtTint(float red, float green, float blue, float alpha, Operation<Void> original,
      @Local(argsOnly = true) EntityLivingBase entity, @Local(argsOnly = true, ordinal = 1) float partialTicks) {
    float r = red, g = green, b = blue, a = alpha;
    try {
      boolean flash = green == 0F && blue == 0F && alpha == 0.4F && (entity.hurtTime > 0 || entity.deathTime > 0);
      HurtTintModule module = flash ? HurtTintModule.active() : null;
      if (module != null && !module.looksLikeGame(entity)) {
        int argb = module.overlay(entity, partialTicks);
        float light = red;
        r = (argb >> 16 & 255) / 255F * light;
        g = (argb >> 8 & 255) / 255F * light;
        b = (argb & 255) / 255F * light;
        a = (argb >>> 24) / 255F;
      }
    } catch (Throwable error) {
      Cbui.reportOnce("damage tint overlay", error);
      r = red; g = green; b = blue; a = alpha;
    }
    original.call(r, g, b, a);
  }
}
