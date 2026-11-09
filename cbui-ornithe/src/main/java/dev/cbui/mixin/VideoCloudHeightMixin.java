package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Cloud height row. 128 is vanilla's height. */
@Mixin(WorldProvider.class)
public abstract class VideoCloudHeightMixin {
  @Inject(method = "getCloudHeight", at = @At("RETURN"), cancellable = true, require = 0)
  private void cbui$cloudHeight(CallbackInfoReturnable<Float> result) {
    if (GlobalOptions.cloudHeight != 128) result.setReturnValue((float) GlobalOptions.cloudHeight);
  }
}
