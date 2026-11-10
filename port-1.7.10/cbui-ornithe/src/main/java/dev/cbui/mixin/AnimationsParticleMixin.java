package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.block.Block;
import net.minecraft.client.particle.EffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EffectRenderer.class)
public abstract class AnimationsParticleMixin {
  @Inject(method = "addBlockDestroyEffects", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$filterBlockDestroy(int x, int y, int z, Block block, int metadata, CallbackInfo ci) {
    if (!GlobalOptions.animationEnabled("item_block_break")) ci.cancel();
  }

  @Inject(method = "addBlockHitEffects", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$filterBlockHit(int x, int y, int z, int side, CallbackInfo ci) {
    if (!GlobalOptions.animationEnabled("item_block_break")) ci.cancel();
  }
}
