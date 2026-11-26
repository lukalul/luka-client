package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.report.LogScrub;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Minecraft's constructor logs "(Session ID is token:<access token>:<uuid>)" on every start, so every latest.log held
 * the player's sign-in token (and SEND LOGS sent it). The line now says the session ID is hidden; everything else the
 * constructor logs ("Setting user: name", ...) passes through LogScrub unchanged.
 */
@Mixin(Minecraft.class)
public abstract class SessionLogMixin {
  @WrapOperation(method = "<init>", require = 0,
      at = @At(value = "INVOKE", target = "Lorg/apache/logging/log4j/Logger;info(Ljava/lang/String;)V", remap = false))
  private void cbui$hideSessionId(Logger logger, String message, Operation<Void> original) {
    String safe = message;
    try {
      safe = LogScrub.clean(message);
    } catch (Throwable ignored) {
      if (message != null && message.startsWith("(Session ID is")) safe = "(Session ID hidden by luka)";
    }
    original.call(logger, safe);
  }
}
