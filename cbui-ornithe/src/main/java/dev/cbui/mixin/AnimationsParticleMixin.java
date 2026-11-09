package dev.cbui.mixin;

import dev.cbui.config.AnimationOptions;
import dev.cbui.config.GlobalOptions;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.block.state.IBlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EffectRenderer.class)
public abstract class AnimationsParticleMixin {
  @Inject(method = "spawnEffectParticle", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$filterParticle(int id, double x, double y, double z, double vx, double vy, double vz,
      int[] arguments, CallbackInfoReturnable<EntityFX> cir) {
    EnumParticleTypes type = EnumParticleTypes.getParticleFromId(id);
    // Vanilla calls level 2 "minimal" and still permits a few effects; this page promises fully off.
    if (Minecraft.getMinecraft().gameSettings.particleSetting == 2
        || (type != null && !AnimationOptions.particleEnabled(type))) cir.setReturnValue(null);
  }

  @Inject(method = "addBlockDestroyEffects", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$filterBlockDestroy(BlockPos pos, IBlockState state, CallbackInfo ci) {
    if (!GlobalOptions.animationEnabled("item_block_break")) ci.cancel();
  }

  @Inject(method = "addBlockHitEffects", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$filterBlockHit(BlockPos pos, EnumFacing side, CallbackInfo ci) {
    if (!GlobalOptions.animationEnabled("item_block_break")) ci.cancel();
  }
}
