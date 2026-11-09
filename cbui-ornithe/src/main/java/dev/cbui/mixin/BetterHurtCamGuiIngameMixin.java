package dev.cbui.mixin;

import dev.cbui.feature.Features;
import dev.cbui.feature.betterhurtcam.BetterHurtCamModule;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = GuiIngame.class, priority = 900)
public abstract class BetterHurtCamGuiIngameMixin {
  @ModifyVariable(method = "renderPlayerStats", at = @At("STORE"), ordinal = 0, require = 0)
  private boolean cbui$betterHurtCamHeartBlink(boolean blinking) {
    try {
      BetterHurtCamModule module = Features.get(BetterHurtCamModule.class);
      return blinking && (module == null || !module.enabled() || module.heartBlink.get());
    } catch (Throwable ignored) {
      return blinking;
    }
  }
}
