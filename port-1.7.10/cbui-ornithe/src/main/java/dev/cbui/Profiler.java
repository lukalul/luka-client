package dev.cbui;

import java.io.File;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLContext;

/**
 * FPS profiler behind a hotkey: while on, a background thread samples the stack of the game thread every few
 * milliseconds and the overlay hook records frame times. Turning it off writes a report to config/luka-profile-*.txt
 * that says which methods and which mods the game thread spent its time in.
 */
public final class Profiler {
  private static final int INTERVAL_MS = 4;
  private static final int STACK_DEPTH = 64;
  private static final String[] PLAIN = {"java.", "javax.", "sun.", "jdk.", "net.minecraft.", "org.lwjgl.", "org.spongepowered.", "net.fabricmc.", "com.google.", "io.netty.", "org.apache.", "com.mojang.", "org.objectweb."};

  private static volatile boolean running;
  private static Thread sampler;
  private static Thread hardwareSampler;
  private static Thread target;
  private static long startNanos, lastFrame;
  private static final List<Long> frames = new ArrayList<>();
  private static final List<StackTraceElement[]> samples = new ArrayList<>();
  private static final List<Long> sampleTimes = new ArrayList<>();
  private static final List<Long> frameTimes = new ArrayList<>();
  private static final List<Long> cpuStamps = new ArrayList<>();
  private static final List<Map<String, Long>> cpuTotals = new ArrayList<>();
  private static long lastCpu;
  private static long gcCountStart, gcMsStart;
  private static long lastScene;
  private static int renderedTilesSinceScene;
  private static final List<SceneSample> sceneSamples = new ArrayList<>();
  private static final List<HardwareSample> hardwareSamples = new ArrayList<>();

  private static final class SceneSample {
    long stamp; int entities, renderedEntities, tiles, renderedTiles, particles, chunks, loadedChunks, width, height, distance, maxFps, particleSetting; String blur="unavailable", cape="none";
  }
  private static final class HardwareSample { long stamp; String line, others; HardwareSample(long s,String l,String o){stamp=s;line=l;others=o;} }

  public enum GpuPhase {
    FRAME("whole frame"), SKY("sky"), CLOUDS("clouds"), TERRAIN_SOLID("terrain solid"),
    TERRAIN_CUTOUT("terrain cutout"), TERRAIN_TRANSLUCENT("terrain translucent"), TILE_ENTITIES("tile entities"),
    ENTITIES("entities"), PARTICLES("particles"), WEATHER("weather"), HAND("hand / held item"),
    BLOCK_OUTLINE("block outline"), HUD("HUD"), SCREEN("GUI screen"), BLUR("motion blur"), POST("post-processing");
    final String label;
    GpuPhase(String label) { this.label = label; }
  }

  private static final class GpuQuery {
    final int begin, end;
    final GpuPhase phase;
    final long frame, stamp, cpuNanos;
    GpuQuery(int begin, int end, GpuPhase phase, long frame, long stamp, long cpuNanos) { this.begin = begin; this.end = end; this.phase = phase; this.frame = frame; this.stamp = stamp; this.cpuNanos = cpuNanos; }
  }

  private static final class GpuSample {
    final GpuPhase phase;
    final long frame, stamp, nanos, cpuNanos;
    GpuSample(GpuPhase phase, long frame, long stamp, long nanos, long cpuNanos) { this.phase = phase; this.frame = frame; this.stamp = stamp; this.nanos = nanos; this.cpuNanos = cpuNanos; }
  }

  private static final class ActiveGpu { final GpuPhase phase; final int begin; final long cpu; ActiveGpu(GpuPhase p, int q, long c) { phase=p; begin=q; cpu=c; } }

  private static final ArrayDeque<GpuQuery> gpuPending = new ArrayDeque<>();
  private static final List<GpuSample> gpuSamples = new ArrayList<>();
  /** Query objects are reused: generating and deleting one per phase per frame cost about 12% of the game thread while profiling. */
  private static final ArrayDeque<Integer> gpuFree = new ArrayDeque<>();
  private static final ArrayDeque<ActiveGpu> gpuActive = new ArrayDeque<>();
  private static long gpuFrame;
  private static boolean gpuSupported = true;

  private Profiler() {}

  public static boolean running() {
    return running;
  }

  private static boolean wasDown;

  /** Edge-detects the key straight from the keyboard, so it works whatever other binding shares the key. */
  public static void poll(int keyCode) {
    boolean down = keyCode != 0 && Minecraft.getMinecraft().currentScreen == null
        && (keyCode < 0 ? org.lwjgl.input.Mouse.isButtonDown(keyCode + 100) : org.lwjgl.input.Keyboard.isKeyDown(keyCode));
    if (down && !wasDown) toggle();
    wasDown = down;
  }

