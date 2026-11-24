package dev.cbui.mixin;

import dev.cbui.Cbui;
import dev.cbui.feature.lookaround.LookAroundModule;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freelook: notices a server's "freelook:disable" plugin message (1.7.10 handles packets on the main thread). */
@Mixin(NetHandlerPlayClient.class)
public abstract class LookAroundNetHandlerMixin {
  @Inject(method = "handleCustomPayload", at = @At("HEAD"), require = 0)
  private void cbui$lookAroundServerSwitch(S3FPacketCustomPayload packet, CallbackInfo ci) {
    LookAroundModule look = LookAroundModule.instance();
    if (look == null) return;
    try {
      // the channel getter has no readable name in this version's mappings
      look.serverMessage(packet.m_45175726());
    } catch (Throwable error) {
      Cbui.reportOnce("freelook server message", error);
    }
  }
}
