package dev.cbui.social;

import dev.cbui.Cbui;
import dev.cbui.feature.Features;
import dev.cbui.feature.online.LukaOnlineModule;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/** The "friends here" tag in the multiplayer server list. */
public final class ServerFriends {
  private ServerFriends() {}

  /** The address without the default port and in lower case, so "Play.Example.net:25565" and "play.example.net" match. */
  static String normalize(String address) {
    if (address == null) return "";
    String a = address.trim().toLowerCase(Locale.ROOT);
    if (a.endsWith(":25565")) a = a.substring(0, a.length() - 6);
    return a;
  }

  /** Draws the tag after the server's name (the caller passes the entry's top-left corner). */
  public static void draw(ServerData server, int x, int y) {
    try {
      if (server == null || server.serverIP == null) return;
      LukaOnlineModule module = Features.get(LukaOnlineModule.class);
      if (module == null || !module.enabled() || !LukaNet.connected()) return;
      List<LukaNet.Person> friends = LukaNet.friends().friends;
      if (friends.isEmpty()) return;
      String wanted = normalize(server.serverIP);
      int count = 0;
      String first = "";
      for (LukaNet.Person p : friends) {
        if (p.online && normalize(p.server).equals(wanted)) {
          if (count++ == 0) first = p.name;
        }
      }
      if (count == 0) return;
      Minecraft mc = Minecraft.getMinecraft();
      String label = count == 1 ? first + " is here" : count + " friends here";
      int nameWidth = mc.fontRendererObj.getStringWidth(server.serverName == null ? "" : server.serverName);
      float lx = x + 35 + nameWidth + 8;
      RankIcon.blit(lx, y, 10, 0xB26FFF, 1f);
      mc.fontRendererObj.drawStringWithShadow(label, lx + 13, y + 1, 0xFFB26FFF);
    } catch (Throwable error) {
      Cbui.reportOnce("server list friends", error);
    }
  }
}
