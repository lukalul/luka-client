package dev.cbui.mixin;

import dev.cbui.Cbui;
import dev.cbui.feature.shotchat.ShotLinks;
import net.minecraft.client.entity.EntityClientPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Screenshot message: safety net so the hidden screenshot command can never be sent to the server. */
@Mixin(EntityClientPlayerMP.class)
public abstract class ShotChatEntityClientPlayerMPMixin {
  @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true, require = 0)
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
