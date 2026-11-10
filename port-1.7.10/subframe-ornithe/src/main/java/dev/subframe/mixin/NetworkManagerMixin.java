package dev.subframe.mixin;

import dev.subframe.SubFrameMod;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetworkManager.class)
abstract class NetworkManagerMixin {
    @Inject(method = "channelInactive", at = @At("HEAD"))
    private void subframe$disconnected(ChannelHandlerContext context, CallbackInfo ci) {
        SubFrameMod.onDisconnect();
    }
}
