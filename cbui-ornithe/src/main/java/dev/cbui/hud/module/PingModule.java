package dev.cbui.hud.module;

import dev.cbui.hud.Anchor;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;

/** Server latency as reported by the tab list entry for the local player. */
public final class PingModule extends ValueModule {
    public PingModule() { super("Ping", false, Anchor.TOP_RIGHT, -5, 27, "ms", "48", new Range(1000, 1, 50, GREEN, 150, RED)); }

    @Override public void tick() {
        NetHandlerPlayClient net = mc.getNetHandler();
        NetworkPlayerInfo info = net == null || mc.thePlayer == null ? null : net.getPlayerInfo(mc.thePlayer.getUniqueID());
        if (info == null || mc.isSingleplayer()) noValue("--"); else value(info.getResponseTime());
    }
}
