package dev.cbui.mixin;

import java.util.Queue;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** HitFix (1.7.10): the connection's queue of received packets and the handler that applies them. */
@Mixin(NetworkManager.class)
public interface HitFixNetworkAccess {
  @Accessor("receivedPacketsQueue")
  Queue<?> cbui$receivedPackets();

  @Accessor("packetListener")
  INetHandler cbui$packetListener();
}
