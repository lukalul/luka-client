package dev.cbui.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The Discord app's local connection (the one Rich Presence uses): a named pipe on Windows, a Unix socket on Linux. Messages
 * are frames of opcode (int, little endian), length (int, little endian) and JSON. Nothing here touches Minecraft, and
 * nothing leaves the computer: the Discord app shows the status itself. Not thread safe: one thread uses it.
 */
public final class DiscordIpc implements AutoCloseable {
  private static final int HANDSHAKE = 0, FRAME = 1, CLOSE = 2;

  private final AutoCloseable handle;
  private final DataInputStream in;
  private final OutputStream out;

  private DiscordIpc(AutoCloseable handle, InputStream in, OutputStream out) {
    this.handle = handle;
    this.in = new DataInputStream(in);
    this.out = out;
  }

  /** Connects and says hello with the application id; null when Discord is not running (or not reachable). */
  public static DiscordIpc open(String appId) {
    for (int i = 0; i < 10; i++) {
      DiscordIpc ipc = null;
      try {
        ipc = connect(i);
        if (ipc == null) continue;
        JsonObject hello = new JsonObject();
        hello.addProperty("v", 1);
        hello.addProperty("client_id", appId);
        ipc.send(HANDSHAKE, hello);
        JsonObject ready = ipc.read();
        if (ready != null && "READY".equals(str(ready, "evt"))) return ipc;
        ipc.close();
      } catch (Exception e) {
        if (ipc != null) ipc.close();
      }
    }
    return null;
  }

  /** The pipe or socket name before the number; tests point it at a stand-in so they never touch the real Discord. */
  private static final String NAME = System.getProperty("luka.discordIpc", "discord-ipc-");

  private static DiscordIpc connect(int index) throws Exception {
    String os = System.getProperty("os.name", "").toLowerCase();
    if (os.contains("win")) {
      File pipe = new File("\\\\.\\pipe\\" + NAME + index);
      final RandomAccessFile file;
      try {
        file = new RandomAccessFile(pipe, "rw");
      } catch (IOException none) {
        return null;
      }
      InputStream in = new InputStream() {
        @Override public int read() throws IOException { return file.read(); }
        @Override public int read(byte[] b, int off, int len) throws IOException { return file.read(b, off, len); }
      };
      OutputStream out = new OutputStream() {
        @Override public void write(int b) throws IOException { file.write(b); }
        @Override public void write(byte[] b, int off, int len) throws IOException { file.write(b, off, len); }
      };
      return new DiscordIpc(file, in, out);
    }
    // Linux: a Unix socket in the runtime or temp folder, also inside Flatpak and Snap installs of Discord. Unix sockets
    // need Java 16 or newer; they are reached by reflection so the mod still loads on older Java (then there is no status).
    String[] roots = {System.getenv("XDG_RUNTIME_DIR"), System.getenv("TMPDIR"), System.getenv("TMP"), System.getenv("TEMP"), "/tmp"};
    String[] subs = {"", "app/com.discordapp.Discord/", "snap.discord/", "app/dev.vencord.Vesktop/"};
    for (String root : roots) {
      if (root == null || root.isEmpty()) continue;
      for (String sub : subs) {
        File socket = new File(root, sub + NAME + index);
        if (!socket.exists()) continue;
        Object address = Class.forName("java.net.UnixDomainSocketAddress").getMethod("of", String.class).invoke(null, socket.getPath());
        Object family = java.net.StandardProtocolFamily.class.getMethod("valueOf", String.class).invoke(null, "UNIX");
        java.nio.channels.SocketChannel channel = (java.nio.channels.SocketChannel) java.nio.channels.SocketChannel.class
            .getMethod("open", java.net.ProtocolFamily.class).invoke(null, family);
        try {
          channel.connect((java.net.SocketAddress) address);
        } catch (IOException refused) {
          channel.close();
          continue;
        }
        return new DiscordIpc(channel, Channels.newInputStream(channel), Channels.newOutputStream(channel));
      }
    }
    return null;
  }

  /** Shows this activity (null clears it). Throws when the connection is gone. */
  public void setActivity(JsonObject activity, long pid) throws IOException {
    JsonObject args = new JsonObject();
    args.addProperty("pid", pid);
    if (activity != null) args.add("activity", activity);
    JsonObject frame = new JsonObject();
    frame.addProperty("cmd", "SET_ACTIVITY");
    frame.add("args", args);
    frame.addProperty("nonce", UUID.randomUUID().toString());
    send(FRAME, frame);
    JsonObject reply = read();
    if (reply == null) throw new IOException("Discord closed the connection");
  }

  private void send(int op, JsonObject body) throws IOException {
    byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
    ByteBuffer frame = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
    frame.putInt(op).putInt(data.length).put(data);
    out.write(frame.array());
    out.flush();
  }

  /** The next frame's JSON, or null when Discord said goodbye. */
  private JsonObject read() throws IOException {
    byte[] head = new byte[8];
    in.readFully(head);
    ByteBuffer h = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
    int op = h.getInt();
    int length = h.getInt();
    if (length < 0 || length > 1 << 20) throw new IOException("bad frame from Discord");
    byte[] data = new byte[length];
    in.readFully(data);
    if (op == CLOSE) return null;
    return new JsonParser().parse(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
  }

  private static String str(JsonObject o, String key) {
    return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
  }

  @Override public void close() {
    try { handle.close(); } catch (Exception ignored) { }
  }

  /** This process's id (Discord uses it to tie the status to the game). */
  public static long pid() {
    try {
      Class<?> type = Class.forName("java.lang.ProcessHandle");
      Object handle = type.getMethod("current").invoke(null);
      return (Long) type.getMethod("pid").invoke(handle);
    } catch (Throwable java8) {
      String name = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
      try { return Long.parseLong(name.substring(0, name.indexOf('@'))); } catch (RuntimeException e) { return 0; }
    }
  }
}
