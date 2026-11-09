package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class AnimationsStarsMixin {
  @Inject(method = "getStarBrightness", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$stars(float partialTicks, CallbackInfoReturnable<Float> result) {
    if (!GlobalOptions.animationEnabled("stars")) result.setReturnValue(0.0F);
  }
}
