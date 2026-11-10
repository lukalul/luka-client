package dev.cbui.hud.module;

import dev.cbui.hud.Anchor;
import net.minecraft.client.network.NetHandlerPlayClient;

/** Server latency as reported by the tab list entry for the local player. */
public final class PingModule extends LabelModule {
    public PingModule() { super("Ping", Anchor.TOP_RIGHT, -5, 27, "48 ms"); }

    @Override public void tick() {
        NetHandlerPlayClient net = mc.getNetHandler();
        int ping = net == null || mc.thePlayer == null ? -1 : dev.luka.compat.Compat.ping(net, mc.thePlayer.getCommandSenderName());
        if (ping < 0 || mc.isSingleplayer()) label("-- ms"); else label(ping, " ms");
    }
}
