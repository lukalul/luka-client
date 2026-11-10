package dev.hcfopt.mixin;

import dev.hcfopt.LightQueue;
import net.minecraft.util.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client light updates go through {@link LightQueue}: queued now, done within a time budget at the next frames. */
@Mixin(World.class)
public abstract class WorldLightMixin {
  @Inject(method = "checkLightFor", at = @At("HEAD"), cancellable = true, require = 0)
  private void hcfopt$queueLight(EnumSkyBlock type, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
    if (LightQueue.defer((World) (Object) this, type, pos)) cir.setReturnValue(Boolean.TRUE);
  }
}
