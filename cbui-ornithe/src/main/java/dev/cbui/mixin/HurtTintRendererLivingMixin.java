package dev.cbui.mixin;

import dev.cbui.Cbui;
import dev.cbui.feature.hurttint.HurtTintModule;
import java.nio.FloatBuffer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Damage Tint: just before the overlay colour is handed to the texture combiner, rewrite it when the entity is hurt
 * or dying and the settings ask for something other than the game's own red.
 */
@Mixin(RendererLivingEntity.class)
public abstract class HurtTintRendererLivingMixin {
  @Shadow protected FloatBuffer brightnessBuffer;

  @Inject(method = "setBrightness",
      at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;flip()Ljava/nio/Buffer;", ordinal = 0, remap = false),
      require = 0)
  private void cbui$hurtTint(EntityLivingBase entity, float partialTicks, boolean combineTextures,
      CallbackInfoReturnable<Boolean> cir) {
    try {
      if (entity.hurtTime <= 0 && entity.deathTime <= 0) return;
      HurtTintModule module = HurtTintModule.active();
      if (module == null || module.looksLikeGame(entity)) return;
      int argb = module.overlay(entity, partialTicks);
      float red = (argb >> 16 & 255) / 255F;
      float green = (argb >> 8 & 255) / 255F;
      float blue = (argb & 255) / 255F;
      float alpha = (argb >>> 24) / 255F;
      brightnessBuffer.position(0);
      brightnessBuffer.put(red).put(green).put(blue).put(alpha);
    } catch (Throwable error) {
      Cbui.reportOnce("damage tint overlay", error);
    }
  }
}
