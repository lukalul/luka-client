package dev.cbui.mixin;

import dev.cbui.feature.Features;
import dev.cbui.feature.betterhurtcam.BetterHurtCamModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class BetterHurtCamMinecraftMixin {
  @Inject(method = "runTick", at = @At("TAIL"), require = 0)
  private void cbui$betterHurtCamKeys(CallbackInfo ci) {
    try {
      BetterHurtCamModule module = Features.get(BetterHurtCamModule.class);
      if (module != null) module.pollKeys();
    } catch (Throwable ignored) { }
  }
}
