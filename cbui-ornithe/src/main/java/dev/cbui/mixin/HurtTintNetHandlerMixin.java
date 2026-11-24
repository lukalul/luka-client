package dev.cbui.mixin;

import dev.cbui.feature.hurttint.HurtTintModule;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Damage Tint: notes hurts and critical sparkles once the game has applied them (on the client thread). */
@Mixin(NetHandlerPlayClient.class)
public abstract class HurtTintNetHandlerMixin {
  @Inject(method = "handleEntityStatus", at = @At("TAIL"), require = 0)
  private void cbui$hurtTintStatus(S19PacketEntityStatus packet, CallbackInfo ci) {
    HurtTintModule.statusHandled(packet);
  }

  @Inject(method = "handleAnimation", at = @At("TAIL"), require = 0)
  private void cbui$hurtTintAnimation(S0BPacketAnimation packet, CallbackInfo ci) {
    HurtTintModule.animationHandled(packet);
  }
}