  /** A small red label at the top of the screen while a profile is being recorded. */
  public static void indicator() {
    if (!running) return;
    Minecraft mc = Minecraft.getMinecraft();
    String text = "PROFILING " + (System.nanoTime() - startNanos) / 1_000_000_000L + "s - press the key again to save";
    net.minecraft.client.gui.ScaledResolution res = dev.luka.compat.Compat.scaled(mc);
    dev.luka.compat.Compat.drawString(mc.fontRendererObj, text, (res.getScaledWidth() - mc.fontRendererObj.getStringWidth(text)) / 2f, 4, 0xFFFF4D6D, true);
  }

  public static void toggle() {
    if (running) stop();
    else start();
  }

  private static void start() {
    synchronized (samples) {
      samples.clear();
      frames.clear();
      sampleTimes.clear();
      frameTimes.clear();
      cpuStamps.clear();
      cpuTotals.clear();
      gpuSamples.clear();
      sceneSamples.clear(); hardwareSamples.clear();
      lastCpu = 0;
      lastScene = 0;
    }
    discardGpuQueries();
    gpuFrame = 0;
    gpuSupported = true;
    target = Thread.currentThread();
    startNanos = System.nanoTime();
    lastFrame = 0;
    long[] gc = gc();
    gcCountStart = gc[0];
    gcMsStart = gc[1];
    running = true;
    sampler = new Thread(() -> {
      while (running) {
        try {
          StackTraceElement[] stack = target.getStackTrace();
          if (stack.length > 0) {
            if (stack.length > STACK_DEPTH * 4) stack = Arrays.copyOf(stack, STACK_DEPTH * 4);
            synchronized (samples) {
              samples.add(stack);
              sampleTimes.add(System.nanoTime());
            }
          }
          long now = System.nanoTime();
          if (now - lastCpu > 250_000_000L) {
            lastCpu = now;
            Map<String, Long> totals = cpu();
            synchronized (samples) {
              cpuStamps.add(now);
              cpuTotals.add(totals);
            }
          }
          Thread.sleep(INTERVAL_MS);
        } catch (InterruptedException e) {
          return;
        } catch (Throwable ignored) {
          // keep sampling
        }
      }
    }, "luka-profiler");
    sampler.setDaemon(true);
    sampler.start();
    hardwareSampler=new Thread(() -> { while(running){try{long now=System.nanoTime();HardwareSample h=readHardware(now);if(h!=null)synchronized(samples){hardwareSamples.add(h);}Thread.sleep(2000L);}catch(InterruptedException done){return;}catch(Throwable ignored){}}},"luka-gpu-sampler");
    hardwareSampler.setDaemon(true);hardwareSampler.start();
    say("Profiler ON - play normally, press the key again to stop and save the report.");
  }

  /** Called once per rendered frame. */
  public static void frame() {
    if (!running) return;
    gpuFrame++;
    harvestGpuQueries();
    long now = System.nanoTime();
    if (lastFrame != 0) {
      synchronized (samples) {
        frames.add(now - lastFrame);
        frameTimes.add(now);
      }
    }
    lastFrame = now;
    if (now-lastScene >= 1_000_000_000L) { lastScene=now; SceneSample s=readScene(now); if(s!=null) synchronized(samples){sceneSamples.add(s);} }
  }

  /** Starts a timestamp pair. Timestamp queries may be nested or overlap, unlike GL_TIME_ELAPSED. */
  public static void gpuBegin(GpuPhase phase) {
    if (!running || phase == null) return;
    if (phase == GpuPhase.TILE_ENTITIES) renderedTilesSinceScene++;
    long cpu=System.nanoTime();
    if(!gpuSupported){gpuActive.push(new ActiveGpu(phase,0,cpu));return;}
    try {
      if (!GLContext.getCapabilities().OpenGL33) { gpuSupported = false; gpuActive.push(new ActiveGpu(phase,0,cpu)); return; }
      Integer reused = gpuFree.pollFirst();
      int query = reused != null ? reused : GL15.glGenQueries();
      GL33.glQueryCounter(query, GL33.GL_TIMESTAMP);
      gpuActive.push(new ActiveGpu(phase, query, cpu));
    } catch (Throwable error) {
      gpuSupported = false;
      gpuActive.push(new ActiveGpu(phase,0,cpu));
      Cbui.reportOnce("GPU profiler", error);
    }
  }

  /** Starts a phase unless it is already open: many small calls (every block entity) then count as one timed span closed by whoever ends it. */
  public static void gpuBeginOnce(GpuPhase phase) {
    if (!running || phase == null) return;
    for (ActiveGpu candidate : gpuActive) {
      if (candidate.phase == phase) {
        if (phase == GpuPhase.TILE_ENTITIES) renderedTilesSinceScene++;
        return;
      }
    }
    gpuBegin(phase);
  }

