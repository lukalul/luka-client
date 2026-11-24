package dev.cbui.mixin;

import dev.cbui.Cbui;
import dev.cbui.feature.lookaround.LookAroundModule;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freelook: notices a server's "freelook:disable" plugin message (after the packet has moved to the main thread). */
@Mixin(NetHandlerPlayClient.class)
public abstract class LookAroundNetHandlerMixin {
  @Inject(method = "handleCustomPayload", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V",
      shift = At.Shift.AFTER), require = 0)
  private void cbui$lookAroundServerSwitch(S3FPacketCustomPayload packet, CallbackInfo ci) {
    LookAroundModule look = LookAroundModule.instance();
    if (look == null) return;
    try {
      look.serverMessage(packet.getChannelName());
    } catch (Throwable error) {
      Cbui.reportOnce("freelook server message", error);
    }
  }
}
