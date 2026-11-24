package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "Particle collisions" off (Performance, Particles): a new particle gets the game's own no-clip flag, so when it moves the game
 * just shifts its box by its motion and takes its position from the box, with no collision checks against blocks. Particles
 * then pass through blocks. Applies to particles created after the switch changes.
 */
@Mixin(EffectRenderer.class)
public abstract class ParticleCollisionsMixin {
  @Inject(method = "addEffect", at = @At("HEAD"), require = 0)
  private void cbui$noParticleCollisions(EntityFX particle, CallbackInfo ci) {
    if (!GlobalOptions.particleCollisions && particle != null) particle.noClip = true;
  }
}
