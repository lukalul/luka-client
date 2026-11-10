package dev.cbui.hud.module;

import dev.cbui.hud.Anchor;
import net.minecraft.client.multiplayer.ServerData;

/** The address of the connected server, or "Singleplayer". */
public final class ServerAddressModule extends LabelModule {
    public ServerAddressModule() { super("Server address", Anchor.TOP_CENTER, 0, 71, "play.example.net"); }

    @Override public void tick() {
        ServerData data = mc.getCurrentServerData();
        if (!mc.isSingleplayer() && dev.cbui.feature.online.LukaOnlineModule.streamerMode()) { label("Streamer mode"); return; }
        label(mc.isSingleplayer() ? "Singleplayer" : data != null && data.serverIP != null ? data.serverIP : "Not connected");
    }
}
