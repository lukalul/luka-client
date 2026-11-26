package dev.cbui.feature.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.cbui.Cbui;
import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import dev.cbui.social.DiscordIpc;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Discord Rich Presence: your Discord profile shows that you play luka, where (menus, singleplayer, or the server, if you
 * allow it), the version and how long you have been at it. Switching the card off clears the status while you play.
 *
 * <p>Started by the luka launcher (1.6.7 on), the launcher owns the status ("Idle / In the launcher" before and after
 * the game): the game only writes where the player is and these choices into the file named by -Dluka.discordState,
 * and the launcher shows it. Started any other way, the game talks to the Discord app on this computer itself, as
 * before. The replay editor's render game never touches the status.
 */
public final class DiscordStatusModule extends FeatureModule implements Features.Synced {
  /** The Discord application the status is shown under (its name is what Discord shows: "Playing luka"). */
  public static final String APP_ID = System.getProperty("luka.discordAppId", dev.cbui.Release.DISCORD_APP_ID);
  private static final String LOGO = "luka-logo"; // Rich Presence art asset of the luka Discord app
  private static final String SITE = "https://client.luka.wtf";
  private static final String INVITE = "https://discord.gg/7gsx7KMT5E";

  public final Setting.Toggle server = add(new Setting.Toggle("Show server", true,
      "Your Discord profile shows the server you are on. Off, or with Streamer mode on: it only says multiplayer."));
  public final Setting.Toggle showVersion = add(new Setting.Toggle("Show version", true,
      "Your Discord status shows luka's and Minecraft's version. Off: it just says you play luka."));
  public final Setting.Toggle button = add(new Setting.Toggle("Show buttons", true,
      "Website and Discord buttons on your Discord status, for luka's website and Discord server."));

  /** The launcher's state file (-Dluka.discordState), or null when the game was not started by a 1.6.7+ launcher. */
  private static final java.io.File STATE = stateFile();
  private static final boolean RENDER_GAME =
      System.getProperty("subframe.renderserver") != null || System.getProperty("subframe.renderjob") != null;
  private String written = null;

  private static final long GAME_START = System.currentTimeMillis() / 1000L;
  private static volatile String wanted = "";      // the activity as JSON text, "" for none
  private static Thread worker;
  private String place = "";
  private long placeSince = GAME_START;

  public DiscordStatusModule() { super("Discord Status", true); }
  @Override public String category() { return "UTILITY"; }

  @Override public void sync() {
    try {
      if (APP_ID == null || APP_ID.isEmpty() || RENDER_GAME) return;
      if (STATE != null) {
        writeState(Minecraft.getMinecraft());
        return;
      }
      JsonObject activity = enabled() ? activity(Minecraft.getMinecraft()) : null;
      wanted = activity == null ? "" : activity.toString();
      if (worker == null && enabled()) start();
    } catch (Throwable error) {
      Cbui.reportOnce("discord status", error);
    }
  }

  private JsonObject activity(Minecraft mc) {
    String where, details;
    if (mc.theWorld == null) {
      where = "menu";
      details = "In the menus";
    } else if (mc.isSingleplayer()) {
      where = "single";
      details = "Playing singleplayer";
    } else {
      net.minecraft.client.multiplayer.ServerData data = mc.getCurrentServerData();
      String address = data == null || data.serverIP == null ? "" : data.serverIP.trim().toLowerCase();
      boolean hide = !server.get() || dev.cbui.feature.online.LukaOnlineModule.streamerMode() || address.isEmpty();
      where = "mp:" + address;
      details = hide ? "Playing multiplayer" : "Playing on " + shorten(address);
    }
    if (!where.equals(place)) {
      place = where;
      placeSince = where.equals("menu") ? GAME_START : System.currentTimeMillis() / 1000L;
    }
    JsonObject a = new JsonObject();
    a.addProperty("details", details);
    a.addProperty("state", "Minecraft " + version() + (FabricLoader.getInstance().isModLoaded("astatine") ? " · Vulkan" : ""));
    JsonObject time = new JsonObject();
    time.addProperty("start", placeSince);
    a.add("timestamps", time);
    JsonObject assets = new JsonObject();
    assets.addProperty("large_image", LOGO);
    assets.addProperty("large_text", "luka client");
    a.add("assets", assets);
    if (button.get()) {
      JsonArray buttons = new JsonArray();
      JsonObject site = new JsonObject();
      site.addProperty("label", "Website");
      site.addProperty("url", SITE);
      buttons.add(site);
      JsonObject discord = new JsonObject();
      discord.addProperty("label", "Discord");
      discord.addProperty("url", INVITE);
      buttons.add(discord);
      a.add("buttons", buttons);
    }
    return a;
  }

