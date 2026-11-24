package dev.cbui.mixin;

import dev.cbui.Cbui;
import dev.cbui.feature.shotchat.ShotLinks;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Screenshot message: a click on a screenshot button is answered here, before chat history or the server see it. */
@Mixin(GuiScreen.class)
public abstract class ShotChatGuiScreenMixin {
  @Inject(method = "sendChatMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$screenshotCommand(String message, boolean addToChat, CallbackInfo ci) {
    try {
      if (!ShotLinks.isCommand(message)) return;
      ci.cancel();
      ShotLinks.run(message);
    } catch (Throwable error) {
      Cbui.reportOnce("Screenshot Message Enhancer command hook", error);
    }
  }
}
