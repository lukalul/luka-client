package dev.cbui.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.cbui.Cbui;
import dev.cbui.feature.online.LukaOnlineModule;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;

/**
 * The client's link to luka's online service: signs in, tells the service you are online, asks which of the players around you use luka
 * (and their rank and cape), and keeps the friends list. Nothing here ever blocks the game: all requests run on one background thread and the
 * game only reads what has already arrived. If the service cannot be reached the client just retries later, and the game is unaffected.
 *
 * The address of the service is https://api.luka.wtf unless config/luka-network.json ({"api": "http://127.0.0.1:8787"}), the system property
 * luka.api or the environment variable LUKA_API says otherwise (the last two win over the file).
 */
public final class LukaNet {
  public static final String DEFAULT_API = "https://api.luka.wtf";

  /** What is known about one luka user. */
  public static final class Info {
    public final String uuid, name, rank, cape;

    Info(String uuid, String name, String rank, String cape) {
      this.uuid = uuid;
      this.name = name;
      this.rank = rank;
      this.cape = cape;
    }
  }

  /** One row of the friends screen. */
  public static final class Person {
    public final String uuid, name, rank, cape, server;
    public final boolean online;

    Person(JsonObject o) {
      uuid = str(o, "uuid");
      name = str(o, "name");
      rank = str(o, "rank");
      cape = str(o, "cape");
      server = str(o, "server");
      online = o.has("online") && !o.get("online").isJsonNull() && o.get("online").getAsBoolean();
    }
  }

  /** The friends, incoming requests and requests I sent, as last received. */
  public static final class FriendsData {
    public final List<Person> friends, incoming, outgoing;

    FriendsData(List<Person> f, List<Person> i, List<Person> o) {
      friends = f;
      incoming = i;
      outgoing = o;
    }
  }

  /** One cape in the account's catalogue. */
  public static final class CapeEntry {
    public final String id, name, kind;
    public final boolean owned, animated;

    CapeEntry(JsonObject o) {
      id = str(o, "id");
      name = str(o, "name");
      kind = str(o, "kind");
      owned = o.has("owned") && o.get("owned").getAsBoolean();
      animated = o.has("animated") && o.get("animated").getAsBoolean();
    }
  }

  /** One direct message between me and a friend. */
  public static final class Message {
    public final long id, ts;
    public final String from, to, text;

    Message(JsonObject o) {
      id = o.has("id") ? o.get("id").getAsLong() : 0;
      ts = o.has("ts") ? o.get("ts").getAsLong() : 0;
      from = str(o, "from");
      to = str(o, "to");
      text = str(o, "text");
    }
  }

  /** What the render thread reads: keyed by the UUID object so a lookup per player per frame builds no strings. */
  private static final class Cached {
    final Info info; // null: known not to be a luka user
    final long at;

    Cached(Info info, long at) {
      this.info = info;
      this.at = at;
    }
  }

  private static final Info NONE = new Info("", "", "", "");
  private static final long FOUND_TTL = 60_000L, MISSING_TTL = 180_000L;

  private static final Map<String, Info> known = new ConcurrentHashMap<String, Info>();
  private static final Map<String, Long> fetchedAt = new ConcurrentHashMap<String, Long>();
  private static final Map<UUID, Cached> byUuid = new ConcurrentHashMap<UUID, Cached>();
  private static volatile UUID selfUuid;
  private static final Set<String> wanted = Collections.synchronizedSet(new HashSet<String>());
  private static final Set<String> lastOnline = new HashSet<String>();
  private static final List<String> notices = Collections.synchronizedList(new ArrayList<String>());

  private static final ExecutorService worker = Executors.newSingleThreadExecutor(new ThreadFactory() {
    @Override public Thread newThread(Runnable r) {
      Thread t = new Thread(r, "luka-net");
      t.setDaemon(true);
      return t;
    }
  });