  /** For the launcher: where the player is, and what they allow it to show. Written only when it changes. */
  private void writeState(Minecraft mc) {
    String place, server = "";
    if (mc.theWorld == null) {
      place = "menu";
    } else if (mc.isSingleplayer()) {
      place = "singleplayer";
    } else {
      place = "multiplayer";
      net.minecraft.client.multiplayer.ServerData data = mc.getCurrentServerData();
      String address = data == null || data.serverIP == null ? "" : data.serverIP.trim().toLowerCase();
      boolean hide = !this.server.get() || dev.cbui.feature.online.LukaOnlineModule.streamerMode();
      if (!hide) server = shorten(address);
    }
    String where = place + ":" + server;
    if (!where.equals(this.place)) {
      this.place = where;
      placeSince = place.equals("menu") ? GAME_START : System.currentTimeMillis() / 1000L;
    }
    String text = "show=" + enabled() + "\nplace=" + place + "\nserver=" + server.replace("\\", "").replace("\n", "")
        + "\nshowVersion=" + showVersion.get() + "\nbuttons=" + button.get() + "\nminecraft=" + version() + "\nsince=" + placeSince + "\n";
    if (text.equals(written)) return;
    try {
      java.io.File tmp = new java.io.File(STATE.getPath() + ".tmp");
      java.nio.file.Files.write(tmp.toPath(), text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      java.nio.file.Files.move(tmp.toPath(), STATE.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      written = text;
    } catch (Throwable error) {
      Cbui.reportOnce("discord state", error);
    }
  }

  private static java.io.File stateFile() {
    String path = System.getProperty("luka.discordState");
    return path == null || path.trim().isEmpty() ? null : new java.io.File(path.trim());
  }

  private static String shorten(String address) {
    String s = address.endsWith(":25565") ? address.substring(0, address.length() - 6) : address;
    return s.length() > 60 ? s.substring(0, 60) : s;
  }

  private static String version() {
    return FabricLoader.getInstance().getModContainer("minecraft").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
  }

  /** One background thread keeps the Discord connection and sends the status when it changes (at most every 5 s). */
  private static synchronized void start() {
    if (worker != null) return;
    worker = new Thread(DiscordStatusModule::run, "luka Discord status");
    worker.setDaemon(true);
    worker.start();
  }

  private static void run() {
    DiscordIpc ipc = null;
    String shown = null;
    long nextTry = 0, lastSend = 0;
    long pid = DiscordIpc.pid();
    while (true) {
      try {
        Thread.sleep(1000L);
        String want = wanted;
        long now = System.currentTimeMillis();
        // with the card off the connection stays open and the status is just cleared, so switching it on is instant
        if (want.isEmpty() && ipc == null) { shown = null; continue; }
        if (ipc == null) {
          if (now < nextTry) continue;
          ipc = DiscordIpc.open(APP_ID);
          if (ipc == null) { nextTry = now + 15_000L; continue; }
          shown = null;
        }
        if (want.equals(shown) || now - lastSend < 5_000L) continue;
        ipc.setActivity(want.isEmpty() ? null : new com.google.gson.JsonParser().parse(want).getAsJsonObject(), pid);
        shown = want;
        lastSend = now;
      } catch (InterruptedException stop) {
        return;
      } catch (Throwable lost) {
        // Discord was closed or restarted: connect again later
        if (ipc != null) ipc.close();
        ipc = null;
        nextTry = System.currentTimeMillis() + 15_000L;
      }
    }
  }
}
