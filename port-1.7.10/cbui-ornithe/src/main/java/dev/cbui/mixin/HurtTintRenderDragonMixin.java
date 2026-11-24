package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.cbui.Cbui;
import dev.cbui.feature.hurttint.HurtTintModule;
import net.minecraft.client.renderer.entity.RenderDragon;
import net.minecraft.entity.boss.EntityDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Damage Tint: the dragon paints its own, stronger flash; give it the chosen colour unless the defaults are on. */
@Mixin(RenderDragon.class)
public abstract class HurtTintRenderDragonMixin {
  @WrapOperation(method = "renderModel(Lnet/minecraft/entity/boss/EntityDragon;FFFFFF)V",
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V", remap = false),
      require = 0)
  private void cbui$dragonFlash(float red, float green, float blue, float alpha, Operation<Void> original,
      @Local(argsOnly = true) EntityDragon dragon, @Local(argsOnly = true, ordinal = 2) float age) {
    float r = red, g = green, b = blue, a = alpha;
    try {
      HurtTintModule module = HurtTintModule.active();
      if (module != null && dragon.hurtTime > 0 && !module.looksLikeGame(dragon)) {
        float partial = Math.max(0F, Math.min(1F, age - dragon.ticksExisted));
        int argb = module.overlay(dragon, partial);
        r = (argb >> 16 & 255) / 255F;
        g = (argb >> 8 & 255) / 255F;
        b = (argb & 255) / 255F;
        a = (argb >>> 24) / 255F;
      }
    } catch (Throwable error) {
      Cbui.reportOnce("damage tint dragon", error);
      r = red; g = green; b = blue; a = alpha;
    }
    original.call(r, g, b, a);
  }
}