  private static volatile String token;
  private static volatile Info self;
  private static volatile FriendsData friends = new FriendsData(Collections.<Person>emptyList(), Collections.<Person>emptyList(), Collections.<Person>emptyList());
  private static volatile String status = "Not connected";
  private static volatile String lastAction = "";
  private static volatile boolean friendsLoaded;
  private static volatile boolean fastFriends;
  private static volatile String apiBase;
  /** Conversations by the friend's uuid; guarded by itself. */
  private static final Map<String, List<Message>> conversations = new HashMap<String, List<Message>>();
  private static final Map<String, Integer> unreadCount = new HashMap<String, Integer>();
  /** A ping from a friend waiting to be shown (made on the network thread, shown on the client thread). */
  private static final class IncomingPing {
    final long id;
    final String name, rank;
    final int x, y, z, dim;

    IncomingPing(JsonObject o) {
      id = o.get("id").getAsLong();
      name = str(o, "name");
      rank = str(o, "rank");
      x = o.get("x").getAsInt();
      y = o.get("y").getAsInt();
      z = o.get("z").getAsInt();
      dim = o.has("dim") ? o.get("dim").getAsInt() : 0;
    }
  }

  private static final List<IncomingPing> incomingPings = Collections.synchronizedList(new ArrayList<IncomingPing>());
  /** Waypoints made for pings: {waypoint id, expiry time}. Client thread only. */
  private static final List<Object[]> pingMarks = new ArrayList<Object[]>();
  private static long lastPingId;
  private static long nextPings;
  private static volatile boolean busyPings;
  private static String pingServer = "";
  private static final long PING_LIFETIME = 180_000L;

  private static long lastMessageId;
  private static long nextMessages;
  private static volatile boolean busyMessages, messagesLoaded;
  private static volatile String openChat = "";
  private static volatile List<CapeEntry> capeCatalog = Collections.emptyList();
  private static volatile String capeSelected = "";
  private static volatile boolean capesLoaded, busyCapes;

  private static long nextSession, nextHeartbeat, nextFriends, nextScan, nextLookup;
  /** How often the service wants to hear that this player is still online (it says so at sign-in). */
  private static volatile long heartbeatMs = 30_000L;
  private static long backoff = 10_000L;
  private static volatile boolean signingIn, busyHeartbeat, busyFriends, busyLookup;
  private static volatile String presenceServer;

  private LukaNet() {}

  // ---- read side (render and UI threads)

  /** The luka user behind this player, or null when they do not use luka (or it is not known yet; a lookup is then queued). */
  public static Info info(UUID id) {
    if (id == null || token == null) return null; // not signed in (offline, service down): nobody is known, and nothing is queued or built per frame
    Cached fast = byUuid.get(id);
    if (fast != null && System.currentTimeMillis() - fast.at <= (fast.info == null ? MISSING_TTL : FOUND_TTL)) return fast.info;
    if (id.equals(selfUuid)) return self;
    String key = id.toString();
    Info me = self;
    if (me != null && me.uuid.equals(key)) return me;
    Info cached = known.get(key);
    Long when = fetchedAt.get(key);
    long age = when == null ? Long.MAX_VALUE : System.currentTimeMillis() - when;
    if (cached == null || age > (cached == NONE ? MISSING_TTL : FOUND_TTL)) wanted.add(key);
    return cached == null || cached == NONE ? null : cached;
  }

  public static Info self() { return self; }
  public static boolean connected() { return token != null; }
  public static String status() { return status; }
  public static String lastAction() { return lastAction; }
  public static FriendsData friends() { return friends; }
  public static boolean friendsLoaded() { return friendsLoaded; }
  public static String api() { return api0(); }

  /** The friends screen asks for quicker refreshes while it is open. */
  public static void fastFriends(boolean on) {
    fastFriends = on;
    if (on) nextFriends = 0;
  }

  // ---- pings: mark a place for the friends who are on the same server

