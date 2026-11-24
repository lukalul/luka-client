package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.client.multiplayer.WorldClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * "Fewer ambient effects" (Performance, Particles): each client tick the game tries 1,000 random block positions around the
 * player for ambient effects (water and lava drips, torch and fire flames, void particles, portal sparkles). With the switch
 * on it tries 100, so those effects show up a tenth as often and cost a tenth of the time.
 */
@Mixin(WorldClient.class)
public abstract class AmbientEffectsMixin {
  @ModifyConstant(method = "doVoidFogParticles", constant = @Constant(intValue = 1000), require = 0)
  private int cbui$fewerAmbientEffects(int tries) {
    return GlobalOptions.fewerAmbientEffects ? 100 : tries;
  }
}
