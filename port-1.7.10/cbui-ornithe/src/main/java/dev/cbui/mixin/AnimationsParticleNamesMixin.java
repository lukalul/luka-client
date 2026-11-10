package dev.cbui.mixin;

import dev.cbui.config.AnimationOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.7.10 spawns particles by name in RenderGlobal, not by id in EffectRenderer. */
@Mixin(RenderGlobal.class)
public abstract class AnimationsParticleNamesMixin {
  @Inject(method = "doSpawnParticle", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$filterParticle(String name, double x, double y, double z, double vx, double vy, double vz, CallbackInfoReturnable<EntityFX> cir) {
    // Vanilla calls level 2 "minimal" and still permits a few effects; this page promises fully off.
    if (Minecraft.getMinecraft().gameSettings.particleSetting == 2 || !AnimationOptions.particleEnabled(name)) cir.setReturnValue(null);
  }
}
