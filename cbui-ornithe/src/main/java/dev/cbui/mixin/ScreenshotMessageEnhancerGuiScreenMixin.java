package dev.cbui.mixin;

import dev.cbui.feature.screenshotmessageenhancer.ScreenshotActions;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiScreen.class)
public abstract class ScreenshotMessageEnhancerGuiScreenMixin {
  @Inject(method = "sendChatMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$routeScreenshotAction(String chat, boolean addToHistory, CallbackInfo callback) {
    if (ScreenshotActions.accepts(chat)) {
      callback.cancel();
      ScreenshotActions.execute(chat);
    }
  }
}
