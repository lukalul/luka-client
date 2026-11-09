package dev.subframe.mixin;

import dev.subframe.SubFrameMod;
import net.minecraft.client.network.NetHandlerLoginClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.login.server.S02PacketLoginSuccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerLoginClient.class)
abstract class NetHandlerLoginClientMixin {
    @Shadow private NetworkManager networkManager;

    @Inject(method = "handleLoginSuccess", at = @At("HEAD"))
    private void subframe$connected(S02PacketLoginSuccess packet, CallbackInfo ci) {
        SubFrameMod.onLoginSuccess(networkManager);
    }
}
