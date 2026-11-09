package dev.cbui.hud.module;

import dev.cbui.hud.Anchor;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;

/** Server latency as reported by the tab list entry for the local player. */
public final class PingModule extends LabelModule {
    public PingModule() { super("Ping", Anchor.TOP_RIGHT, -5, 27, "48 ms"); }

    @Override public void tick() {
        NetHandlerPlayClient net = mc.getNetHandler();
        NetworkPlayerInfo info = net == null || mc.thePlayer == null ? null : net.getPlayerInfo(mc.thePlayer.getUniqueID());
        if (info == null || mc.isSingleplayer()) label("-- ms"); else label(info.getResponseTime(), " ms");
    }
}
