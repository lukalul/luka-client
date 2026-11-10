package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.feature.rendertweaks.RenderTweaksModule;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderLightningBolt;
import net.minecraft.entity.effect.EntityLightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderLightningBolt.class)
public abstract class RenderTweaksLightningMixin {
  @Inject(method = "doRender(Lnet/minecraft/entity/effect/EntityLightningBolt;DDDFF)V", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$skipBolt(EntityLightningBolt bolt, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
    try {
      RenderTweaksModule module = RenderTweaksModule.active();
      if (module != null && module.hidesLightning()) ci.cancel();
    } catch (Throwable failure) {
      // draw the bolt as vanilla would
    }
  }

  @WrapOperation(
      method = "doRender(Lnet/minecraft/entity/effect/EntityLightningBolt;DDDFF)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorRGBA_F(FFFF)V"),
      require = 0)
  private void cbui$tintBolt(Tessellator tessellator, float r, float g, float b, float a, Operation<Void> vanilla) {
    RenderTweaksModule module = RenderTweaksModule.active();
    if (module == null) {
      vanilla.call(tessellator, r, g, b, a);
      return;
    }
    int argb = module.lightningColor.get();
    vanilla.call(tessellator, RenderTweaksModule.channel(argb, 16), RenderTweaksModule.channel(argb, 8),
        RenderTweaksModule.channel(argb, 0), RenderTweaksModule.channel(argb, 24));
  }
}
