package dev.cbui.hud.module;

import dev.cbui.hud.Anchor;
import net.minecraft.client.network.NetHandlerPlayClient;

/** Server latency as reported by the tab list entry for the local player. */
public final class PingModule extends ValueModule {
    public PingModule() { super("Ping", false, Anchor.TOP_RIGHT, -5, 27, "ms", "48", new Range(1000, 1, 50, GREEN, 150, RED)); }

    @Override public void tick() {
        NetHandlerPlayClient net = mc.getNetHandler();
        int ping = net == null || mc.thePlayer == null ? -1 : dev.luka.compat.Compat.ping(net, mc.thePlayer.getCommandSenderName());
        if (ping < 0 || mc.isSingleplayer()) noValue("--"); else value(ping);
    }
}
