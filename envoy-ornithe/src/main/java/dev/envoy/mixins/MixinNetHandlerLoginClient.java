package dev.envoy.mixins;

import dev.envoy.Envoy;
import net.minecraft.client.network.NetHandlerLoginClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.login.server.S02PacketLoginSuccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerLoginClient.class)
public abstract class MixinNetHandlerLoginClient {
    @Shadow private NetworkManager networkManager;
    @Inject(method = "handleLoginSuccess", at = @At("RETURN"), require = 0)
    private void envoy$finishLogin(S02PacketLoginSuccess success, CallbackInfo callback) {
        NetworkManager connection = this.networkManager;
        Envoy.onConnect(connection);
    }
}
