package dev.cbui.mixin;

import dev.cbui.config.OptimalPreset;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A new player gets luka's optimal video settings once the game has started; whether they are new is decided before it loads. */
@Mixin(Minecraft.class)
public abstract class OptimalPresetMinecraftMixin {
  @Inject(method = "startGame", at = @At("HEAD"), require = 0)
  private void cbui$noticeConfig(CallbackInfo ci) {
    OptimalPreset.noticeConfig();
  }

  @Inject(method = "startGame", at = @At("RETURN"), require = 0)
  private void cbui$optimalForNewPlayer(CallbackInfo ci) {
    OptimalPreset.firstLaunch();
  }
}
