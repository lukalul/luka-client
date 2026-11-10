package dev.cbui.mixin;

import dev.cbui.StallLog;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame and tick boundaries for the stall log. */
@Mixin(Minecraft.class)
public abstract class StallLogMinecraftMixin {
  @Inject(method = "runGameLoop", at = @At("HEAD"), require = 0)
  private void cbui$stallFrame(CallbackInfo ci) {
    StallLog.frame();
  }

  @Inject(method = "runTick", at = @At("HEAD"), require = 0)
  private void cbui$stallTick(CallbackInfo ci) {
    StallLog.tick();
  }
}