  /** Sends the place to friends on this server and marks it for yourself. Call on the client thread while in a multiplayer world. */
  public static void sendPing(int x, int y, int z) {
    Minecraft mc = Minecraft.getMinecraft();
    final String server = currentServer(mc);
    if (token == null || server == null || mc.theWorld == null) {
      lastAction = "Pings need luka online and a multiplayer server";
      return;
    }
    final int dim = mc.theWorld.provider.getDimensionId();
    final int px = x, py = y, pz = z;
    mark("Ping: you", px, py, pz, 0xFFFFFFFF);
    lastAction = "Ping sent";
    worker.execute(new Runnable() {
      @Override public void run() {
        try {
          JsonObject body = new JsonObject();
          body.addProperty("server", server);
          body.addProperty("x", px);
          body.addProperty("y", py);
          body.addProperty("z", pz);
          body.addProperty("dim", dim);
          call("POST", "/v1/pings", body);
          nextPings = 0;
        } catch (ApiException e) {
          lastAction = e.getMessage();
        } catch (Exception e) {
          lastAction = "Could not reach luka online";
        }
      }
    });
  }

  private static void mark(String name, int x, int y, int z, int argb) {
    String id = PingWaypoints.add(name, x, y, z, argb);
    if (id != null) pingMarks.add(new Object[] {id, System.currentTimeMillis() + PING_LIFETIME});
  }

  private static void refreshPings(final String server) {
    try {
      JsonObject reply = call("GET", "/v1/pings?after=" + lastPingId + "&server=" + URLEncoder.encode(server, "UTF-8"), null);
      JsonArray array = reply.getAsJsonArray("pings");
      if (array != null) {
        for (JsonElement e : array) {
          IncomingPing p = new IncomingPing(e.getAsJsonObject());
          if (p.id <= lastPingId) continue;
          lastPingId = p.id;
          incomingPings.add(p);
        }
      }
    } catch (Exception ignored) {
      // tried again soon
    } finally {
      nextPings = System.currentTimeMillis() + 3_000L;
      busyPings = false;
    }
  }

