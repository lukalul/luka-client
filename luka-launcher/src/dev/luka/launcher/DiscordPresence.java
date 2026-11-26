package dev.luka.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * luka's Discord status, owned by the launcher so it lasts as long as the launcher is open:
 * <ul>
 *   <li>launcher open, no game: "Idle" / "In the launcher", timer since the launcher opened;</li>
 *   <li>game running: "luka &lt;version&gt; · &lt;Minecraft version&gt;" / "Playing on &lt;server&gt;", "Singleplayer" or
 *   "In the menus", timer since the game started. The game's Discord Status module writes where the player is into
 *   {@link #stateFile} (and the player's choices: hide the server, hide the version, no status at all);</li>
 *   <li>game closed again: back to Idle.</li>
 * </ul>
 * Buttons: luka's website, and luka's Discord when {@link #DISCORD_INVITE} is set.
 *
 * <p>When Discord answers, it also names the signed-in Discord user. The launcher sends that name with the Minecraft
 * names of its saved accounts to api.luka.wtf (POST /v1/discord/seen), once per change; the privacy page says so, and
 * the launcher's "Discord status" switch turns both off.
 */
final class DiscordPresence {
  static final String APP_ID = System.getProperty("luka.discordAppId", "1575546703149535485");
  static final String SITE = "https://client.luka.wtf";
  /** luka's Discord invite for the second button; empty: no button. */
  static final String DISCORD_INVITE = System.getProperty("luka.discordInvite", "https://discord.gg/7gsx7KMT5E");
  /** Rich Presence art asset uploaded to the luka Discord app (release-work/discord/luka-logo.png). */
  private static final String LOGO = "luka-logo";

  private final File root;
  private final Settings settings;
  private final Accounts accounts;
  private final Consumer<String> log;
  final File stateFile;
  private final long launcherStart = System.currentTimeMillis() / 1000L;
  private volatile Process game;
  private volatile long gameStart;
  private volatile String mcVersion = "";
  private Thread worker;

  DiscordPresence(File root, Settings settings, Accounts accounts, Consumer<String> log) {
    this.root = root;
    this.settings = settings;
    this.accounts = accounts;
    this.log = log;
    this.stateFile = new File(root, "discord-state.properties");
  }

  /** The launcher's "Discord status" switch (on unless the player turned it off). */
  static boolean enabled(Settings settings) {
    return Boolean.parseBoolean(settings.get("discordStatus", "true"));
  }

  synchronized void start() {
    if (worker != null) return;
    worker = new Thread(this::run, "luka-discord");
    worker.setDaemon(true);
    worker.start();
  }

  /** The game started (Play); its module reports into stateFile until it exits. */
  void gameStarted(Process process, String minecraftVersion) {
    stateFile.delete();
    mcVersion = minecraftVersion == null ? "" : minecraftVersion;
    gameStart = System.currentTimeMillis() / 1000L;
    game = process;
  }

  private void run() {
    DiscordIpc ipc = null;
    String shown = null;
    String sharedFor = null;
    long nextTry = 0, lastSend = 0;
    while (true) {
      try {
        Thread.sleep(1000L);
        long now = System.currentTimeMillis();
        boolean on = enabled(settings);
        if (!on) {
          if (ipc != null) {
            try {
              ipc.setActivity(null);
            } catch (IOException ignored) {
              // closing anyway
            }
            ipc.close();
            ipc = null;
            shown = null;
          }
          continue;
        }
        if (ipc == null || !ipc.alive()) {
          if (ipc != null) ipc.close();
          ipc = null;
          if (now < nextTry) continue;
          ipc = DiscordIpc.connect(APP_ID);
          if (ipc == null) {
            nextTry = now + 15_000L;
            continue;
          }
          shown = null;
          log.accept("Discord status: connected");
        }
        ipc.pump();
        if (ipc.lastError != null) {
          log.accept("Discord status: Discord refused the status: " + ipc.lastError);
          ipc.lastError = null;
        }
        String want = activity();
        if (!want.equals(shown) && now - lastSend >= 2_000L) {
          ipc.setActivity(want.isEmpty() ? null : want);
          shown = want;
          lastSend = now;
        }
        String key = shareKey(ipc.user);
        if (key != null && !key.equals(sharedFor)) {
          sharedFor = key;
          final DiscordIpc.User user = ipc.user;
          Thread t = new Thread(() -> share(user), "luka-discord-share");
          t.setDaemon(true);
          t.start();
        }
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

  /** The activity JSON for now ("" for none). */
  private String activity() {
    Process p = game;
    if (p != null && !p.isAlive()) {
      game = null;
      p = null;
      stateFile.delete();
    }
    String details, state;
    long start;
    boolean buttons = true;
    if (p == null) {
      details = "Idle";
      state = "In the launcher";
      start = launcherStart;
    } else {
      Properties s = readState();
      if ("false".equals(s.getProperty("show"))) return "";
      String luka = LogReport.lukaVersion(root);
      boolean showVersion = !"false".equals(s.getProperty("showVersion"));
      String mc = s.getProperty("minecraft", mcVersion);
      details = showVersion ? ("luka" + (luka.isEmpty() ? "" : " " + luka) + (mc.isEmpty() ? "" : " · " + mc)) : "Playing luka";
      String place = s.getProperty("place", "menu");
      String server = s.getProperty("server", "");
      if (place.equals("multiplayer")) state = server.isEmpty() ? "Playing multiplayer" : "Playing on " + server;
      else if (place.equals("singleplayer")) state = "Singleplayer";
      else state = "In the menus";
      start = parseLong(s.getProperty("since"), gameStart);
      buttons = !"false".equals(s.getProperty("buttons")); // the game's "Show buttons"
    }
    StringBuilder a = new StringBuilder("{");
    a.append("\"details\":").append(DiscordIpc.quote(clip(details))).append(',');
    a.append("\"state\":").append(DiscordIpc.quote(clip(state))).append(',');
    a.append("\"timestamps\":{\"start\":").append(start).append("},");
    a.append("\"assets\":{\"large_image\":").append(DiscordIpc.quote(LOGO)).append(",\"large_text\":\"luka client\"}");
    if (buttons) {
      a.append(",\"buttons\":[{\"label\":\"Website\",\"url\":").append(DiscordIpc.quote(SITE)).append('}');
      if (!DISCORD_INVITE.isEmpty()) {
        a.append(",{\"label\":\"Discord\",\"url\":").append(DiscordIpc.quote(DISCORD_INVITE)).append('}');
      }
      a.append(']');
    }
    a.append('}');
    return a.toString();
  }

  private Properties readState() {
    Properties p = new Properties();
    try {
      if (stateFile.isFile()) {
        try (java.io.Reader r = new java.io.InputStreamReader(new java.io.FileInputStream(stateFile), StandardCharsets.UTF_8)) {
          p.load(r);
        }
      }
    } catch (IOException | RuntimeException ignored) {
      // the game is writing it: next second
    }
    return p;
  }

  /** Discord wants 2..128 characters. */
  private static String clip(String s) {
    if (s.length() < 2) s = s + "  ";
    return s.length() > 128 ? s.substring(0, 128) : s;
  }

  private static long parseLong(String s, long fallback) {
    try {
      return s == null ? fallback : Long.parseLong(s.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  // ------------------------------------------------------------------ sharing the Discord name with luka

  /** What has to be shared: the Discord user and the account names; null when Discord named no user. */
  private String shareKey(DiscordIpc.User user) {
    if (user == null || user.id == null) return null;
    StringBuilder k = new StringBuilder(user.id).append('|').append(user.username).append('|').append(user.globalName);
    for (Accounts.Account a : accounts.all()) k.append('|').append(a.name).append(':').append(a.uuid);
    return k.toString();
  }

  private void share(DiscordIpc.User user) {
    try {
      File seen = new File(root, "discord-shared.txt");
      String body = shareJson(user);
      String digest = sha256(body);
      if (seen.isFile() && digest.equals(new String(Files.readAllBytes(seen.toPath()), StandardCharsets.UTF_8).trim())) return;
      String api = System.getProperty("luka.api", "https://api.luka.wtf");
      Http.postJson(api + "/v1/discord/seen", body, null);
      Files.write(seen.toPath(), digest.getBytes(StandardCharsets.UTF_8));
      log.accept("Discord status: shared the Discord name and " + accounts.all().size() + " account name(s) with luka");
    } catch (Throwable e) {
      log.accept("Discord status: could not share the Discord name: " + e.getMessage());
    }
  }

  private String shareJson(DiscordIpc.User user) {
    StringBuilder j = new StringBuilder("{");
    j.append("\"installId\":").append(DiscordIpc.quote(GameRunner.installId(root))).append(',');
    j.append("\"discordId\":").append(DiscordIpc.quote(nz(user.id))).append(',');
    j.append("\"username\":").append(DiscordIpc.quote(nz(user.username))).append(',');
    j.append("\"globalName\":").append(DiscordIpc.quote(nz(user.globalName))).append(',');
    j.append("\"accounts\":[");
    List<Accounts.Account> all = accounts.all();
    for (int i = 0; i < all.size(); i++) {
      Accounts.Account a = all.get(i);
      if (i > 0) j.append(',');
      j.append("{\"name\":").append(DiscordIpc.quote(nz(a.name)))
          .append(",\"uuid\":").append(DiscordIpc.quote(nz(a.uuid)))
          .append(",\"microsoft\":").append(a.microsoft()).append('}');
    }
    return j.append("]}").toString();
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }

  private static String sha256(String s) throws Exception {
    byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
    StringBuilder b = new StringBuilder();
    for (byte x : d) b.append(String.format("%02x", x));
    return b.toString();
  }
}
