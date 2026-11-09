package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.feature.rendertweaks.RenderTweaksModule;
import net.minecraft.client.renderer.WorldRenderer;
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
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/WorldRenderer;color(FFFF)Lnet/minecraft/client/renderer/WorldRenderer;"),
      require = 0)
  private WorldRenderer cbui$tintBolt(WorldRenderer renderer, float r, float g, float b, float a, Operation<WorldRenderer> vanilla) {
    RenderTweaksModule module = RenderTweaksModule.active();
    if (module == null) return vanilla.call(renderer, r, g, b, a);
    int argb = module.lightningColor.get();
    return vanilla.call(renderer, RenderTweaksModule.channel(argb, 16), RenderTweaksModule.channel(argb, 8),
        RenderTweaksModule.channel(argb, 0), RenderTweaksModule.channel(argb, 24));
  }
}
