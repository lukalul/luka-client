package dev.luka.launcher;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Talks to the Discord app on this PC over its local RPC connection (the one Discord's own Game SDK uses): the named
 * pipe {@code \\.\pipe\discord-ipc-N} on Windows, the socket {@code discord-ipc-N} in the runtime/temp folder on Linux
 * and macOS (also the Flatpak and Snap places). Nothing here goes over the internet; Discord shows the activity to the
 * player's friends. The READY answer names the signed-in Discord user ({@link #user}).
 *
 * Frames: little-endian int opcode, int length, then JSON. Opcodes: 0 handshake, 1 frame, 2 close, 3 ping, 4 pong.
 *
 * Windows pipes are opened synchronously by RandomAccessFile, so a blocking read would also block writes: there,
 * {@link #pump()} only reads when the pipe has data ({@code length()} reports the bytes waiting). Unix sockets are
 * full duplex: a reader thread answers pings and notices when Discord goes away.
 */
final class DiscordIpc implements Closeable {
  /** The signed-in Discord user, from READY. */
  static final class User {
    final String id, username, globalName;

    User(String id, String username, String globalName) {
      this.id = id;
      this.username = username;
      this.globalName = globalName;
    }
  }

  private final InputStream in;
  private final OutputStream out;
  private final Closeable handle;
  private final RandomAccessFile pipe; // Windows only
  private volatile boolean alive = true;
  /** Discord's last ERROR reply (a rejected activity), or null. */
  volatile String lastError;
  final User user;

  private DiscordIpc(InputStream in, OutputStream out, Closeable handle, RandomAccessFile pipe, User user) {
    this.in = in;
    this.out = out;
    this.handle = handle;
    this.pipe = pipe;
    this.user = user;
  }

  /** Connects and says hello as the given Discord application; null when Discord is not running. */
  static DiscordIpc connect(String clientId) {
    for (int i = 0; i < 10; i++) {
      Opened o = open(i);
      if (o == null) continue;
      try {
        write(o.out, 0, "{\"v\":1,\"client_id\":" + quote(clientId) + "}");
        for (int tries = 0; tries < 6; tries++) {
          Frame f = read(o.in);
          if (f.op == 2) throw new IOException("Discord closed the connection: " + f.json);
          if (f.op == 3) {
            write(o.out, 4, f.json);
            continue;
          }
          if ("READY".equals(str(f.json, "evt"))) {
            String userJson = object(f.json, "user");
            User user = userJson == null ? null
                : new User(str(userJson, "id"), str(userJson, "username"), str(userJson, "global_name"));
            DiscordIpc ipc = new DiscordIpc(o.in, o.out, o.handle, o.pipe, user);
            if (o.pipe == null) ipc.startReader();
            return ipc;
          }
        }
        throw new IOException("no READY from Discord");
      } catch (IOException e) {
        closeQuietly(o.handle);
      }
    }
    return null;
  }

  boolean alive() {
    return alive;
  }

  /** Shows an activity (activityJson is the "activity" object), or clears it when null. */
  synchronized void setActivity(String activityJson) throws IOException {
    String body = "{\"cmd\":\"SET_ACTIVITY\",\"args\":{\"pid\":" + pid() + ",\"activity\":"
        + (activityJson == null ? "null" : activityJson) + "},\"nonce\":\"" + UUID.randomUUID() + "\"}";
    try {
      write(out, 1, body);
    } catch (IOException e) {
      alive = false;
      throw e;
    }
  }

  /** Windows: reads and answers what Discord sent (pings, command replies). Throws when the connection is gone. */
  void pump() throws IOException {
    if (!alive) throw new EOFException("Discord went away");
    if (pipe == null) return; // the reader thread does it
    try {
      while (pipe.length() >= 8) {
        Frame f = read(in);
        if (f.op == 2) throw new EOFException("Discord closed the connection");
        if (f.op == 3) synchronized (this) {
          write(out, 4, f.json);
        }
        noteError(f);
      }
    } catch (IOException e) {
      alive = false;
      throw e;
    }
  }

  private void noteError(Frame f) {
    if (f.op == 1 && "ERROR".equals(str(f.json, "evt"))) {
      String data = object(f.json, "data");
      lastError = data == null ? f.json : data;
    }
  }

  private void startReader() {
    Thread t = new Thread(() -> {
      try {
        while (alive) {
          Frame f = read(in);
          if (f.op == 2) break;
          if (f.op == 3) synchronized (this) {
            write(out, 4, f.json);
          }
          noteError(f);
        }
      } catch (IOException ignored) {
        // Discord went away
      }
      alive = false;
    }, "luka-discord-reader");
    t.setDaemon(true);
    t.start();
  }

  @Override public void close() {
    if (alive) {
      try {
        synchronized (this) {
          write(out, 2, "{}");
        }
      } catch (IOException ignored) {
        // already gone
      }
    }
    alive = false;
    closeQuietly(handle);
  }

  // ------------------------------------------------------------------ transport

  private static final class Opened {
    final InputStream in;
    final OutputStream out;
    final Closeable handle;
    final RandomAccessFile pipe;

    Opened(InputStream in, OutputStream out, Closeable handle, RandomAccessFile pipe) {
      this.in = in;
      this.out = out;
      this.handle = handle;
      this.pipe = pipe;
    }
  }

  private static Opened open(int n) {
    String name = "discord-ipc-" + n;
    if (Platform.windows()) {
      try {
        final RandomAccessFile pipe = new RandomAccessFile("\\\\.\\pipe\\" + name, "rw");
        InputStream in = new InputStream() {
          @Override public int read() throws IOException {
            return pipe.read();
          }

          @Override public int read(byte[] b, int off, int len) throws IOException {
            return pipe.read(b, off, len);
          }
        };
        OutputStream out = new OutputStream() {
          @Override public void write(int b) throws IOException {
            pipe.write(b);
          }

          @Override public void write(byte[] b, int off, int len) throws IOException {
            pipe.write(b, off, len);
          }
        };
        return new Opened(in, out, pipe, pipe);
      } catch (IOException e) {
        return null;
      }
    }
    for (String dir : unixDirs()) {
      File socket = new File(dir, name);
      if (!socket.exists()) continue;
      try {
        // Unix sockets need Java 16+ (the launcher runs on its own newer Java); compiled for 8, so by reflection
        Object address = Class.forName("java.net.UnixDomainSocketAddress").getMethod("of", String.class)
            .invoke(null, socket.getAbsolutePath());
        Class<?> family = Class.forName("java.net.StandardProtocolFamily");
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object unix = Enum.valueOf((Class) family, "UNIX");
        SocketChannel ch = (SocketChannel) SocketChannel.class
            .getMethod("open", Class.forName("java.net.ProtocolFamily")).invoke(null, unix);
        ch.connect((java.net.SocketAddress) address);
        return new Opened(Channels.newInputStream(ch), Channels.newOutputStream(ch), ch, null);
      } catch (Throwable e) {
        // try the next place
      }
    }
    return null;
  }

  private static String[] unixDirs() {
    String base = null;
    for (String env : new String[] {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
      String v = System.getenv(env);
      if (v != null && !v.isEmpty()) {
        base = v;
        break;
      }
    }
    if (base == null) base = "/tmp";
    return new String[] {base, base + "/app/com.discordapp.Discord", base + "/app/com.discordapp.DiscordCanary",
        base + "/snap.discord", base + "/.flatpak/dev.vencord.Vesktop/xdg-run", "/tmp"};
  }

  private static final class Frame {
    final int op;
    final String json;

    Frame(int op, String json) {
      this.op = op;
      this.json = json;
    }
  }

  private static void write(OutputStream out, int op, String json) throws IOException {
    byte[] body = json.getBytes(StandardCharsets.UTF_8);
    ByteBuffer frame = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
    frame.putInt(op).putInt(body.length).put(body);
    out.write(frame.array());
    out.flush();
  }

  private static Frame read(InputStream in) throws IOException {
    ByteBuffer b = ByteBuffer.wrap(readFully(in, 8)).order(ByteOrder.LITTLE_ENDIAN);
    int op = b.getInt();
    int len = b.getInt();
    if (len < 0 || len > 1 << 20) throw new IOException("bad frame length " + len);
    return new Frame(op, new String(readFully(in, len), StandardCharsets.UTF_8));
  }

  private static byte[] readFully(InputStream in, int n) throws IOException {
    byte[] buf = new byte[n];
    int got = 0;
    while (got < n) {
      int r = in.read(buf, got, n - got);
      if (r < 0) throw new EOFException();
      got += r;
    }
    return buf;
  }

  private static long pid() {
    try {
      Object handle = Class.forName("java.lang.ProcessHandle").getMethod("current").invoke(null);
      return (Long) handle.getClass().getMethod("pid").invoke(handle);
    } catch (Throwable e) {
      String name = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
      try {
        return Long.parseLong(name.substring(0, name.indexOf('@')));
      } catch (RuntimeException ignored) {
        return 0;
      }
    }
  }

  private static void closeQuietly(Closeable c) {
    try {
      if (c != null) c.close();
    } catch (IOException ignored) {
      // closing
    }
  }

  // ------------------------------------------------------------------ tiny JSON helpers (Discord's replies only)

  /** The first "key":"value" string in json (null when missing or null). */
  static String str(String json, String key) {
    Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(null|\"((?:[^\"\\\\]|\\\\.)*)\")").matcher(json);
    if (!m.find() || m.group(2) == null) return null;
    return unescape(m.group(2));
  }

  /** The first "key":{...} object in json, braces balanced (strings respected). */
  static String object(String json, String key) {
    Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\{").matcher(json);
    if (!m.find()) return null;
    int start = m.end() - 1, depth = 0;
    boolean inString = false;
    for (int i = start; i < json.length(); i++) {
      char c = json.charAt(i);
      if (inString) {
        if (c == '\\') i++;
        else if (c == '"') inString = false;
      } else if (c == '"') {
        inString = true;
      } else if (c == '{') {
        depth++;
      } else if (c == '}' && --depth == 0) {
        return json.substring(start, i + 1);
      }
    }
    return null;
  }

  static String quote(String s) {
    StringBuilder b = new StringBuilder("\"");
    for (char c : s.toCharArray()) {
      if (c == '"' || c == '\\') b.append('\\').append(c);
      else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
      else b.append(c);
    }
    return b.append('"').toString();
  }

  private static String unescape(String s) {
    StringBuilder b = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c != '\\' || i + 1 >= s.length()) {
        b.append(c);
        continue;
      }
      char n = s.charAt(++i);
      switch (n) {
        case 'n': b.append('\n'); break;
        case 't': b.append('\t'); break;
        case 'r': b.append('\r'); break;
        case 'b': b.append('\b'); break;
        case 'f': b.append('\f'); break;
        case 'u':
          if (i + 4 < s.length()) {
            b.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
            i += 4;
          }
          break;
        default: b.append(n);
      }
    }
    return b.toString();
  }
}