  public static void gpuEnd(GpuPhase phase) {
    if (!running || gpuActive.isEmpty()) return;
    ActiveGpu active = null;
    for (ActiveGpu candidate : gpuActive) if (candidate.phase == phase) { active = candidate; break; }
    if (active == null) return;
    gpuActive.remove(active);
    long now=System.nanoTime();
    if(active.begin==0){synchronized(samples){gpuSamples.add(new GpuSample(phase,gpuFrame,now,0,now-active.cpu));}return;}
    try {
      Integer reused = gpuFree.pollFirst();
      int end = reused != null ? reused : GL15.glGenQueries();
      GL33.glQueryCounter(end, GL33.GL_TIMESTAMP);
      gpuPending.addLast(new GpuQuery(active.begin, end, phase, gpuFrame, now, now - active.cpu));
    } catch (Throwable error) {
      gpuSupported = false;
      Cbui.reportOnce("GPU profiler", error);
    }
  }

  /** Reads only queries submitted in an earlier frame and only after the driver says they are ready. */
  private static void harvestGpuQueries() {
    if (!gpuSupported) return;
    try {
      java.util.Iterator<GpuQuery> it = gpuPending.iterator();
      while (it.hasNext()) {
        GpuQuery query = it.next();
        if (query.frame >= gpuFrame) continue;
        if (GL15.glGetQueryObjecti(query.end, GL15.GL_QUERY_RESULT_AVAILABLE) == 0 || GL15.glGetQueryObjecti(query.begin, GL15.GL_QUERY_RESULT_AVAILABLE) == 0) continue;
        long nanos = GL33.glGetQueryObjectui64(query.end, GL15.GL_QUERY_RESULT) - GL33.glGetQueryObjectui64(query.begin, GL15.GL_QUERY_RESULT);
        gpuFree.addLast(query.begin); gpuFree.addLast(query.end);
        synchronized (samples) { gpuSamples.add(new GpuSample(query.phase, query.frame, query.stamp, Math.max(0, nanos), query.cpuNanos)); }
        it.remove();
      }
    } catch (Throwable error) {
      gpuSupported = false;
      Cbui.reportOnce("GPU profiler results", error);
    }
  }

  private static void discardGpuQueries() {
    while (!gpuActive.isEmpty()) try { GL15.glDeleteQueries(gpuActive.pop().begin); } catch (Throwable ignored) { }
    while (!gpuPending.isEmpty()) try { GpuQuery q=gpuPending.removeFirst(); GL15.glDeleteQueries(q.begin); GL15.glDeleteQueries(q.end); } catch (Throwable ignored) { }
    while (!gpuFree.isEmpty()) try { GL15.glDeleteQueries(gpuFree.removeFirst()); } catch (Throwable ignored) { }
  }

