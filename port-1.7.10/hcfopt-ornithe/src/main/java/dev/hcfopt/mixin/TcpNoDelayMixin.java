package dev.hcfopt.mixin;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TCP_NODELAY on the server connection, as Minecraft does from 1.8.1 on. 1.7.10 opens it with TCP_NODELAY off, so
 * Windows may hold small outgoing packets (clicks, hits, movement, keep-alive answers) back to send them together with
 * the next ones, which adds delay and shows up as higher ping. Set when the connection becomes active; a single-player
 * world's in-memory connection has no such option and is left alone.
 */
@Mixin(NetworkManager.class)
public abstract class TcpNoDelayMixin {
  @Unique private static boolean hcfopt$logged;

  @Inject(method = "channelActive", at = @At("RETURN"), require = 0, remap = false)
  private void hcfopt$noDelay(ChannelHandlerContext context, CallbackInfo ci) {
    try {
      if (context.channel().config().setOption(ChannelOption.TCP_NODELAY, Boolean.TRUE) && !hcfopt$logged) {
        hcfopt$logged = true;
        org.apache.logging.log4j.LogManager.getLogger("hcfopt").info("TCP_NODELAY on for the server connection");
      }
    } catch (Throwable unsupported) {
      // a channel without TCP options
    }
  }
}
