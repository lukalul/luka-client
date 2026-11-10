package dev.cbui;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import net.minecraft.client.Minecraft;

/**
 * Event log for stalled frames: every frame that takes much longer than the usual one gets an entry in
 * {@code logs/luka-stalls.log} saying how long it took, which parts of the game loop the time went to (the game's own
 * profiler sections), which garbage collections ran during it and what the render thread was doing while it was stuck
 * (stack traces taken by a second thread during the stall, not afterwards).
 * A high frame rate says nothing about these frames; this log is where they show.
 */
public final class StallLog {
  /** On unless the game was started with -Dluka.stallLog=false. */
  public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("luka.stallLog"));
  /** A frame is a stall when it takes longer than this and longer than {@link #SLOWER} times the usual frame. */
  public static volatile double thresholdMs = number("luka.stallMs", 4.0);
  /** Set to true to start the counts in {@link #summary} again. */
  public static volatile boolean resetNow;
  /** Stalls since the log was started or reset, by where the time went. Rewritten after every logged stall. */
  public static volatile String summary = "stalls=0";

  private static final double SLOWER = 2.0;
  /** Sections nested deeper than this are not timed, only counted in and out. */
  private static final int MAX_DEPTH = 5;
  private static final int MAX_KIDS = 24;
  private static final int MAX_SAMPLES = 20;
  /** More stalls than this in one second are counted but not written out one by one. */
  private static final int MAX_PER_SECOND = 20;
  private static final long MAX_FILE = 2L * 1024 * 1024;
  /** An entry waits this long before it is written, so collections that report late still land in it. */
  private static final long WRITE_DELAY = 200_000_000L;

  private static final class Node {
    final String name;
    final Node parent;
    Node[] kids = new Node[4];
    int kidCount;
    long nanos, opened;
    int count;
    boolean touched;

    Node(String name, Node parent) {
      this.name = name;
      this.parent = parent;
    }

    Node child(String wanted) {
      for (int i = 0; i < kidCount; i++) if (kids[i].name == wanted) return kids[i];
      for (int i = 0; i < kidCount; i++) if (kids[i].name.equals(wanted)) return kids[i];
      if (kidCount >= MAX_KIDS) return kids[MAX_KIDS - 1];
      if (kidCount == kids.length) kids = Arrays.copyOf(kids, kidCount * 2);
      return kids[kidCount++] = new Node(kidCount == MAX_KIDS ? "other" : wanted, this);
    }
  }

  private static final class Entry {
    long queued, wallStart, wallEnd, took, usual;
    int ticks, skipped;
    String sections, cause, screen, note;
    boolean inWorld;
    StackTraceElement[][] stacks;
  }

  private static final class Gc {
    long start, end, duration;
    /** A collector that works beside the game; what it reports is how long a round took, not how long the game stood still. */
    boolean beside;
    String text;
  }

  private static final class Tally {
    int count;
    long total, worst;
  }

  // render thread only
  private static boolean live;
  private static final Node root = new Node("frame", null);
  private static Node current = root;
  private static int depth;
  private static Node[] touched = new Node[64];
  private static int touchedCount;
  private static final long[] recent = new long[64];
  private static long frames;
  private static int ticks;
  private static Object lastWorld;
  private static long second;
  private static int inSecond, skipped;

  // shared with the watcher thread
  private static volatile Thread thread;
  private static volatile long frameStart, frameId;
  private static volatile int generation;
  private static volatile long trigger = 4_000_000L;
  private static final Object sampleLock = new Object();
  private static final List<StackTraceElement[]> samples = new ArrayList<StackTraceElement[]>();
  private static long sampleFrame = -1;
  private static final ConcurrentLinkedQueue<Entry> queue = new ConcurrentLinkedQueue<Entry>();
  private static final ArrayDeque<Gc> collections = new ArrayDeque<Gc>();
  private static boolean listening;

  // watcher thread only
  private static BufferedWriter out;
  private static long written;
  private static final Map<String, Tally> tallies = new LinkedHashMap<String, Tally>();
  private static int stallCount;
  private static long stallTotal, stallWorst, countingSince;

  private StallLog() {}

  /** Start of every frame, on the render thread. */
  public static void frame() {
    if (!enabled) {
      if (live) live = false;
      return;
    }
    try {
      long now = System.nanoTime();
      if (!live) {
        start(now);
        return;
      }
      long took = now - frameStart;
      recent[(int) (frames & 63)] = took;
      frames++;
      if ((frames & 15) == 0) retune();
      Object world = Minecraft.getMinecraft().theWorld;
      if (world != lastWorld) {
        note(now, lastWorld == null ? "joined a world" : world == null ? "left the world" : "changed world");
        lastWorld = world;
      }
      if (took > trigger && frames > 64) stalled(now, took);
      for (int i = 0; i < touchedCount; i++) {
        Node node = touched[i];
        node.nanos = 0;
        node.count = 0;
        node.touched = false;
      }
      touchedCount = 0;
      current = root;
      depth = 0;
      ticks = 0;
      frameStart = now;
      frameId++;
    } catch (Throwable error) {
      enabled = false;
      live = false;
      Cbui.reportOnce("stall log", error);
    }
  }

  /** Start of every client tick. */
  public static void tick() {
    ticks++;
  }

  /** A section of the game loop opens. Called for every section of the game's own profiler, whether that one is recording or not. */
  public static void begin(String name) {
    if (!live || Thread.currentThread() != thread) return;
    if (depth >= MAX_DEPTH) {
      depth++;
      return;
    }
    Node node = current.child(name);
    node.opened = System.nanoTime();
    current = node;
    depth++;
  }

  /** The innermost open section closes. The game does not pair its sections exactly, so one too many is ignored. */
  public static void end() {
    if (!live || Thread.currentThread() != thread) return;
    if (depth > MAX_DEPTH) {
      depth--;
      return;
    }
    if (depth == 0) return;
    Node node = current;
    node.nanos += System.nanoTime() - node.opened;
    node.count++;
    if (!node.touched) {
      node.touched = true;
      if (touchedCount == touched.length) touched = Arrays.copyOf(touched, touchedCount * 2);
      touched[touchedCount++] = node;
    }
    current = node.parent;
    depth--;
  }

  private static void start(long now) {
    thread = Thread.currentThread();
    frames = 0;
    current = root;
    depth = 0;
    touchedCount = 0;
    frameStart = now;
    frameId++;
    lastWorld = Minecraft.getMinecraft().theWorld;
    resetNow = true;
    live = true;
    listen();
    final int mine = ++generation;
    Thread watcher = new Thread(new Runnable() {
      @Override public void run() {
        watch(mine);
      }
    }, "luka stall log");
    watcher.setDaemon(true);
    watcher.setPriority(Thread.MAX_PRIORITY);
    watcher.start();
  }

  /** The usual frame is the median of the last 64; menus and frame limits move it, so the trigger follows. */
  private static void retune() {
    long[] sorted = recent.clone();
    Arrays.sort(sorted);
    long usual = sorted[sorted.length / 2];
    trigger = Math.max((long) (thresholdMs * 1_000_000), (long) (usual * SLOWER));
  }

  private static long usual() {
    long[] sorted = recent.clone();
    Arrays.sort(sorted);
    return sorted[sorted.length / 2];
  }

  private static void note(long now, String text) {
    Entry entry = new Entry();
    entry.queued = now;
    entry.wallEnd = System.currentTimeMillis();
    entry.note = text;
    queue.add(entry);
  }

  private static void stalled(long now, long took) {
    long wall = System.currentTimeMillis();
    if (wall / 1000 != second) {
      second = wall / 1000;
      inSecond = 0;
    }
    if (++inSecond > MAX_PER_SECOND) {
      skipped++;
      return;
    }
    Entry entry = new Entry();
    entry.queued = now;
    entry.wallEnd = wall;
    entry.wallStart = wall - took / 1_000_000;
    entry.took = took;
    entry.usual = usual();
    entry.ticks = ticks;
    entry.skipped = skipped;
    skipped = 0;
    long floor = Math.max(300_000L, took / 12);
    StringBuilder text = new StringBuilder();
    describe(root, floor, text);
    entry.sections = text.length() == 0 ? "none above " + ms(floor) + " ms" : text.toString();
    entry.cause = cause(took);
    Minecraft mc = Minecraft.getMinecraft();
    entry.inWorld = mc.theWorld != null;
    entry.screen = mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName();
    synchronized (sampleLock) {
      if (sampleFrame == frameId && !samples.isEmpty()) entry.stacks = samples.toArray(new StackTraceElement[0][]);
      samples.clear();
    }
    queue.add(entry);
  }

  /** The sections that took at least {@code floor}, heaviest first, each followed by its own heavy parts. */
  private static void describe(Node node, long floor, StringBuilder text) {
    Node[] heavy = new Node[node.kidCount];
    int n = 0;
    for (int i = 0; i < node.kidCount; i++) if (node.kids[i].nanos >= floor) heavy[n++] = node.kids[i];
    for (int i = 1; i < n; i++) {
      Node moving = heavy[i];
      int j = i - 1;
      while (j >= 0 && heavy[j].nanos < moving.nanos) {
        heavy[j + 1] = heavy[j];
        j--;
      }
      heavy[j + 1] = moving;
    }
    for (int i = 0; i < n; i++) {
      if (i > 0) text.append(", ");
      text.append(heavy[i].name).append(' ').append(ms(heavy[i].nanos));
      if (heavy[i].count > 1) text.append(" (").append(heavy[i].count).append("x)");
      StringBuilder inner = new StringBuilder();
      describe(heavy[i], floor, inner);
      if (inner.length() > 0) {
        boolean several = inner.indexOf(", ") >= 0;
        text.append(" > ").append(several ? "(" : "").append(inner).append(several ? ")" : "");
      }
    }
  }

  /** The chain of sections that each hold at least half of the frame, as one name for the tally. */
  private static String cause(long took) {
    StringBuilder path = new StringBuilder();
    Node node = root;
    while (true) {
      Node heaviest = null;
      for (int i = 0; i < node.kidCount; i++) if (heaviest == null || node.kids[i].nanos > heaviest.nanos) heaviest = node.kids[i];
      if (heaviest == null || heaviest.nanos * 2 < took) break;
      if (!(node == root && "root".equals(heaviest.name))) {
        if (path.length() > 0) path.append('>');
        path.append(heaviest.name);
      }
      node = heaviest;
    }
    return path.length() == 0 ? null : path.toString();
  }

  /** The innermost frame of a stack that is the game's or a mod's own code, not Java's or the graphics library's. */
  private static String gameFrame(StackTraceElement[] trace) {
    for (StackTraceElement frame : trace) {
      String owner = frame.getClassName();
      if (owner.startsWith("java.") || owner.startsWith("sun.") || owner.startsWith("jdk.") || owner.startsWith("org.lwjgl.")) continue;
      return owner.substring(owner.lastIndexOf('.') + 1) + "." + frame.getMethodName();
    }
    return trace.length == 0 ? "nothing" : trace[0].getClassName() + "." + trace[0].getMethodName();
  }

  private static void listen() {
    if (listening) return;
    listening = true;
    try {
      final long jvmStart = ManagementFactory.getRuntimeMXBean().getStartTime();
      NotificationListener listener = new NotificationListener() {
        @Override public void handleNotification(Notification notification, Object handback) {
          try {
            if (!"com.sun.management.gc.notification".equals(notification.getType())) return;
            CompositeData data = (CompositeData) notification.getUserData();
            CompositeData info = (CompositeData) data.get("gcInfo");
            Gc c = new Gc();
            c.start = jvmStart + ((Number) info.get("startTime")).longValue();
            c.end = jvmStart + ((Number) info.get("endTime")).longValue();
            c.duration = ((Number) info.get("duration")).longValue();
            String name = String.valueOf(data.get("gcName"));
            c.beside = name.contains("Cycles");
            c.text = name + " " + c.duration + " ms" + (c.beside ? " beside the game" : "") + " (" + data.get("gcCause") + ")";
            synchronized (collections) {
              collections.addLast(c);
              while (collections.size() > 256) collections.removeFirst();
            }
          } catch (Throwable ignored) {
            // a collector that reports differently: its collections are just not listed
          }
        }
      };
      for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans())
        if (bean instanceof NotificationEmitter) ((NotificationEmitter) bean).addNotificationListener(listener, null, null);
    } catch (Throwable ignored) {
      // no collection reports on this Java
    }
  }

  /** Second thread: takes the render thread's stack while a frame is overdue, and writes the entries out. */
  private static void watch(int mine) {
    long seen = -1, nextSample = 0;
    int taken = 0;
    try {
      while (live && enabled && generation == mine) {
        long id = frameId, start = frameStart, now = System.nanoTime();
        if (id != seen) {
          seen = id;
          taken = 0;
          nextSample = start + trigger * 3 / 4;
        }
        if (now >= nextSample && taken < MAX_SAMPLES) {
          StackTraceElement[] trace = thread.getStackTrace();
          if (frameId == id && trace.length > 0) {
            synchronized (sampleLock) {
              if (sampleFrame != id) {
                sampleFrame = id;
                samples.clear();
              }
              samples.add(trace);
            }
          }
          taken++;
          nextSample = System.nanoTime() + (taken < 4 ? 1_000_000L : taken < 12 ? 4_000_000L : 50_000_000L);
          continue;
        }
        drain(now);
        long wait = taken >= MAX_SAMPLES ? 5_000_000L : Math.min(nextSample - now, 5_000_000L);
        LockSupport.parkNanos(Math.max(wait, 100_000L));
      }
    } catch (Throwable error) {
      enabled = false;
      Cbui.reportOnce("stall log writer", error);
    } finally {
      if (generation == mine) {
        try {
          if (out != null) out.close();
        } catch (Throwable ignored) {
          // nothing left to do with the file
        }
        out = null;
      }
    }
  }

  private static void drain(long now) throws Exception {
    if (resetNow) {
      resetNow = false;
      tallies.clear();
      stallCount = 0;
      stallTotal = stallWorst = 0;
      countingSince = now;
      summary = "stalls=0";
    }
    boolean wrote = false;
    while (true) {
      Entry entry = queue.peek();
      if (entry == null || now - entry.queued < WRITE_DELAY) break;
      queue.poll();
      write(entry);
      wrote = true;
    }
    if (wrote) {
      if (out != null) out.flush();
      summary = summarise(now);
    }
  }

  private static void write(Entry entry) throws Exception {
    if (out == null) open();
    SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss.SSS");
    StringBuilder text = new StringBuilder();
    if (entry.note != null) {
      text.append(clock.format(new Date(entry.wallEnd))).append(" EVENT ").append(entry.note).append('\n');
    } else {
      String gc = collectionsDuring(entry);
      text.append(clock.format(new Date(entry.wallStart))).append(" STALL ").append(ms(entry.took)).append(" ms (usual ").append(ms(entry.usual))
          .append(") ").append(entry.inWorld ? "in world" : "no world").append(", screen ").append(entry.screen)
          .append(", ticks ").append(entry.ticks).append('\n');
      if (entry.skipped > 0) text.append("    ").append(entry.skipped).append(" stalls before this one were not written (more than ").append(MAX_PER_SECOND).append(" a second)\n");
      text.append("    sections: ").append(entry.sections).append('\n');
      if (gc != null) text.append("    gc: ").append(gc).append('\n');
      stacks(entry.stacks, text);
      String cause = entry.cause;
      if (cause == null) cause = entry.stacks == null ? "outside the sections" : "at " + gameFrame(entry.stacks[0]);
      if (gc != null && gcMillis(entry) * 1_000_000L * 2 >= entry.took) cause = "gc";
      Tally tally = tallies.get(cause);
      if (tally == null) tallies.put(cause, tally = new Tally());
      tally.count++;
      tally.total += entry.took;
      tally.worst = Math.max(tally.worst, entry.took);
      stallCount += 1 + entry.skipped;
      stallTotal += entry.took;
      stallWorst = Math.max(stallWorst, entry.took);
    }
    out.write(text.toString());
    written += text.length();
    if (written > MAX_FILE) {
      out.close();
      out = null;
    }
  }

  private static String collectionsDuring(Entry entry) {
    StringBuilder text = null;
    synchronized (collections) {
      for (Gc c : collections) {
        if (c.end < entry.wallStart - 2 || c.start > entry.wallEnd + 2) continue;
        if (text == null) text = new StringBuilder();
        else text.append(", ");
        text.append(c.text);
      }
    }
    return text == null ? null : text.toString();
  }

  private static long gcMillis(Entry entry) {
    long sum = 0;
    synchronized (collections) {
      for (Gc c : collections) if (!c.beside && c.end >= entry.wallStart - 2 && c.start <= entry.wallEnd + 2) sum += c.duration;
    }
    return sum;
  }

  /** The stacks taken during one stall, the same ones counted together, most common first. */
  private static void stacks(StackTraceElement[][] taken, StringBuilder text) {
    if (taken == null) {
      text.append("    stack: none taken (the frame ended before the first sample)\n");
      return;
    }
    Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
    for (StackTraceElement[] trace : taken) {
      StringBuilder line = new StringBuilder();
      for (int i = 0; i < trace.length && i < 10; i++) {
        if (i > 0) line.append(" < ");
        line.append(trace[i].getClassName()).append('.').append(trace[i].getMethodName());
        if (trace[i].getLineNumber() > 0) line.append(':').append(trace[i].getLineNumber());
      }
      String key = line.toString();
      Integer old = counts.get(key);
      counts.put(key, old == null ? 1 : old + 1);
    }
    for (int shown = 0; shown < 3 && !counts.isEmpty(); shown++) {
      String best = null;
      for (Map.Entry<String, Integer> e : counts.entrySet()) if (best == null || e.getValue() > counts.get(best)) best = e.getKey();
      text.append("    stack ").append(counts.remove(best)).append(" of ").append(taken.length).append(": ").append(best).append('\n');
    }
  }

  private static String summarise(long now) {
    StringBuilder text = new StringBuilder();
    text.append("stalls=").append(stallCount).append(" seconds=").append((now - countingSince) / 1_000_000_000L)
        .append(" stalled_ms=").append(ms(stallTotal)).append(" worst_ms=").append(ms(stallWorst));
    List<Map.Entry<String, Tally>> order = new ArrayList<Map.Entry<String, Tally>>(tallies.entrySet());
    for (int shown = 0; shown < 6 && !order.isEmpty(); shown++) {
      Map.Entry<String, Tally> best = order.get(0);
      for (Map.Entry<String, Tally> e : order) if (e.getValue().total > best.getValue().total) best = e;
      order.remove(best);
      Tally t = best.getValue();
      text.append(" | ").append(best.getKey()).append(": ").append(t.count).append("x avg ").append(ms(t.total / t.count)).append(" worst ").append(ms(t.worst));
    }
    return text.toString();
  }

  private static void open() throws Exception {
    File folder = new File(Minecraft.getMinecraft().mcDataDir, "logs");
    folder.mkdirs();
    File file = new File(folder, "luka-stalls.log");
    if (file.length() > MAX_FILE / 2) {
      File old = new File(folder, "luka-stalls.old.log");
      old.delete();
      file.renameTo(old);
    }
    out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8));
    written = file.length();
    out.write("---- " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " stall log, Java " + System.getProperty("java.version")
        + ", a stall is a frame over " + thresholdMs + " ms and over " + SLOWER + " times the usual frame\n");
  }

  private static String ms(long nanos) {
    return String.format(Locale.ROOT, "%.1f", nanos / 1_000_000.0);
  }

  private static double number(String property, double fallback) {
    try {
      String text = System.getProperty(property);
      return text == null ? fallback : Double.parseDouble(text);
    } catch (Throwable ignored) {
      return fallback;
    }
  }
}
