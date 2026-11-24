package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.Cbui;
import dev.cbui.feature.worldfx.WorldEffectsModule;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderLightningBolt;
import net.minecraft.entity.effect.EntityLightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render Tweaks: hides or recolours lightning bolts. The sky flash and thunder are not touched. */
@Mixin(RenderLightningBolt.class)
public abstract class WorldFxLightningMixin {
  @Inject(method = "doRender(Lnet/minecraft/entity/effect/EntityLightningBolt;DDDFF)V", at = @At("HEAD"),
      cancellable = true, require = 0)
  private void cbui$boltShown(EntityLightningBolt bolt, double x, double y, double z, float yaw, float partialTicks,
      CallbackInfo ci) {
    try {
      WorldEffectsModule module = WorldEffectsModule.active();
      if (module != null && module.skipBolt()) ci.cancel();
    } catch (Throwable error) {
      Cbui.reportOnce("Render Tweaks lightning", error);
    }
  }

  @WrapOperation(method = "doRender(Lnet/minecraft/entity/effect/EntityLightningBolt;DDDFF)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorRGBA_F(FFFF)V"),
      require = 0)
  private void cbui$boltColour(Tessellator tessellator, float red, float green, float blue, float alpha,
      Operation<Void> original) {
    float r = red, g = green, b = blue, a = alpha;
    try {
      WorldEffectsModule module = WorldEffectsModule.active();
      if (module != null && !module.boltIsVanilla()) {
        r = module.boltChannel(0);
        g = module.boltChannel(1);
        b = module.boltChannel(2);
        a = module.boltChannel(3);
      }
    } catch (Throwable error) {
      Cbui.reportOnce("Render Tweaks lightning", error);
      r = red; g = green; b = blue; a = alpha;
    }
    original.call(tessellator, r, g, b, a);
  }
}
