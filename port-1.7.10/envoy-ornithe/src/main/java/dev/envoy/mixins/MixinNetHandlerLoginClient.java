package dev.envoy.mixins;

import dev.envoy.Envoy;
import net.minecraft.client.network.NetHandlerLoginClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.login.server.S02PacketLoginSuccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerLoginClient.class)
public abstract class MixinNetHandlerLoginClient {
    /** The login handler's NetworkManager; the MCP 1.7.10 mappings leave this field unnamed. */
    @Shadow @Final private NetworkManager f_40997890;

    @Inject(method = "handleLoginSuccess", at = @At("RETURN"), require = 0)
    private void envoy$finishLogin(S02PacketLoginSuccess success, CallbackInfo callback) {
        Envoy.onConnect(this.f_40997890);
    }
}
