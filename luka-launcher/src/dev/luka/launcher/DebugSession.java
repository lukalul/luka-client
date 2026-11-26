package dev.luka.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Debug mode (SEND LOGS > "Record a debug session first", then PLAY): while that game runs, the launcher records the
 * system side once a second into &lt;luka folder&gt;/debug/session-&lt;time&gt;/system.csv, next to the game's own
 * logs/luka-debug-*.csv (frame times, chunk updates). Here: CPU of the system and of the game, free memory, and GPU use:
 * on Windows from Windows' GPU counters (any vendor: the game's 3D share, the total, the game's video memory, and the
 * busiest other program), on NVIDIA also nvidia-smi (load, clocks, power, temperature, throttling), on Linux with an AMD
 * card the driver's busy percentage. system-info.txt: graphics cards and drivers, power plan, NVIDIA details. The
 * tools run as separate processes, so the game's own frames are not slowed down.
 */
final class DebugSession {
  private final File dir;
  private final List<Process> tools = new ArrayList<>();
  private volatile boolean running;
  private Thread sampler;
  private long gamePid = -1;

  // latest values from the streaming tools
  private volatile String[] nvidia = new String[0];
  private volatile double gpuTotal = -1, gpuGame = -1, gpuMemGameMb = -1;
  private volatile String gpuOther = "";
  private volatile long typeperfStarted;
  private volatile boolean typeperfSawGame;

