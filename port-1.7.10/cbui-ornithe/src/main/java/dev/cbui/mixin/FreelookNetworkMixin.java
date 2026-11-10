package dev.cbui.mixin;

import dev.cbui.feature.freelook.FreelookModule;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Watches for the server's request to turn Freelook off. */
@Mixin(NetHandlerPlayClient.class)
public abstract class FreelookNetworkMixin {
  @Inject(method = "handleCustomPayload", at = @At("HEAD"), require = 0)
  private void cbui$obeyServerOptOut(S3FPacketCustomPayload packet, CallbackInfo ci) {
    try {
      if (!"freelook:disable".equals(packet.m_45175726())) return;
      FreelookModule module = FreelookModule.instance();
      if (module != null) module.serverDisabled();
    } catch (Throwable failure) {
      // ignore a payload we cannot act on
    }
  }
}
