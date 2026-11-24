package dev.cbui.mixin;

import dev.cbui.Cbui;
import dev.cbui.feature.shotchat.ShotLinks;
import net.minecraft.client.gui.GuiChat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Screenshot message: a click on a screenshot button arrives here as chat text. It is answered before the text is
 * added to the sent-message history (up arrow) and before it could reach the server.
 */
@Mixin(GuiChat.class)
public abstract class ShotChatGuiChatMixin {
  @Inject(method = "submitChatMessage", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$screenshotCommand(String message, CallbackInfo ci) {
    try {
      if (!ShotLinks.isCommand(message)) return;
      ci.cancel();
      ShotLinks.run(message);
    } catch (Throwable error) {
      Cbui.reportOnce("Screenshot Message Enhancer command hook", error);
    }
  }
}
