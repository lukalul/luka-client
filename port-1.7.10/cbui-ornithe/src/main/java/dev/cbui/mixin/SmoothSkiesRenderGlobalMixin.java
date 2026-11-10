package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.feature.smoothskies.SmoothSkiesModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public abstract class SmoothSkiesRenderGlobalMixin {
  @Shadow @Final private Minecraft mc;

  @Inject(method = "renderSky(F)V", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$skipSkyBelowWorld(float partialTicks, CallbackInfo ci) {
    SmoothSkiesModule module = SmoothSkiesModule.active();
    if (module == null || !module.removeSkyInVoid.get()) return;
    try {
      EntityPlayer player = mc.thePlayer;
      if (player != null && player.getPosition(partialTicks).yCoord < SmoothSkiesModule.VOID_SKY_CUTOFF) ci.cancel();
    } catch (Throwable failure) {
      // leave the sky to vanilla
    }
  }

  @WrapOperation(
      method = "renderSky(F)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldProvider;calcSunriseSunsetColors(FF)[F"),
      require = 0)
  private float[] cbui$maybeNoHorizonGlow(
      WorldProvider provider, float angle, float partialTicks, Operation<float[]> vanillaColors) {
    SmoothSkiesModule module = SmoothSkiesModule.active();
    if (module != null && module.removeSunriseAndSunsetColoring.get()) return null;
    return vanillaColors.call(provider, angle, partialTicks);
  }
}