  /** Client thread: shows what arrived and removes the markers that ran out. */
  private static void tickPings(Minecraft mc, LukaOnlineModule module, long now) {
    String server = currentServer(mc);
    if (server == null || mc.theWorld == null) {
      pingServer = "";
      return;
    }
    if (!server.equals(pingServer)) { // a new server: start the list again
      pingServer = server;
      lastPingId = 0;
      incomingPings.clear();
      if (pingMarks.isEmpty()) PingWaypoints.purgeLeftovers();
    }
    if (now >= nextPings && !busyPings && module.showPings()) {
      busyPings = true;
      final String s = server;
      worker.execute(new Runnable() {
        @Override public void run() { refreshPings(s); }
      });
    }
    if (!incomingPings.isEmpty()) {
      List<IncomingPing> batch;
      synchronized (incomingPings) {
        batch = new ArrayList<IncomingPing>(incomingPings);
        incomingPings.clear();
      }
      int dimension = mc.theWorld.provider.getDimensionId();
      for (IncomingPing p : batch) {
        if (!module.showPings() || p.dim != dimension) continue;
        mark("Ping: " + p.name, p.x, p.y, p.z, 0xFF000000 | Ranks.get(p.rank).color);
        if (module.friendAlerts()) mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("§5[luka] §f" + p.name + " §7pinged §f" + p.x + " " + p.y + " " + p.z));
        if (mc.getSoundHandler() != null) mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(new net.minecraft.util.ResourceLocation("note.pling"), 1.4f));
      }
    }
    for (java.util.Iterator<Object[]> it = pingMarks.iterator(); it.hasNext();) {
      Object[] m = it.next();
      if (now >= (Long) m[1]) {
        PingWaypoints.remove((String) m[0]);
        it.remove();
      }
    }
  }

  // ---- direct messages

  /** The conversation with this friend, oldest first. */
  public static List<Message> conversation(String uuid) {
    synchronized (conversations) {
      List<Message> list = conversations.get(uuid);
      return list == null ? new ArrayList<Message>() : new ArrayList<Message>(list);
    }
  }

  public static int unread(String uuid) {
    synchronized (conversations) {
      Integer n = unreadCount.get(uuid);
      return n == null ? 0 : n;
    }
  }

  public static int totalUnread() {
    int sum = 0;
    synchronized (conversations) {
      for (Integer n : unreadCount.values()) sum += n;
    }
    return sum;
  }

  /** The message screen reports which friend it shows: that conversation counts as read and refreshes faster. */
  public static void openChat(String uuid) {
    openChat = uuid == null ? "" : uuid;
    if (!openChat.isEmpty()) {
      synchronized (conversations) { unreadCount.remove(openChat); }
      nextMessages = 0;
    }
  }

  /** Sends a message to a friend (in the background; it appears in the conversation when the service has it). */
  public static void sendMessage(final String toUuid, final String text) {
    final String clean = text.trim();
    if (clean.isEmpty() || token == null) return;
    lastAction = "...";
    worker.execute(new Runnable() {
      @Override public void run() {
        try {
          JsonObject body = new JsonObject();
          body.addProperty("to", toUuid);
          body.addProperty("text", clean);
          call("POST", "/v1/messages", body);
          lastAction = "";
          nextMessages = 0;
        } catch (ApiException e) {
          lastAction = e.getMessage();
        } catch (Exception e) {
          lastAction = "Could not reach luka online";
        }
      }
    });
  }

  private static void refreshMessages() {
    try {
      JsonObject reply = call("GET", "/v1/messages?after=" + lastMessageId, null);
      JsonArray array = reply.getAsJsonArray("messages");
      Info me = self;
      boolean quiet = !messagesLoaded; // the first load is history: no alerts for it
      if (array != null && me != null) {
        for (JsonElement e : array) {
          Message m = new Message(e.getAsJsonObject());
          if (m.id <= lastMessageId) continue;
          lastMessageId = m.id;
          boolean incoming = m.to.equals(me.uuid);
          String other = incoming ? m.from : m.to;
          synchronized (conversations) {
            List<Message> list = conversations.get(other);
            if (list == null) {
              list = new ArrayList<Message>();
              conversations.put(other, list);
            }
            list.add(m);
            if (list.size() > 200) list.remove(0);
            if (incoming && !quiet && !other.equals(openChat)) {
              Integer n = unreadCount.get(other);
              unreadCount.put(other, n == null ? 1 : n + 1);
            }
          }
          if (incoming && !quiet && !other.equals(openChat)) notices.add("§5[luka] §f" + nameOf(other) + "§7: §f" + m.text);
        }
      }
      messagesLoaded = true;
    } catch (Exception ignored) {
      // tried again on the next round
    } finally {
      nextMessages = System.currentTimeMillis() + (openChat.isEmpty() ? 9_000L : 2_000L);
      busyMessages = false;
    }
  }

  private static String nameOf(String uuid) {
    for (Person p : friends.friends) if (p.uuid.equals(uuid)) return p.name;
    return "a friend";
  }

  // ---- capes

  public static List<CapeEntry> capes() { return capeCatalog; }
  /** "" (the cape of my rank), "none" (no cape) or the id of the cape I chose. */
  public static String selectedCape() { return capeSelected; }
  public static boolean capesLoaded() { return capesLoaded; }

  /** Asks the service for the capes this account has and which one is worn (the cape screen calls this when it opens). */
  public static void refreshCapes() {
    if (token == null || busyCapes) return;
    busyCapes = true;
    worker.execute(new Runnable() {
      @Override public void run() {
        try {
          JsonObject reply = call("GET", "/v1/capes", null);
          List<CapeEntry> list = new ArrayList<CapeEntry>();
          JsonArray array = reply.getAsJsonArray("capes");
          if (array != null) for (JsonElement e : array) list.add(new CapeEntry(e.getAsJsonObject()));
          capeSelected = str(reply, "selected");
          capeCatalog = list;
          capesLoaded = true;
        } catch (Exception ignored) {
          // the screen keeps what it had
        } finally {
          busyCapes = false;
        }
      }
    });
  }

  /** Wears this cape on my account ("" = my rank's cape, "none" = none). Other players see it within a minute. */
  public static void equipCape(final String id) {
    final String previous = capeSelected;
    capeSelected = id; // shown at once; put back if the service says no
    lastAction = "...";
    worker.execute(new Runnable() {
      @Override public void run() {
        try {
          JsonObject body = new JsonObject();
          body.addProperty("id", id);
          JsonObject reply = call("POST", "/v1/cape", body);
          Info me = self;
          if (me != null) self = new Info(me.uuid, me.name, me.rank, str(reply, "cape"));
          lastAction = "none".equals(id) ? "Cape removed" : "Cape changed";
        } catch (ApiException e) {
          capeSelected = previous;
          lastAction = e.getMessage();
        } catch (Exception e) {
          capeSelected = previous;
          lastAction = "Could not reach luka online";
        }
      }
    });
  }

  // ---- friend actions (run in the background; the list refreshes right after)

  public static void sendRequest(final String name) { action("request", "name", name, "Request sent to " + name); }
  public static void accept(Person p) { action("accept", "uuid", p.uuid, "You and " + p.name + " are friends now"); }
  public static void decline(Person p) { action("decline", "uuid", p.uuid, "Request removed"); }
  public static void remove(Person p) { action("remove", "uuid", p.uuid, p.name + " removed from your friends"); }

  private static void action(final String what, final String key, final String value, final String done) {
    lastAction = "...";
    worker.execute(new Runnable() {
      @Override public void run() {
        try {
          JsonObject body = new JsonObject();
          body.addProperty(key, value);
          JsonObject reply = call("POST", "/v1/friends/" + what, body);
          String state = reply.has("state") ? reply.get("state").getAsString() : "";
          lastAction = "friends".equals(state) ? "You are friends now" : done;
          nextFriends = 0;
        } catch (ApiException e) {
          lastAction = e.getMessage();
        } catch (Exception e) {
          lastAction = "Could not reach luka online";
        }
      }
    });
  }

  // ---- the tick (client thread)

  /** Called every client tick (by the Luka Online module). Schedules the background work that is due and shows pending friend alerts. */
  public static void tick(LukaOnlineModule module) {
    Minecraft mc = Minecraft.getMinecraft();
    long now = System.currentTimeMillis();
    try {
      if (token == null) {
        if (now >= nextSession && !signingIn && mc.getSession() != null) {
          signingIn = true;
          final String id = String.valueOf(mc.getSession().getProfile().getId());
          final String name = mc.getSession().getUsername();
          worker.execute(new Runnable() {
            @Override public void run() { signIn(id, name); }
          });
        }
        return;
      }
      if (now >= nextHeartbeat && !busyHeartbeat) {
        busyHeartbeat = true;
        presenceServer = module.shareServer() ? currentServer(mc) : null;
        final String server = presenceServer;
        worker.execute(new Runnable() {
          @Override public void run() { heartbeat(server); }
        });
      }
      if (now >= nextFriends && !busyFriends) {
        busyFriends = true;
        worker.execute(new Runnable() {
          @Override public void run() { refreshFriends(); }
        });
      }
      if (dev.cbui.Release.PINGS) tickPings(mc, module, now);
      if (dev.cbui.Release.MESSAGES && now >= nextMessages && !busyMessages && self != null) {
        busyMessages = true;
        worker.execute(new Runnable() {
          @Override public void run() { refreshMessages(); }
        });
      }
      if (now >= nextScan) {
        nextScan = now + 2000L;
        scanPlayers(mc);
      }
      if (now >= nextLookup && !wanted.isEmpty() && !busyLookup) {
        nextLookup = now + 1500L;
        busyLookup = true;
        final List<String> batch = takeBatch(40);
        worker.execute(new Runnable() {
          @Override public void run() { lookup(batch); }
        });
      }
      if (!notices.isEmpty() && mc.thePlayer != null && module.friendAlerts()) {
        synchronized (notices) {
          for (String line : notices) mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(line));
          notices.clear();
        }
      } else if (!module.friendAlerts()) {
        notices.clear();
      }
    } catch (Throwable error) {
      Cbui.reportOnce("luka online tick", error);
    }
  }

  private static String currentServer(Minecraft mc) {
    if (mc.theWorld == null || mc.isSingleplayer()) return null;
    net.minecraft.client.multiplayer.ServerData data = mc.getCurrentServerData();
    return data == null ? null : data.serverIP;
  }

  private static void scanPlayers(Minecraft mc) {
    if (mc.theWorld == null) return;
    long now = System.currentTimeMillis();
    List<String> found = new ArrayList<String>();
    if (mc.getNetHandler() != null) {
      Collection<NetworkPlayerInfo> infos = mc.getNetHandler().getPlayerInfoMap();
      for (NetworkPlayerInfo i : infos) if (i.getGameProfile() != null && i.getGameProfile().getId() != null) found.add(i.getGameProfile().getId().toString());
    }
    for (Object o : mc.theWorld.playerEntities) {
      EntityPlayer p = (EntityPlayer) o;
      if (p != mc.thePlayer) found.add(p.getUniqueID().toString());
    }
    for (String uuid : found) {
      Info cached = known.get(uuid);
      Long when = fetchedAt.get(uuid);
      long age = when == null ? Long.MAX_VALUE : now - when;
      if (cached == null || age > (cached == NONE ? MISSING_TTL : FOUND_TTL)) wanted.add(uuid);
    }
  }

  private static List<String> takeBatch(int max) {
    List<String> batch = new ArrayList<String>();
    synchronized (wanted) {
      java.util.Iterator<String> it = wanted.iterator();
      while (it.hasNext() && batch.size() < max) {
        batch.add(it.next());
        it.remove();
      }
    }
    return batch;
  }

  // ---- background work

  private static void signIn(String uuid, String name) {
    try {
      // Verified sign-in: the service hands out a server id and the game joins it at Mojang's session server, as it does
      // for an online-mode server; the service then asks Mojang whether this account joined it. The access token goes to
      // Mojang only, never to luka. A service without this step (the local stand-in) answers 404 and is used as before.
      String serverId = null;
      JsonObject start = new JsonObject();
      start.addProperty("uuid", uuid);
      start.addProperty("name", name);
      try {
        JsonObject s = call("POST", "/v1/session/start", start);
        if (s.has("serverId")) serverId = s.get("serverId").getAsString();
      } catch (IOException noVerification) {
        serverId = null;
      }
      if (serverId != null) {
        Minecraft mc = Minecraft.getMinecraft();
        try {
          mc.getSessionService().joinServer(mc.getSession().getProfile(), mc.getSession().getToken(), serverId);
        } catch (Exception notJoined) {
          // an offline account cannot join; the service then refuses the sign-in (a local test service may let it through)
        }
      }
      JsonObject body = new JsonObject();
      body.addProperty("uuid", uuid);
      body.addProperty("name", name);
      body.addProperty("version", Cbui.VERSION);
      if (serverId != null) body.addProperty("serverId", serverId);
      JsonObject reply = call("POST", "/v1/session", body);
      if (reply.has("heartbeatSeconds")) heartbeatMs = Math.max(15, Math.min(300, reply.get("heartbeatSeconds").getAsInt())) * 1000L;
      JsonObject user = reply.getAsJsonObject("user");
      self = new Info(str(user, "uuid"), str(user, "name"), str(user, "rank"), str(user, "cape"));
      try {
        selfUuid = UUID.fromString(self.uuid);
      } catch (IllegalArgumentException e) {
        selfUuid = null;
      }
      token = reply.get("token").getAsString();
      status = "Connected";
      backoff = 10_000L;
      nextHeartbeat = 0;
      nextFriends = 0;
      try {
        Ranks.update(call("GET", "/v1/ranks", null).getAsJsonArray("ranks"));
      } catch (Exception ignored) {
        // the built-in table is fine
      }
    } catch (Exception e) {
      status = "Offline";
      nextSession = System.currentTimeMillis() + backoff;
      backoff = Math.min(backoff * 2, 300_000L);
    } finally {
      signingIn = false;
    }
  }

  private static void heartbeat(String server) {
    try {
      JsonObject body = new JsonObject();
      if (server != null) body.addProperty("server", server);
      call("POST", "/v1/presence", body);
      nextHeartbeat = System.currentTimeMillis() + heartbeatMs;
      status = "Connected";
    } catch (Exception e) {
      nextHeartbeat = System.currentTimeMillis() + 15_000L;
    } finally {
      busyHeartbeat = false;
    }
  }

  private static void refreshFriends() {
    try {
      JsonObject reply = call("GET", "/v1/friends", null);
      FriendsData data = new FriendsData(people(reply, "friends"), people(reply, "incoming"), people(reply, "outgoing"));
      noticeChanges(data);
      friends = data;
      friendsLoaded = true;
    } catch (Exception ignored) {
      // shown again on the next refresh
    } finally {
      nextFriends = System.currentTimeMillis() + (fastFriends ? 5_000L : 60_000L);
      busyFriends = false;
    }
  }

  private static void noticeChanges(FriendsData data) {
    Set<String> online = new HashSet<String>();
    for (Person p : data.friends) if (p.online) online.add(p.uuid);
    if (friendsLoaded) {
      for (Person p : data.friends) {
        if (p.online && !lastOnline.contains(p.uuid)) {
          notices.add("§5[luka] §f" + p.name + " §7is online" + (p.server.isEmpty() ? "" : " on " + p.server));
        }
      }
      for (Person p : data.incoming) {
        boolean seen = false;
        for (Person old : friends.incoming) if (old.uuid.equals(p.uuid)) seen = true;
        if (!seen) notices.add("§5[luka] §f" + p.name + " §7sent you a friend request");
      }
    }
    lastOnline.clear();
    lastOnline.addAll(online);
  }

  private static void lookup(List<String> batch) {
    try {
      StringBuilder ids = new StringBuilder();
      for (String id : batch) ids.append(ids.length() == 0 ? "" : ",").append(id);
      JsonObject reply = call("GET", "/v1/users?uuids=" + URLEncoder.encode(ids.toString(), "UTF-8"), null);
      JsonObject users = reply.getAsJsonObject("users");
      long now = System.currentTimeMillis();
      for (String id : batch) {
        JsonElement e = users.get(id);
        if (e != null && e.isJsonObject()) {
          JsonObject o = e.getAsJsonObject();
          known.put(id, new Info(id, str(o, "name"), str(o, "rank"), str(o, "cape")));
        } else {
          known.put(id, NONE);
        }
        fetchedAt.put(id, now);
        try {
          Info found = known.get(id);
          byUuid.put(UUID.fromString(id), new Cached(found == NONE ? null : found, now));
        } catch (IllegalArgumentException ignored) {
          // not a UUID: only the slow path knows it
        }
      }
    } catch (Exception e) {
      synchronized (wanted) {
        wanted.addAll(batch);
      }
      nextLookup = System.currentTimeMillis() + 10_000L;
    } finally {
      busyLookup = false;
    }
  }

  private static List<Person> people(JsonObject reply, String key) {
    List<Person> out = new ArrayList<Person>();
    JsonArray array = reply.getAsJsonArray(key);
    if (array != null) for (JsonElement e : array) out.add(new Person(e.getAsJsonObject()));
    return out;
  }

  // ---- HTTP

  /** An error the service answered with (its message is meant to be shown). */
  static final class ApiException extends IOException {
    final int code;

    ApiException(int code, String message) {
      super(message);
      this.code = code;
    }
  }

  private static String api0() {
    String base = apiBase;
    if (base == null) {
      base = System.getProperty("luka.api");
      if (base == null || base.isEmpty()) base = System.getenv("LUKA_API");
      if (base == null || base.isEmpty()) base = fromFile();
      if (base == null || base.isEmpty()) base = DEFAULT_API;
      while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
      apiBase = base;
    }
    return base;
  }

  private static String fromFile() {
    try {
      File f = new File(Minecraft.getMinecraft().mcDataDir, "config/luka-network.json");
      if (!f.isFile()) return null;
      try (InputStreamReader in = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
        JsonObject o = new JsonParser().parse(in).getAsJsonObject();
        return o.has("api") ? o.get("api").getAsString().trim() : null;
      }
    } catch (Exception e) {
      return null;
    }
  }

  /** GET or POST JSON; returns the parsed reply object. */
  static JsonObject call(String method, String path, JsonObject body) throws IOException {
    HttpURLConnection c = (HttpURLConnection) new URL(api0() + path).openConnection();
    c.setConnectTimeout(4000);
    c.setReadTimeout(6000);
    c.setRequestMethod(method);
    c.setRequestProperty("Accept", "application/json");
    c.setRequestProperty("User-Agent", "luka/" + Cbui.VERSION);
    String t = token;
    if (t != null && !path.equals("/v1/session")) c.setRequestProperty("Authorization", "Bearer " + t);
    if (body != null) {
      byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
      c.setDoOutput(true);
      c.setRequestProperty("Content-Type", "application/json");
      c.setFixedLengthStreamingMode(data.length);
      try (OutputStream out = c.getOutputStream()) { out.write(data); }
    }
    int code = c.getResponseCode();
    InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
    String text = in == null ? "" : new String(readAll(in), StandardCharsets.UTF_8);
    JsonObject o;
    try {
      o = text.isEmpty() ? new JsonObject() : new JsonParser().parse(text).getAsJsonObject();
    } catch (RuntimeException e) {
      throw new IOException("bad reply from luka online");
    }
    if (code == 401 && !path.equals("/v1/session")) {
      token = null; // sign in again
      status = "Signing in";
      nextSession = 0;
    }
    if (code >= 400) throw new ApiException(code, o.has("error") ? o.get("error").getAsString() : "Error " + code);
    return o;
  }

  /** GET raw bytes (cape files). */
  public static byte[] fetch(String path) throws IOException {
    HttpURLConnection c = (HttpURLConnection) new URL(api0() + path).openConnection();
    c.setConnectTimeout(4000);
    c.setReadTimeout(8000);
    c.setRequestProperty("User-Agent", "luka/" + Cbui.VERSION);
    if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
    try (InputStream in = c.getInputStream()) { return readAll(in); }
  }

  private static byte[] readAll(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int n;
    long total = 0;
    while ((n = in.read(buffer)) > 0) {
      total += n;
      if (total > 4_000_000L) throw new IOException("reply too large");
      out.write(buffer, 0, n);
    }
    return out.toByteArray();
  }

  private static String str(JsonObject o, String key) {
    JsonElement e = o.get(key);
    return e == null || e.isJsonNull() ? "" : e.getAsString();
  }

  /** For tests and tools: forget everything and sign in again. */
  public static void reset() {
    token = null;
    self = null;
    known.clear();
    fetchedAt.clear();
    byUuid.clear();
    selfUuid = null;
    wanted.clear();
    nextSession = 0;
    friendsLoaded = false;
    capesLoaded = false;
    capeCatalog = Collections.emptyList();
    capeSelected = "";
    synchronized (conversations) {
      conversations.clear();
      unreadCount.clear();
    }
    lastMessageId = 0;
    messagesLoaded = false;
    nextMessages = 0;
  }

  /** Used by the test bot: a readable summary of the state. */
  public static String debug() {
    Info me = self;
    return "status=" + status + " api=" + api0() + " self=" + (me == null ? "-" : me.name + "/" + me.rank + "/" + me.cape)
        + " known=" + known.size() + " friends=" + friends.friends.size() + " incoming=" + friends.incoming.size() + " luka=" + lukaPlayers();
  }

  /** The luka players seen around this player, name=rank, for the test bot. */
  private static String lukaPlayers() {
    StringBuilder out = new StringBuilder("[");
    for (Info i : known.values()) if (i != null && i != NONE && i.name != null) out.append(i.name).append('=').append(i.rank).append(' ');
    return out.toString().trim() + "]";
  }
}
