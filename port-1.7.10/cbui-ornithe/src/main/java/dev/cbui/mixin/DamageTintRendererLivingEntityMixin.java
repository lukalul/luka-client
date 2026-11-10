package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.cbui.feature.damagetint.DamageTintModule;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Overwrites the colour vanilla gives the hurt/death flash of a living entity. 1.7.10 sets that colour with glColor4f inside
 * doRender (red-only at alpha 0.4), so those calls are the ones recoloured here.
 */
@Mixin(RendererLivingEntity.class)
public abstract class DamageTintRendererLivingEntityMixin {
  @WrapOperation(
      method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V", remap = false),
      require = 0)
  private void cbui$replaceFlashColour(float r, float g, float b, float a, Operation<Void> vanilla, @Local(argsOnly = true) EntityLivingBase entity) {
    DamageTintModule module = g == 0.0F && b == 0.0F && a == 0.4F ? DamageTintModule.active() : null;
    if (module != null && (entity.hurtTime > 0 || entity.deathTime > 0)) {
      try {
        int argb = module.colorFor(DamageTintModule.variantOf(entity));
        vanilla.call(DamageTintModule.channel(argb, 16), DamageTintModule.channel(argb, 8), DamageTintModule.channel(argb, 0), module.alphaFor(argb, entity));
        return;
      } catch (Throwable failure) {
        // keep vanilla's flash
      }
    }
    vanilla.call(r, g, b, a);
  }
}