  DebugSession(File root) {
    this.dir = new File(root, "debug/session-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()));
  }

  File dir() {
    return dir;
  }

  /** Starts recording for this game process (its pid comes from Process.pid(), Java 9+). */
  void start(Process game) {
    start(pid(game));
  }

  /** Starts recording for the process with this id (tests attach it to a rig game). */
  void start(long pid) {
    gamePid = pid;
    dir.mkdirs();
    running = true;
    new Thread(this::writeSystemInfo, "luka-debug-info").start();
    if (Platform.windows()) startTypeperf();
    startNvidia();
    sampler = new Thread(this::sample, "luka-debug-sampler");
    sampler.setDaemon(true);
    sampler.start();
  }

  void stop() {
    running = false;
    synchronized (tools) {
      for (Process p : tools) p.destroy();
      tools.clear();
    }
    if (sampler != null) sampler.interrupt();
  }

  // ---- once a second ----

  private void sample() {
    long started = System.nanoTime();
    long lastCpu = -1, lastAt = System.nanoTime();
    int cores = Runtime.getRuntime().availableProcessors();
    try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(dir, "system.csv")), StandardCharsets.UTF_8))) {
      out.println("# luka debug session (launcher side): " + System.getProperty("os.name") + " " + System.getProperty("os.version")
          + ", cores " + cores + ", game pid " + gamePid + ", memory " + (totalMemoryMb()) + " MB");
      out.println("time,uptime_s,system_cpu,game_cpu,free_memory_mb,gpu_3d_total,gpu_3d_game,gpu_memory_game_mb,gpu_busiest_other,"
          + "nv_gpu,nv_memory,nv_clock_mhz,nv_max_clock_mhz,nv_power_w,nv_temp_c,nv_throttle,amd_busy");
      while (running) {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          break;
        }
        long now = System.nanoTime();
        long cpu = processCpuNanos(gamePid);
        String gameCpu = "";
        if (cpu >= 0 && lastCpu >= 0) gameCpu = fmt(100.0 * (cpu - lastCpu) / (now - lastAt) / cores);
        lastCpu = cpu;
        lastAt = now;
        String[] nv = nvidia;
        StringBuilder line = new StringBuilder();
        line.append(new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date())).append(',')
            .append(Math.round((now - started) / 1e9)).append(',')
            .append(fmt(systemCpu())).append(',').append(gameCpu).append(',')
            .append(freeMemoryMb()).append(',')
            .append(gpuTotal < 0 ? "" : fmt(gpuTotal)).append(',').append(gpuGame < 0 ? "" : fmt(gpuGame)).append(',')
            .append(gpuMemGameMb < 0 ? "" : Math.round(gpuMemGameMb)).append(',').append('"').append(gpuOther).append('"');
        for (int i = 0; i < 7; i++) line.append(',').append(i < nv.length ? nv[i].trim().replace(",", " ") : "");
        line.append(',').append(amdBusy());
        out.println(line);
        out.flush();
        // Windows' counters list the processes using the GPU when typeperf starts: until the game shows up, ask again
        if (Platform.windows() && !typeperfSawGame && System.currentTimeMillis() - typeperfStarted > 15_000) startTypeperf();
      }
    } catch (IOException e) {
      // the folder went away: nothing more to record
    }
  }

  // ---- Windows GPU counters (every vendor) ----

  private void startTypeperf() {
    typeperfStarted = System.currentTimeMillis();
    Process p = launch("typeperf", "-si", "1", "\\GPU Engine(*engtype_3D)\\Utilization Percentage", "\\GPU Process Memory(*)\\Dedicated Usage");
    if (p == null) return;
    final Process mine = p;
    new Thread(() -> readTypeperf(mine), "luka-debug-typeperf").start();
  }

  private Process typeperf;

  private void readTypeperf(Process p) {
    synchronized (this) {
      if (typeperf != null) typeperf.destroy();
      typeperf = p;
    }
    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
      String[] header = null;
      long[] pids = null;
      boolean[] memory = null;
      for (String l; running && (l = r.readLine()) != null; ) {
        if (!l.startsWith("\"")) continue;
        String[] cols = splitCsv(l);
        if (header == null) {
          header = cols;
          pids = new long[cols.length];
          memory = new boolean[cols.length];
          for (int i = 1; i < cols.length; i++) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("pid_(\\d+)_").matcher(cols[i]);
            pids[i] = m.find() ? Long.parseLong(m.group(1)) : -1;
            memory[i] = cols[i].contains("GPU Process Memory");
            if (pids[i] == gamePid) typeperfSawGame = true;
          }
          continue;
        }
        double total = 0, game = 0, mem = 0;
        Map<Long, Double> others = new HashMap<>();
        for (int i = 1; i < cols.length && i < header.length; i++) {
          double v;
          try {
            v = Double.parseDouble(cols[i].trim().replace(',', '.')); // some locales write 12,5
          } catch (NumberFormatException e) {
            continue;
          }
          if (memory[i]) {
            if (pids[i] == gamePid) mem += v;
          } else {
            total += v;
            if (pids[i] == gamePid) game += v;
            else if (pids[i] > 0) others.merge(pids[i], v, Double::sum);
          }
        }
        gpuTotal = Math.min(100, total);
        gpuGame = Math.min(100, game);
        gpuMemGameMb = mem / (1024 * 1024);
        long busiest = -1;
        double most = 5; // below 5 % nobody is worth naming
        for (Map.Entry<Long, Double> e : others.entrySet()) {
          if (e.getValue() > most) {
            most = e.getValue();
            busiest = e.getKey();
          }
        }
        gpuOther = busiest < 0 ? "" : processName(busiest) + " " + fmt(Math.min(100, most)) + "%";
      }
    } catch (IOException ignored) {
      // typeperf ended
    }
  }

  // ---- NVIDIA ----

  private void startNvidia() {
    final Process p = launch("nvidia-smi", "--query-gpu=utilization.gpu,utilization.memory,clocks.sm,clocks.max.sm,power.draw,temperature.gpu,"
        + "clocks_throttle_reasons.active", "--format=csv,noheader,nounits", "-l", "1");
    if (p == null) return;
    new Thread(() -> {
      try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
        for (String l; running && (l = r.readLine()) != null; ) {
          String[] cols = l.split(",");
          if (cols.length >= 7) nvidia = cols;
        }
      } catch (IOException ignored) {
        // nvidia-smi ended
      }
    }, "luka-debug-nvidia").start();
  }

  // ---- Linux AMD ----

  private static String amdBusy() {
    if (!Platform.linux()) return "";
    File[] cards = new File("/sys/class/drm").listFiles((d, n) -> n.matches("card\\d+"));
    if (cards == null) return "";
    for (File card : cards) {
      File busy = new File(card, "device/gpu_busy_percent");
      try {
        if (busy.isFile()) return new String(Files.readAllBytes(busy.toPath()), StandardCharsets.UTF_8).trim();
      } catch (IOException ignored) {
        // not readable
      }
    }
    return "";
  }

  // ---- system info, once ----

  private void writeSystemInfo() {
    StringBuilder s = new StringBuilder("luka debug session, " + new Date() + "\n");
    s.append("os: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version")).append(' ')
        .append(System.getProperty("os.arch")).append("\ncores: ").append(Runtime.getRuntime().availableProcessors())
        .append("\nmemory: ").append(totalMemoryMb()).append(" MB\n\n");
    if (Platform.windows()) {
      s.append("== graphics cards ==\n").append(run(20, "powershell", "-NoProfile", "-Command",
          "Get-CimInstance Win32_VideoController | Select-Object Name,DriverVersion,DriverDate,CurrentHorizontalResolution,CurrentVerticalResolution,"
              + "CurrentRefreshRate,AdapterRAM,Status | Format-List | Out-String -Width 200"));
      s.append("== power plan ==\n").append(run(10, "powercfg", "/getactivescheme")).append('\n');
      s.append("== hardware-accelerated GPU scheduling (HwSchMode 2 = on) ==\n")
          .append(run(10, "reg", "query", "HKLM\\SYSTEM\\CurrentControlSet\\Control\\GraphicsDrivers", "/v", "HwSchMode")).append('\n');
    } else if (Platform.linux()) {
      s.append("== graphics cards ==\n").append(run(10, "sh", "-c", "lspci 2>/dev/null | grep -iE 'vga|3d|display'")).append('\n');
      s.append("== session ==\n").append(run(5, "sh", "-c", "echo XDG_SESSION_TYPE=$XDG_SESSION_TYPE; echo DESKTOP=$XDG_CURRENT_DESKTOP")).append('\n');
    }
    String nv = run(15, "nvidia-smi", "-q", "-d", "PERFORMANCE,CLOCK,POWER,TEMPERATURE");
    if (!nv.trim().isEmpty()) s.append("== nvidia-smi ==\n").append(nv);
    try {
      Files.write(new File(dir, "system-info.txt").toPath(), s.toString().getBytes(StandardCharsets.UTF_8));
    } catch (IOException ignored) {
      // the folder went away
    }
  }

  // ---- helpers ----

  private Process launch(String... command) {
    try {
      Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
      synchronized (tools) {
        tools.add(p);
      }
      return p;
    } catch (IOException e) {
      return null; // the tool is not on this PC
    }
  }

  private static String run(int seconds, String... command) {
    try {
      Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
      StringBuilder out = new StringBuilder();
      Thread reader = new Thread(() -> {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
          for (String l; (l = r.readLine()) != null; ) {
            synchronized (out) {
              if (out.length() < 20_000) out.append(l).append('\n');
            }
          }
        } catch (IOException ignored) {
          // ended
        }
      });
      reader.start();
      if (!p.waitFor(seconds, TimeUnit.SECONDS)) p.destroyForcibly();
      reader.join(2000);
      synchronized (out) {
        return out.toString();
      }
    } catch (Exception e) {
      return "";
    }
  }

  private static String[] splitCsv(String line) {
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean quoted = false;
    for (char c : line.toCharArray()) {
      if (c == '"') quoted = !quoted;
      else if (c == ',' && !quoted) {
        out.add(cur.toString());
        cur.setLength(0);
      } else cur.append(c);
    }
    out.add(cur.toString());
    return out.toArray(new String[0]);
  }

  /** Process.pid() (Java 9+) by reflection: the launcher is built for Java 8 but runs on 21+. */
  static long pid(Process p) {
    try {
      return ((Number) Process.class.getMethod("pid").invoke(p)).longValue();
    } catch (Throwable t) {
      return -1;
    }
  }

  /** Total CPU time of a process in nanoseconds (ProcessHandle, Java 9+), -1 when unknown. */
  private static long processCpuNanos(long pid) {
    try {
      Object info = info(pid);
      if (info == null) return -1;
      Object duration = ((java.util.Optional<?>) Class.forName("java.lang.ProcessHandle$Info").getMethod("totalCpuDuration").invoke(info)).orElse(null);
      return duration == null ? -1 : (Long) Class.forName("java.time.Duration").getMethod("toNanos").invoke(duration);
    } catch (Throwable t) {
      return -1;
    }
  }

  private static String processName(long pid) {
    try {
      Object info = info(pid);
      Object command = info == null ? null : ((java.util.Optional<?>) Class.forName("java.lang.ProcessHandle$Info").getMethod("command").invoke(info)).orElse(null);
      if (command == null) return "pid " + pid;
      String c = command.toString();
      return c.substring(Math.max(c.lastIndexOf('\\'), c.lastIndexOf('/')) + 1);
    } catch (Throwable t) {
      return "pid " + pid;
    }
  }

  private static Object info(long pid) throws Exception {
    Class<?> handle = Class.forName("java.lang.ProcessHandle");
    Object opt = handle.getMethod("of", long.class).invoke(null, pid);
    Object h = ((java.util.Optional<?>) opt).orElse(null);
    if (h == null) return null;
    Method m = handle.getMethod("info");
    m.setAccessible(true);
    return m.invoke(h);
  }

  private static double systemCpu() {
    Object v = osBean("getCpuLoad", "getSystemCpuLoad");
    return v instanceof Number ? Math.max(0, ((Number) v).doubleValue() * 100) : -1;
  }

  private static long freeMemoryMb() {
    Object v = osBean("getFreeMemorySize", "getFreePhysicalMemorySize");
    return v instanceof Number ? ((Number) v).longValue() >> 20 : -1;
  }

  private static long totalMemoryMb() {
    Object v = osBean("getTotalMemorySize", "getTotalPhysicalMemorySize");
    return v instanceof Number ? ((Number) v).longValue() >> 20 : -1;
  }

  private static Object osBean(String... names) {
    Object os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
    for (String name : names) {
      try {
        Class<?> api = Class.forName("com.sun.management.OperatingSystemMXBean");
        return api.getMethod(name).invoke(os);
      } catch (Throwable ignored) {
        // older or newer name
      }
    }
    return null;
  }

  private static String fmt(double v) {
    return v < 0 ? "" : String.format(Locale.ROOT, "%.1f", v);
  }
}
