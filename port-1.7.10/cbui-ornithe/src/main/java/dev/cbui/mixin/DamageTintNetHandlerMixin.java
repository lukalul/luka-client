package dev.cbui.mixin;

import dev.cbui.feature.damagetint.DamageTracking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Watches the packets that announce a hit, so the tint can tell normal, blocked and critical hits apart. */
@Mixin(NetHandlerPlayClient.class)
public abstract class DamageTintNetHandlerMixin {
  @Inject(method = "handleEntityStatus", at = @At("TAIL"), require = 0)
  private void cbui$noteHurt(S19PacketEntityStatus packet, CallbackInfo ci) {
    try {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc.theWorld != null && packet.m_59227312() == DamageTracking.STATUS_HURT) DamageTracking.recordHurt(packet.m_16748207(mc.theWorld));
    } catch (Throwable failure) {
      // the tint simply stays on its default colour
    }
  }

  @Inject(method = "handleAnimation", at = @At("TAIL"), require = 0)
  private void cbui$noteCrit(S0BPacketAnimation packet, CallbackInfo ci) {
    try {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc.theWorld != null && packet.m_87015290() == DamageTracking.ANIMATION_CRIT) {
        DamageTracking.recordCritAnimation(mc.theWorld.getEntityByID(packet.m_18157545()));
      }
    } catch (Throwable failure) {
      // the tint simply stays on its default colour
    }
  }
}
