package dev.cbui.mixin;

import dev.cbui.feature.damagetint.DamageTintModule;
import java.nio.FloatBuffer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Overwrites the colour vanilla is about to upload as the hurt/death flash for a living entity. */
@Mixin(RendererLivingEntity.class)
public abstract class DamageTintRendererLivingEntityMixin<T extends EntityLivingBase> {
  @Shadow protected FloatBuffer brightnessBuffer;

  @Inject(
      method = "setBrightness(Lnet/minecraft/entity/EntityLivingBase;FZ)Z",
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexEnv(IILjava/nio/FloatBuffer;)V"),
      require = 0)
  private void cbui$replaceFlashColour(T entity, float partialTicks, boolean combineTextures,
      CallbackInfoReturnable<Boolean> cir) {
    DamageTintModule module = DamageTintModule.active();
    if (module == null || (entity.hurtTime <= 0 && entity.deathTime <= 0)) return;
    try {
      int argb = module.colorFor(DamageTintModule.variantOf(entity));
      brightnessBuffer.put(0, DamageTintModule.channel(argb, 16));
      brightnessBuffer.put(1, DamageTintModule.channel(argb, 8));
      brightnessBuffer.put(2, DamageTintModule.channel(argb, 0));
      brightnessBuffer.put(3, module.alphaFor(argb, entity));
    } catch (Throwable failure) {
      // keep vanilla's flash
    }
  }
}
