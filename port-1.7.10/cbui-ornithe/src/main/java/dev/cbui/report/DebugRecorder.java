package dev.cbui.report;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import net.minecraft.client.Minecraft;

/**
 * Debug mode (-Dluka.debug=true, set by the launcher's "Record a debug session"): once a second, one line of how the
 * game is doing goes to logs/luka-debug-<time>.csv, for SEND LOGS. The launcher records the system side (GPU, CPU)
 * next to it. Cheap: a few counters per frame, one short line a second. Off (and free) without the switch.
 */
public final class DebugRecorder {
  private static final boolean ON = Boolean.getBoolean("luka.debug");
  private static PrintWriter out;
  private static boolean failed;
  private static long started, secondStart, lastFrame, sumNs, maxNs, gcCount0 = -1, gcMs0;
  private static int frames;

  private DebugRecorder() {}

  public static boolean active() {
    return ON && !failed;
  }

  /** Every frame (runGameLoop). */
  public static void frame() {
    if (!ON || failed) return;
    try {
      long now = System.nanoTime();
      if (out == null) open(now);
      if (lastFrame != 0) {
        long d = now - lastFrame;
        sumNs += d;
        if (d > maxNs) maxNs = d;
        frames++;
      }
      lastFrame = now;
      if (now - secondStart >= 1_000_000_000L) {
        line(now);
        secondStart = now;
        frames = 0;
        sumNs = 0;
        maxNs = 0;
      }
    } catch (Throwable t) {
      failed = true;
    }
  }

  private static void open(long now) throws Exception {
    Minecraft mc = Minecraft.getMinecraft();
    File dir = new File(mc.mcDataDir, "logs");
    dir.mkdirs();
    File file = new File(dir, "luka-debug-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()) + ".csv");
    out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
    String gl = "";
    try {
      gl = org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER) + " / " + org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION);
    } catch (Throwable ignored) {
      // no context yet
    }
    out.println("# luka debug session: minecraft " + LogSender.MINECRAFT + ", java " + System.getProperty("java.version")
        + ", max heap " + (Runtime.getRuntime().maxMemory() >> 20) + " MB, cores " + Runtime.getRuntime().availableProcessors()
        + ", renderer " + (vulkan() ? "vulkan" : "normal") + ", gl " + gl.replace(',', ' '));
    out.println("time,uptime_s,frames,fps,avg_ms,max_ms,chunk_updates,renders,entities,heap_mb,gc_count,gc_ms,place,render_distance,max_fps,screen");
    out.flush();
    started = now;
    secondStart = now;
  }

  private static void line(long now) {
    Minecraft mc = Minecraft.getMinecraft();
    double seconds = (now - secondStart) / 1e9;
    long gcCount = 0, gcMs = 0;
    for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
      gcCount += Math.max(0, gc.getCollectionCount());
      gcMs += Math.max(0, gc.getCollectionTime());
    }
    long dCount = gcCount0 < 0 ? 0 : gcCount - gcCount0, dMs = gcCount0 < 0 ? 0 : gcMs - gcMs0;
    gcCount0 = gcCount;
    gcMs0 = gcMs;
    Runtime rt = Runtime.getRuntime();
    String renders = "", entities = "";
    try {
      if (mc.renderGlobal != null && mc.theWorld != null) {
        renders = mc.renderGlobal.getDebugInfoRenders();
        entities = mc.renderGlobal.getDebugInfoEntities();
      }
    } catch (Throwable ignored) {
      // not drawn yet
    }
    String place = mc.theWorld == null ? "menu" : mc.isSingleplayer() ? "singleplayer" : "multiplayer";
    String screen = mc.currentScreen == null ? "" : mc.currentScreen.getClass().getName();
    out.println(new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date()) + "," + Math.round((now - started) / 1e9) + "," + frames + ","
        + Math.round(frames / seconds) + "," + fmt(frames == 0 ? 0 : sumNs / 1e6 / frames) + "," + fmt(maxNs / 1e6) + ","
        + number(chunkUpdates()) + "," + csv(renders) + "," + csv(entities) + "," + ((rt.totalMemory() - rt.freeMemory()) >> 20) + ","
        + dCount + "," + dMs + "," + place + "," + mc.gameSettings.renderDistanceChunks + "," + mc.gameSettings.limitFramerate + "," + csv(screen));
    out.flush();
  }

  /** "60 fps, 12 chunk updates": Minecraft's own debug line (F3), the chunk updates of the last second. */
  private static String chunkUpdates() {
    try {
      return Minecraft.getMinecraft().debug;
    } catch (Throwable t) {
      return "";
    }
  }

  private static String number(String debug) {
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+) chunk update").matcher(debug == null ? "" : debug);
    return m.find() ? m.group(1) : "";
  }

  private static boolean vulkan() {
    try {
      Class.forName("yt.ren.astatine.Native", false, DebugRecorder.class.getClassLoader());
      return true;
    } catch (Throwable e) {
      return false;
    }
  }

  private static String fmt(double v) {
    return String.format(Locale.ROOT, "%.2f", v);
  }

  private static String csv(String s) {
    if (s == null) return "";
    return "\"" + s.replace("\"", "'") + "\"";
  }
}