  private static void stop() {
    running = false;
    long seconds = Math.max(1, (System.nanoTime() - startNanos) / 1_000_000_000L);
    List<StackTraceElement[]> taken;
    List<Long> times, sampleStamps, frameStamps, cpuTimes;
    List<Map<String, Long>> cpuSnapshots;
    List<GpuSample> gpuTaken;
    harvestGpuQueries();
    synchronized (samples) {
      cpuTimes = new ArrayList<>(cpuStamps);
      cpuSnapshots = new ArrayList<>(cpuTotals);
      taken = new ArrayList<>(samples);
      times = new ArrayList<>(frames);
      sampleStamps = new ArrayList<>(sampleTimes);
      frameStamps = new ArrayList<>(frameTimes);
      gpuTaken = new ArrayList<>(gpuSamples);
    }
    long[] gc = gc();
    try {
      File out = new File(Minecraft.getMinecraft().mcDataDir, "config/luka-profile-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".txt");
      out.getParentFile().mkdirs();
      try (PrintWriter w = new PrintWriter(out, "UTF-8")) {
        writeHeader(w);
        write(w, taken, times, gpuTaken, seconds, gc[0] - gcCountStart, gc[1] - gcMsStart);
        timeline(w, taken, sampleStamps, times, frameStamps, cpuTimes, cpuSnapshots, gpuTaken);
        lagAnalysis(w, times, frameStamps, gpuTaken);
        scene(w);
      }
      say("Profiler saved: config/" + out.getName());
    } catch (Throwable error) {
      Cbui.reportOnce("profiler report", error);
      say("Profiler could not write the report: " + error);
    }
  }

  private static SceneSample readScene(long now) {
    try {
      Minecraft mc=Minecraft.getMinecraft(); SceneSample s=new SceneSample(); s.stamp=now; s.width=mc.displayWidth; s.height=mc.displayHeight;
      s.distance=mc.gameSettings.renderDistanceChunks; s.maxFps=mc.gameSettings.limitFramerate; s.particleSetting=mc.gameSettings.particleSetting;
      if(mc.theWorld!=null){s.entities=mc.theWorld.loadedEntityList.size();s.tiles=mc.theWorld.loadedTileEntityList.size();s.loadedChunks=mc.theWorld.getChunkProvider().getLoadedChunkCount();}
      if(mc.renderGlobal!=null){s.renderedEntities=number(mc.renderGlobal.getDebugInfoEntities(),0);s.chunks=number(mc.renderGlobal.getDebugInfoRenders(),0);}
      if(mc.effectRenderer!=null)s.particles=number(mc.effectRenderer.getStatistics(),0);
      try { Class<?> c=Class.forName("dev.motionblur.MotionBlurSettings"); s.blur=c.getField("enabled").getBoolean(null)+" / level "+c.getField("level").getInt(null); } catch(Throwable ignored){}
      s.renderedTiles=renderedTilesSinceScene; renderedTilesSinceScene=0;
      try { Class<?> capes=Class.forName("dev.cbui.cosmetics.Capes"); java.lang.reflect.Field f=capes.getDeclaredField("selected"); f.setAccessible(true); Object v=f.get(null); if(v!=null&&!String.valueOf(v).isEmpty()){s.cape=String.valueOf(v);java.lang.reflect.Field ef=capes.getDeclaredField("entries");ef.setAccessible(true);for(Object e:(Iterable<?>)ef.get(null))if(s.cape.equals(e.getClass().getField("id").get(e))){s.cape=String.valueOf(e.getClass().getField("name").get(e));break;}} } catch(Throwable ignored){}
      return s;
    } catch(Throwable ignored){return null;}
  }

  private static int number(String text,int fallback){ if(text==null)return fallback; java.util.regex.Matcher m=java.util.regex.Pattern.compile("[0-9]+").matcher(text); return m.find()?Integer.parseInt(m.group()):fallback; }

  private static HardwareSample readHardware(long now) {
    try {
      String q=runCommand(new String[]{"nvidia-smi","--query-gpu=utilization.gpu,utilization.memory,clocks.sm,clocks.max.sm,power.draw,temperature.gpu,clocks_throttle_reasons.active","--format=csv,noheader,nounits"});
      if(q==null||q.trim().isEmpty())return null;
      String p=runCommand(new String[]{"nvidia-smi","pmon","-c","1","-s","u"}), others="none";
      if(p!=null){StringBuilder b=new StringBuilder();for(String line:p.split("\\r?\\n")){line=line.trim();if(line.isEmpty()||line.startsWith("#"))continue;String[] a=line.split("\\s+");if(a.length>=8){int sm=parseInt(a[3]),mem=parseInt(a[4]);if(sm>0||mem>0){if(b.length()>0)b.append(", ");b.append(a[a.length-1]).append(' ').append(Math.max(sm,mem)).append('%');}}}if(b.length()>0)others=b.toString();}
      return new HardwareSample(now,q.trim(),others);
    } catch(Throwable ignored){return null;}
  }
  private static int parseInt(String s){try{return Integer.parseInt(s);}catch(Throwable ignored){return 0;}}
  private static String runCommand(String[] command) throws Exception { Process p=new ProcessBuilder(command).redirectErrorStream(true).start(); if(!p.waitFor(1500,java.util.concurrent.TimeUnit.MILLISECONDS)){p.destroy();return null;} BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream(),"UTF-8"));StringBuilder b=new StringBuilder();String line;while((line=r.readLine())!=null)b.append(line).append('\n');return p.exitValue()==0?b.toString():null; }

  private static void writeHeader(PrintWriter w){w.println("== SETTINGS / SCENE HEADER ==");SceneSample s=sceneSamples.isEmpty()?readScene(System.nanoTime()):sceneSamples.get(sceneSamples.size()-1);if(s!=null)writeSceneLine(w,"",s);HardwareSample h=hardwareSamples.isEmpty()?null:hardwareSamples.get(hardwareSamples.size()-1);if(h!=null)w.println("GPU hardware: "+hardwareText(h));else w.println("GPU hardware: nvidia-smi unavailable or no NVIDIA GPU");w.println();}
  private static void writeSceneLine(PrintWriter w,String prefix,SceneSample s){w.println(prefix+"scene: entities "+s.entities+" loaded / "+s.renderedEntities+" rendered, tile entities "+s.tiles+" / "+s.renderedTiles+", particles "+s.particles+", chunks "+s.chunks+" rendered / "+s.loadedChunks+" loaded");w.println(prefix+"settings: "+s.width+"x"+s.height+", distance "+s.distance+", max fps "+s.maxFps+", particles "+s.particleSetting+", motion blur "+s.blur+", cape "+s.cape);}
  private static String hardwareText(HardwareSample h){return h.line+" [gpu%, memory%, clock, max clock, watts, C, throttle], other programs: "+h.others;}

  private static void write(PrintWriter w, List<StackTraceElement[]> taken, List<Long> times, List<GpuSample> gpu, long seconds, long gcCount, long gcMs) {
    int n = taken.size();
    w.println("luka FPS profile - " + seconds + " s, " + n + " stack samples every " + INTERVAL_MS + " ms, " + times.size() + " frames");
    w.println("java " + System.getProperty("java.version") + ", heap used " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576 + " MB of " + Runtime.getRuntime().maxMemory() / 1048576 + " MB");
    w.println("garbage collection during the run: " + gcCount + " runs, " + gcMs + " ms total (" + (gcMs * 100 / (seconds * 1000)) + "% of the time)");
    w.println();
    if (!times.isEmpty()) {
      long[] sorted = new long[times.size()];
      long total = 0;
      for (int i = 0; i < sorted.length; i++) {
        sorted[i] = times.get(i);
        total += sorted[i];
      }
      Arrays.sort(sorted);
      double avg = total / (double) sorted.length / 1e6;
      w.printf("frame time: avg %.2f ms (%.0f fps), median %.2f ms, 1%% low %.0f fps, worst %.1f ms%n", avg, 1000 / avg, sorted[sorted.length / 2] / 1e6, 1e9 / sorted[(int) Math.min(sorted.length - 1, sorted.length * 0.99)], sorted[sorted.length - 1] / 1e6);
      int over25 = 0, over50 = 0;
      for (long t : sorted) {
        if (t > 25_000_000L) over25++;
        if (t > 50_000_000L) over50++;
      }
      w.println("frames over 25 ms: " + over25 + ", over 50 ms: " + over50);
      w.println();
    }
    writeGpu(w, "GPU time per rendered frame", gpu);
    if (n == 0) {
      w.println("no samples were taken");
      return;
    }
    Map<String, Integer> leaf = new HashMap<>(), owner = new HashMap<>(), inclusive = new HashMap<>();
    for (StackTraceElement[] stack : taken) {
      leaf.merge(describe(stack[0]), 1, Integer::sum);
      String who = "vanilla / JDK / libraries";
      for (StackTraceElement e : stack) {
        String pkg = modPackage(e.getClassName());
        if (pkg != null) {
          who = pkg;
          break;
        }
      }
      owner.merge(who, 1, Integer::sum);
      Set<String> seen = new HashSet<>();
      for (StackTraceElement e : stack) {
        String key = describe(e);
        if (seen.add(key)) inclusive.merge(key, 1, Integer::sum);
      }
    }
    section(w, "WHERE THE GAME THREAD IS RIGHT NOW (self time, top of the stack)", leaf, n, 25);
    section(w, "WHICH MOD IS ON THE STACK (the first non-vanilla package from the top; 'vanilla' means no mod code was active)", owner, n, 20);
    section(w, "WHAT IS RUNNING (inclusive: the method is anywhere on the stack, so parents add up past 100%)", inclusive, n, 45);
    w.println("Reading it: big self time in org.lwjgl / glfwSwapBuffers / Display.update means the GPU or vsync is the limit;");
    w.println("a mod package with a large share is that mod costing frame time. The thread is only sampled while it is at a JVM safepoint, so treat small differences as noise.");
  }

  /** What is around the player when the profile stops: entities, block entities and the most common blocks, so a lag spot can be read off. */
  private static void scene(PrintWriter w) {
    try {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc.theWorld == null || mc.thePlayer == null) return;
      w.println("== WHAT IS AROUND YOU (when you stopped) ==");
      w.println("renderer: " + mc.renderGlobal.getDebugInfoRenders() + " | " + mc.renderGlobal.getDebugInfoEntities());
      Map<String, Integer> entities = new HashMap<>();
      for (Object entity : new ArrayList<Object>(mc.theWorld.loadedEntityList)) {
        net.minecraft.entity.Entity e = (net.minecraft.entity.Entity) entity;
        String name = net.minecraft.entity.EntityList.getEntityString(e);
        entities.merge(name == null ? e.getCommandSenderName() : name, 1, Integer::sum);
      }
      w.println("entities loaded (" + mc.theWorld.loadedEntityList.size() + "): " + top(entities, Math.max(1, mc.theWorld.loadedEntityList.size()), 8));
      Map<String, Integer> tiles = new HashMap<>();
      for (Object tile : new ArrayList<Object>(mc.theWorld.loadedTileEntityList)) {
        net.minecraft.tileentity.TileEntity t = (net.minecraft.tileentity.TileEntity) tile;
        net.minecraft.block.Block block = t.getBlockType();
        tiles.merge(block == null ? "?" : String.valueOf(net.minecraft.block.Block.blockRegistry.getNameForObject(block)), 1, Integer::sum);
      }
      w.println("block entities loaded (" + mc.theWorld.loadedTileEntityList.size() + "): " + top(tiles, Math.max(1, mc.theWorld.loadedTileEntityList.size()), 8));
      Map<String, Integer> blocks = new HashMap<>();
      int px = (int) mc.thePlayer.posX, py = (int) mc.thePlayer.posY, pz = (int) mc.thePlayer.posZ, scanned = 0;
      for (int x = px - 32; x <= px + 32; x++)
        for (int z = pz - 32; z <= pz + 32; z++)
          for (int y = Math.max(0, py - 24); y <= Math.min(255, py + 24); y++) {
            net.minecraft.block.Block b = mc.theWorld.getBlock(x, y, z);
            if (b == net.minecraft.init.Blocks.air) continue;
            scanned++;
            blocks.merge(String.valueOf(net.minecraft.block.Block.blockRegistry.getNameForObject(b)), 1, Integer::sum);
          }
      w.println("blocks within 32 blocks (" + scanned + " non-air): " + top(blocks, Math.max(1, scanned), 12));
      w.println();
    } catch (Throwable error) {
      w.println("(could not read the surroundings: " + error + ")");
    }
  }

  /** Frame rate and the busiest methods and mods for every 10 seconds, to see when and why it dropped. */
  private static void timeline(PrintWriter w, List<StackTraceElement[]> taken, List<Long> sampleStamps, List<Long> frames, List<Long> frameStamps, List<Long> cpuTimes, List<Map<String, Long>> cpuSnapshots, List<GpuSample> gpu) {
    long length = 10_000_000_000L;
    int segments = (int) Math.max(1, ((System.nanoTime() - startNanos) + length - 1) / length);
    w.println("== TIMELINE (10 second slices) ==");
    for (int seg = 0; seg < segments; seg++) {
      long from = startNanos + seg * length, to = from + length;
      long total = 0, worst = 0;
      int count = 0;
      for (int i = 0; i < frameStamps.size(); i++) {
        long at = frameStamps.get(i);
        if (at < from || at >= to) continue;
        long d = frames.get(i);
        total += d;
        worst = Math.max(worst, d);
        count++;
      }
      Map<String, Integer> leaf = new HashMap<>(), owner = new HashMap<>(), parts = new HashMap<>();
      int n = 0;
      for (int i = 0; i < taken.size(); i++) {
        long at = sampleStamps.get(i);
        if (at < from || at >= to) continue;
        n++;
        StackTraceElement[] stack = taken.get(i);
        leaf.merge(describe(stack[0]), 1, Integer::sum);
        String who = "vanilla";
        for (StackTraceElement e : stack) {
          String pkg = modPackage(e.getClassName());
          if (pkg != null) {
            who = pkg;
            break;
          }
        }
        owner.merge(who, 1, Integer::sum);
        Set<String> seen = new HashSet<>();
        for (StackTraceElement e : stack) {
          String name = e.getClassName();
          if ((name.contains("argentum") || name.contains("artemis") || name.contains("celeritas") || name.contains("embeddium") || name.contains("cera") || name.contains("cbui") || name.contains("motionblur")) && seen.add(describe(e))) parts.merge(describe(e), 1, Integer::sum);
        }
      }
      if (count == 0) {
        w.printf("%3d-%3d s: no frames%n", seg * 10, seg * 10 + 10);
        continue;
      }
      double avg = total / (double) count / 1e6;
      w.printf("%3d-%3d s: %.0f fps (avg %.2f ms, worst %.1f ms)%n", seg * 10, seg * 10 + 10, 1000 / avg, avg, worst / 1e6);
      w.println("      self:  " + top(leaf, n, 3));
      w.println("      mods:  " + top(owner, n, 3));
      w.println("      inside: " + top(parts, n, 3));
      List<GpuSample> gpuSlice = new ArrayList<>();
      for (GpuSample sample : gpu) if (sample.stamp >= from && sample.stamp < to) gpuSlice.add(sample);
      writeGpu(w, "      GPU", gpuSlice);
      SceneSample ss=sceneAt(to);if(ss!=null)writeSceneLine(w,"      ",ss);
      HardwareSample hs=hardwareAt(to);if(hs!=null)w.println("      hardware: "+hardwareText(hs));
      Map<String, Long> before = cpuAt(cpuTimes, cpuSnapshots, from), after = cpuAt(cpuTimes, cpuSnapshots, to);
      if (before != null && after != null && after != before) {
        List<Map.Entry<String, Long>> busy = new ArrayList<>();
        for (Map.Entry<String, Long> e : after.entrySet()) {
          long delta = e.getValue() - (before.containsKey(e.getKey()) ? before.get(e.getKey()) : 0L);
          if (delta > 0) busy.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), delta));
        }
        busy.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        StringBuilder line = new StringBuilder("      cpu by thread (100% = one core): ");
        for (int i = 0; i < Math.min(5, busy.size()); i++) {
          if (i > 0) line.append(" | ");
          line.append(String.format("%.0f%% %s", busy.get(i).getValue() * 100.0 / length, busy.get(i).getKey()));
        }
        w.println(line);
      }
    }
    w.println();
  }

  private static void writeGpu(PrintWriter w, String prefix, List<GpuSample> gpu) {
    if (gpu.isEmpty()) {
      w.println(prefix + ": no completed timer queries");
      return;
    }
    EnumMap<GpuPhase, Long> totals = new EnumMap<>(GpuPhase.class);
    EnumMap<GpuPhase, Long> cpu = new EnumMap<>(GpuPhase.class);
    EnumMap<GpuPhase, Set<Long>> frameIds = new EnumMap<>(GpuPhase.class);
    for (GpuSample sample : gpu) {
      totals.merge(sample.phase, sample.nanos, Long::sum);
      cpu.merge(sample.phase, sample.cpuNanos, Long::sum);
      frameIds.computeIfAbsent(sample.phase, ignored -> new LinkedHashSet<>()).add(sample.frame);
    }
    StringBuilder line = new StringBuilder(prefix).append(": ");
    boolean first = true;
    for (GpuPhase phase : GpuPhase.values()) {
      Long total = totals.get(phase);
      if (total == null) continue;
      if (!first) line.append(" | ");
      first = false;
      line.append(String.format(Locale.ROOT,"%s GPU %.3f ms / CPU %.3f ms", phase.label, total / (double) frameIds.get(phase).size() / 1e6, cpu.get(phase) / (double) frameIds.get(phase).size() / 1e6));
    }
    w.println(line);
    Long frame=totals.get(GpuPhase.FRAME);if(frame!=null){long used=0,cpuUsed=0;for(GpuPhase p:GpuPhase.values())if(p!=GpuPhase.FRAME&&p!=GpuPhase.TILE_ENTITIES&&p!=GpuPhase.BLOCK_OUTLINE){Long v=totals.get(p),c=cpu.get(p);if(v!=null)used+=v;if(c!=null)cpuUsed+=c;}int nf=frameIds.get(GpuPhase.FRAME).size();w.printf(Locale.ROOT,"%s UNACCOUNTED: GPU %.3f ms / CPU %.3f ms (FRAME minus non-nested phases)%n",prefix,Math.max(0,frame-used)/(double)nf/1e6,Math.max(0,cpu.get(GpuPhase.FRAME)-cpuUsed)/(double)nf/1e6);}
  }

  private static SceneSample sceneAt(long time){SceneSample best=null;for(SceneSample s:sceneSamples)if(s.stamp<=time)best=s;return best;}
  private static HardwareSample hardwareAt(long time){HardwareSample best=null;for(HardwareSample s:hardwareSamples)if(s.stamp<=time)best=s;return best;}

  private static void lagAnalysis(PrintWriter w,List<Long> frames,List<Long> stamps,List<GpuSample> gpu){
    w.println("== LAG ANALYSIS ==");long slice=10_000_000_000L;int count=(int)Math.max(1,(System.nanoTime()-startNanos+slice-1)/slice);double[] fps=new double[count];
    for(int seg=0;seg<count;seg++){long total=0;int n=0;long from=startNanos+seg*slice,to=from+slice;for(int i=0;i<stamps.size();i++)if(stamps.get(i)>=from&&stamps.get(i)<to){total+=frames.get(i);n++;}fps[seg]=n==0?0:1e9*n/total;}
    double[] sorted=fps.clone();Arrays.sort(sorted);double median=sorted[sorted.length/2];boolean found=false;
    for(int seg=0;seg<count;seg++)if(fps[seg]>0&&fps[seg]<median*.60){found=true;long from=startNanos+seg*slice,to=from+slice;EnumMap<GpuPhase,Long> sums=new EnumMap<>(GpuPhase.class),cpus=new EnumMap<>(GpuPhase.class);EnumMap<GpuPhase,Integer> ns=new EnumMap<>(GpuPhase.class);for(GpuSample s:gpu)if(s.stamp>=from&&s.stamp<to){sums.merge(s.phase,s.nanos,Long::sum);cpus.merge(s.phase,s.cpuNanos,Long::sum);ns.merge(s.phase,1,Integer::sum);}GpuPhase dominant=null,growGpu=null,growCpu=null;double ms=0,bestGr=0,bestCr=0;for(GpuPhase p:sums.keySet()){double x=sums.get(p)/(double)ns.get(p)/1e6;if(p!=GpuPhase.FRAME&&x>ms){ms=x;dominant=p;}double ng=normalAverage(gpu,fps,median,slice,p,false),nc=normalAverage(gpu,fps,median,slice,p,true);double gr=ng>0?x/ng:0,curCpu=cpus.get(p)/(double)ns.get(p)/1e6,cr=nc>0?curCpu/nc:0;if(gr>bestGr){bestGr=gr;growGpu=p;}if(cr>bestCr){bestCr=cr;growCpu=p;}}HardwareSample h=hardwareAt(to);SceneSample sc=sceneAt(to);w.printf(Locale.ROOT,"%d-%d s: %.0f fps vs median %.0f (%.0f%% lower)%n",seg*10,seg*10+10,fps[seg],median,100*(1-fps[seg]/median));if(growGpu!=null){double cur=sums.get(growGpu)/(double)ns.get(growGpu)/1e6,base=normalAverage(gpu,fps,median,slice,growGpu,false);w.printf(Locale.ROOT,"  GPU growth: %s %.2f ms vs %.2f normal (+%.2f ms, x%.1f)%n",growGpu.label,cur,base,cur-base,bestGr);}if(growCpu!=null){double cur=cpus.get(growCpu)/(double)ns.get(growCpu)/1e6,base=normalAverage(gpu,fps,median,slice,growCpu,true);w.printf(Locale.ROOT,"  CPU growth: %s %.2f ms vs %.2f normal (+%.2f ms, x%.1f)%n",growCpu.label,cur,base,cur-base,bestCr);}if(sc!=null)writeSceneLine(w,"  ",sc);if(h!=null)w.println("  hardware change: "+hardwareText(h));String conclusion=h!=null&&!"none".equals(h.others)?"other program using the GPU: "+h.others:(dominant!=null&&ms>8?"GPU-bound: "+dominant.label+" GPU time x"+String.format(Locale.ROOT,"%.1f",bestGr):"likely CPU-bound; "+(growCpu==null?"inspect stacks":growCpu.label+" grew x"+String.format(Locale.ROOT,"%.1f",bestCr)));w.println("  conclusion: "+conclusion);}
    if(!found)w.println("No 10-second slice fell below 60% of the median slice FPS.");
    Integer[] order=new Integer[frames.size()];for(int i=0;i<order.length;i++)order[i]=i;Arrays.sort(order,(a,b)->Long.compare(frames.get(b),frames.get(a)));w.println("10 worst single frames:");for(int k=0;k<Math.min(10,order.length);k++){int i=order[k];long frameNo=i+1;GpuPhase dom=null;long best=0;for(GpuSample s:gpu)if(s.frame==frameNo&&s.phase!=GpuPhase.FRAME&&s.nanos>best){best=s.nanos;dom=s.phase;}w.printf(Locale.ROOT,"  %.2f ms at %.2f s%s%n",frames.get(i)/1e6,(stamps.get(i)-startNanos)/1e9,dom==null?"":"; dominated by "+dom.label+" ("+String.format(Locale.ROOT,"%.2f ms",best/1e6)+")");}w.println();
  }
  private static double normalAverage(List<GpuSample> gpu,double[] fps,double median,long slice,GpuPhase phase,boolean cpu){long sum=0;int n=0;for(GpuSample s:gpu){int seg=(int)((s.stamp-startNanos)/slice);if(s.phase==phase&&seg>=0&&seg<fps.length&&fps[seg]>=median*.60){sum+=cpu?s.cpuNanos:s.nanos;n++;}}return n==0?0:sum/(double)n/1e6;}

  private static String top(Map<String, Integer> counts, int total, int limit) {
    List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
    entries.sort((a, b) -> b.getValue() - a.getValue());
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < Math.min(limit, entries.size()); i++) {
      if (i > 0) out.append(" | ");
      out.append(String.format("%.0f%% %s", entries.get(i).getValue() * 100.0 / Math.max(1, total), entries.get(i).getKey()));
    }
    return out.toString();
  }

  private static void section(PrintWriter w, String title, Map<String, Integer> counts, int total, int limit) {
    w.println("== " + title + " ==");
    List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
    entries.sort((a, b) -> b.getValue() - a.getValue());
    for (int i = 0; i < Math.min(limit, entries.size()); i++) {
      Map.Entry<String, Integer> e = entries.get(i);
      w.printf("%5.1f%%  %s%n", e.getValue() * 100.0 / total, e.getKey());
    }
    w.println();
  }

  private static String describe(StackTraceElement e) {
    return e.getClassName() + "." + e.getMethodName();
  }

  /** First three package segments for a class that is not vanilla, the JDK or a core library; null otherwise. */
  private static String modPackage(String className) {
    for (String prefix : PLAIN) if (className.startsWith(prefix)) return null;
    String[] parts = className.split("\\.");
    if (parts.length <= 1) return null;
    return parts.length >= 4 ? parts[0] + "." + parts[1] + "." + parts[2] : parts[0] + "." + parts[1];
  }

  /** CPU time used so far by every live thread, summed by thread name with the numbers removed ("Worker-3" and "Worker-4" are one group). */
  private static Map<String, Long> cpu() {
    Map<String, Long> out = new HashMap<>();
    try {
      java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
      long[] ids = bean.getAllThreadIds();
      java.lang.management.ThreadInfo[] infos = bean.getThreadInfo(ids);
      for (int i = 0; i < ids.length; i++) {
        if (infos[i] == null) continue;
        long time = bean.getThreadCpuTime(ids[i]);
        if (time < 0) continue;
        out.merge(infos[i].getThreadName().replaceAll("[0-9]+", "#"), time, Long::sum);
      }
    } catch (Throwable ignored) {
      // thread timing is not available
    }
    return out;
  }

  private static Map<String, Long> cpuAt(List<Long> stamps, List<Map<String, Long>> totals, long time) {
    Map<String, Long> best = null;
    for (int i = 0; i < stamps.size(); i++) if (stamps.get(i) <= time) best = totals.get(i);
    return best;
  }

  private static long[] gc() {
    long count = 0, ms = 0;
    for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
      if (bean.getCollectionCount() > 0) count += bean.getCollectionCount();
      if (bean.getCollectionTime() > 0) ms += bean.getCollectionTime();
    }
    return new long[] {count, ms};
  }

  private static void say(String message) {
    try {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText(String.valueOf((char) 167) + "5[luka] " + (char) 167 + "f" + message));
      System.out.println("[luka] " + message);
    } catch (Throwable ignored) {
      // no chat available
    }
  }
}
