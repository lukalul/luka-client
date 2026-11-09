package dev.cbui.mixin;

import dev.cbui.feature.screenshotmessageenhancer.ScreenshotActions;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityPlayerSP.class)
public abstract class ScreenshotMessageEnhancerEntityPlayerSPMixin {
  @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$consumeScreenshotAction(String chat, CallbackInfo callback) {
    if (!ScreenshotActions.accepts(chat)) return;
    callback.cancel();
    ScreenshotActions.execute(chat);
  }
}
