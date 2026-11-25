package dev.luka.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Prepares and starts the client: downloads missing Minecraft assets, unpacks the native libraries and runs the game
 * with the class path, Java flags and arguments that the bundle describes.
 */
final class GameRunner {
  static final String MAIN_CLASS = "net.fabricmc.loader.impl.launch.knot.KnotClient";
  private static final Pattern ASSET_ENTRY = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\{\\s*\"hash\"\\s*:\\s*\"([0-9a-f]{40})\"");
  /** Encrypted copies of luka's mods, shipped by releases 1.4.0 and 1.4.1; this launcher no longer opens them. */
  private static final String ENCRYPTED = ".jar.enc";

  /** The identity the game starts with. */
  static final class Identity {
    String name, uuid, accessToken = "0", userType = "legacy";
    String server = "";
  }

  private final File root; // the luka folder next to the launcher
  private final GameVersion version;
  private final File home; // this version's own files (the luka folder itself for 1.8.9)
  private Identity identity;
  private final Settings settings;
  private final Consumer<String> log;
  private final ProgressSink progress;
  private volatile Process process;
  /** Extra JVM options, after luka's own (the replay editor's render job). */
  private final List<String> extraJvmArgs = new ArrayList<>();

  void addJvmArg(String arg) {
    extraJvmArgs.add(arg);
  }

  /** Start the game on a hidden desktop (Windows, the replay editor's renders): it renders, but no window shows. */
  private boolean hidden;

  void runHidden() {
    hidden = true;
  }

  /**
   * luka-hidden.exe (exe/hidden.cpp, carried in this jar), unpacked into the luka folder when missing or different.
   * It runs the game on its own desktop and ends it when it ends itself. Null when not on Windows or it cannot be unpacked.
   */
  private File hiddenHelper() {
    if (!Platform.windows()) return null;
    try (java.io.InputStream in = GameRunner.class.getResourceAsStream("/luka-hidden.exe")) {
      if (in == null) return null;
      java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
      byte[] b = new byte[1 << 16];
      for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
      byte[] exe = buf.toByteArray();
      File target = new File(root, "luka-hidden.exe");
      if (!target.isFile() || !java.util.Arrays.equals(java.nio.file.Files.readAllBytes(target.toPath()), exe)) {
        File tmp = new File(root, "luka-hidden.exe.tmp");
        java.nio.file.Files.write(tmp.toPath(), exe);
        java.nio.file.Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
      return target;
    } catch (IOException e) {
      log.accept("Could not unpack luka-hidden.exe, the game window will show: " + e.getMessage());
      return null;
    }
  }

  interface ProgressSink {
    void update(String status, double fraction);
  }

  GameRunner(File root, Settings settings, Identity identity, Consumer<String> log, ProgressSink progress) {
    this.root = root;
    this.identity = identity;
    this.settings = settings;
    this.version = settings.version();
    this.home = version.home(root);
    this.log = log;
    this.progress = progress;
  }

  File gameDir() {
    return new File(home, "game");
  }

  boolean isGameRunning() { return process != null && process.isAlive(); }

  /** Ends the game (with luka-hidden.exe in front, ending the helper ends the game through its job object). */
  void stop() {
    Process p = process;
    if (p != null) p.destroy();
  }

  /** Runs the whole sequence and waits for the game to close. Returns the game's exit code. */
  int play() throws Exception {
    File libs = new File(home, "libs");
    if (!libs.isDirectory()) throw new IOException("Missing the game files (" + libs + "). Run build-launcher.ps1 first.");
    progress.update("Checking game files", 0.02);
    File assets = new File(root, "assets");
    ensureAssets(assets);
    progress.update("Unpacking natives", 0.9);
    File natives = unpackNatives();
    progress.update("Starting luka", 0.95);
    sweepOldLaunchTempDirs();
    List<String> command = command(libs, assets, natives);
    log.accept("Command: " + String.join(" ", command).replace(identity.accessToken, "***"));
    NvidiaProfile.apply(settings, new File(command.get(0)), log);
    gameDir().mkdirs();
    List<String> launch = command;
    File helper = hidden ? hiddenHelper() : null;
    if (helper != null) {
      launch = new ArrayList<>();
      launch.add(helper.getAbsolutePath());
      launch.addAll(command);
      log.accept("Starting the game on a hidden desktop (no window)");
    }
    ProcessBuilder builder = new ProcessBuilder(launch).directory(gameDir()).redirectErrorStream(true);
    process = builder.start();
    progress.update("Playing", 1.0);
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) log.accept(line);
    }
    int code = process.waitFor();
    process = null;
    return code;
  }

  /**
   * Launchers of releases 1.4.0 and 1.4.1 opened encrypted mods into luka-launch-* temp folders: deletes any they left
   * behind, except those a game still has open (on Windows a folder with open files cannot be renamed; on Linux a
   * running process names the folder on its command line).
   */
  private static void sweepOldLaunchTempDirs() {
    File[] old = new File(System.getProperty("java.io.tmpdir")).listFiles((dir, name) -> name.startsWith("luka-launch-"));
    if (old == null) return;
    for (File dir : old) {
      if (!dir.isDirectory() || inUseByGame(dir)) continue;
      deleteTree(dir);
    }
  }

  private static boolean inUseByGame(File dir) {
    if (Platform.windows()) {
      File probe = new File(dir.getParentFile(), dir.getName() + ".sweep");
      if (!dir.renameTo(probe)) return true;   // something holds a file inside open
      if (!probe.renameTo(dir)) deleteTree(probe); // could not put it back: it is free, so delete it under that name
      return false;
    }
    File[] procs = new File("/proc").listFiles((d, name) -> name.chars().allMatch(Character::isDigit));
    if (procs == null) return true;            // cannot tell: leave it
    byte[] needle = dir.getAbsolutePath().getBytes(StandardCharsets.UTF_8);
    for (File p : procs) {
      try {
        byte[] cmd = Files.readAllBytes(new File(p, "cmdline").toPath());
        if (indexOf(cmd, needle) >= 0) return true;
      } catch (IOException | SecurityException gone) {
        // the process ended or is not ours
      }
    }
    return false;
  }

  private static int indexOf(byte[] hay, byte[] needle) {
    outer:
    for (int i = 0; i + needle.length <= hay.length; i++) {
      for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
      return i;
    }
    return -1;
  }

  private static void deleteTree(File root) {
    try {
      Files.walkFileTree(root.toPath(), new SimpleFileVisitor<Path>() {
        @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.deleteIfExists(file);
          return FileVisitResult.CONTINUE;
        }
        @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          Files.deleteIfExists(dir);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException ignored) {
      // best-effort: what is left (a file still open) goes on the next launch's sweep
    }
  }

  private List<String> command(File libs, File assets, File natives) throws Exception {
    List<String> command = new ArrayList<>();
    command.add(javaExecutable());
    for (String arg : lines(new File(home, "jvmargs.txt"))) {
      // the Windows toolkit package only exists on Windows; opening it elsewhere only prints a warning
      if (!Platform.windows() && arg.contains("sun.awt.windows")) continue;
      // older installs have -Djava.net.preferIPv4Stack=true in their jvmargs.txt. It stops Java from talking to IPv6
      // DNS servers, and when Windows lists one of those first, the game's server lookup (SRV records such as
      // pvp.land -> na-east.pvp.land) fails, so the server list cannot ping and joining hangs. Connections still go
      // over IPv4 without it (Java prefers IPv4 addresses by default).
      if (arg.startsWith("-Djava.net.preferIPv4Stack")) continue;
      command.add(arg);
    }
    int ram = Math.max(1024, settings.ramMb());
    command.add("-Xms" + ram + "m");
    command.add("-Xmx" + ram + "m");
    command.add("-Djava.library.path=" + natives.getAbsolutePath());
    // where the game's account screen finds this launcher, its home folder and the shared account list
    File launcherJar = Main.launcherJar();
    if (launcherJar != null) command.add("-Dluka.launcher=" + launcherJar.getAbsolutePath());
    command.add("-Dluka.home=" + root.getAbsolutePath());
    command.add("-Dluka.accounts=" + new File(root, "accounts.properties").getAbsolutePath());
    String installId = installId();
    command.add("-Dluka.installId=" + installId);
    File renderer = new File(home, "renderers/" + settings.renderer());
    File mods = new File(gameDir(), "mods");
    // An encrypted mod left here means the update list still names one (an older release): Fabric cannot load it, so
    // stop Play instead of starting a game with luka's mods missing
    if (!encryptedJars(mods).isEmpty() || !encryptedJars(renderer).isEmpty())
      throw new IOException("This luka download is from an older release. Press Play again in a few minutes, or use Repair.");
    if (renderer.isDirectory()) command.add("-Dfabric.addMods=" + renderer.getAbsolutePath());
    command.addAll(extraJvmArgs);
    StringBuilder classpath = new StringBuilder();
    for (String entry : lines(new File(home, "classpath.txt"))) {
      File path = new File(libs, entry);
      if (classpath.length() > 0) classpath.append(File.pathSeparatorChar);
      classpath.append(path.getAbsolutePath());
    }
    command.add("-cp");
    command.add(classpath.toString());
    command.add(MAIN_CLASS);
    command.add("--username");
    command.add(identity.name);
    command.add("--version");
    command.add("luka");
    command.add("--gameDir");
    command.add(gameDir().getAbsolutePath());
    command.add("--assetsDir");
    command.add(assets.getAbsolutePath());
    command.add("--assetIndex");
    command.add(version.assetIndex);
    command.add("--uuid");
    command.add(identity.uuid);
    command.add("--accessToken");
    command.add(identity.accessToken);
    command.add("--userProperties");
    command.add("{}");
    command.add("--userType");
    command.add(identity.userType);
    if (!identity.server.isEmpty()) {
      String host = identity.server;
      String port = "25565";
      int colon = host.lastIndexOf(':');
      if (colon > 0) {
        port = host.substring(colon + 1);
        host = host.substring(0, colon);
      }
      command.add("--server");
      command.add(host);
      command.add("--port");
      command.add(port);
    }
    return command;
  }

  private String javaExecutable() throws Exception {
    File selected = Installer.findUsableRuntime(root, settings);
    if (selected == null) selected = Installer.ensureRuntime(root, (status, fraction) -> progress.update(status, fraction));
    return selected.getAbsolutePath();
  }

  private static List<String> lines(File file) throws IOException {
    if (!file.isFile()) return Collections.emptyList();
    List<String> out = new ArrayList<>();
    for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
      line = line.trim();
      if (!line.isEmpty() && !line.startsWith("#")) out.add(line);
    }
    return out;
  }

  /** Unpacks the platform libraries of natives/*.jar into a fresh folder. */
  private File unpackNatives() throws IOException {
    File target = new File(home, "natives-unpacked");
    target.mkdirs();
    File[] jars = new File(home, "natives").listFiles();
    if (jars == null) return target;
    for (File jar : jars) {
      if (!jar.getName().endsWith(".jar")) continue;
      try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(jar.toPath()))) {
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
          String name = entry.getName();
          if (entry.isDirectory() || name.startsWith("META-INF") || name.contains("/")) continue;
          File out = new File(target, name);
          if (out.isFile() && out.length() > 0) continue; // already unpacked (and possibly in use)
          try (FileOutputStream stream = new FileOutputStream(out)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = zip.read(buffer)) > 0) stream.write(buffer, 0, n);
          }
        }
      }
    }
    return target;
  }

  // ---- Minecraft assets (sounds, language files) ----

  private void ensureAssets(File assets) throws Exception {
    File index = new File(assets, "indexes/" + version.assetIndex + ".json");
    if (!index.isFile()) {
      progress.update("Downloading asset index", 0.03);
      download(version.assetIndexUrl, index);
    }
    String json = new String(Files.readAllBytes(index.toPath()), StandardCharsets.UTF_8);
    List<String> hashes = new ArrayList<>();
    Matcher m = ASSET_ENTRY.matcher(json);
    while (m.find()) hashes.add(m.group(2));
    List<String> missing = new ArrayList<>();
    for (String hash : hashes) if (!new File(assets, "objects/" + hash.substring(0, 2) + "/" + hash).isFile()) missing.add(hash);
    if (missing.isEmpty()) return;
    log.accept("Downloading " + missing.size() + " missing assets");
    ExecutorService pool = Executors.newFixedThreadPool(24);
    AtomicInteger done = new AtomicInteger();
    final int total = missing.size();
    List<Exception> failures = Collections.synchronizedList(new ArrayList<Exception>());
    for (final String hash : missing) {
      pool.submit(() -> {
        try {
          String prefix = hash.substring(0, 2);
          download("https://resources.download.minecraft.net/" + prefix + "/" + hash, new File(assets, "objects/" + prefix + "/" + hash));
        } catch (Exception e) {
          failures.add(e);
        }
        int n = done.incrementAndGet();
        if (n % 20 == 0 || n == total) progress.update("Downloading assets " + n + "/" + total, 0.05 + 0.8 * n / total);
      });
    }
    pool.shutdown();
    pool.awaitTermination(30, TimeUnit.MINUTES);
    if (!failures.isEmpty()) throw new IOException("Could not download " + failures.size() + " assets: " + failures.get(0).getMessage());
  }

  // ---- leftovers of releases 1.4.0 and 1.4.1, which shipped encrypted mods (*.jar.enc) ----

  /** The *.jar.enc files directly inside dir, sorted by name (none when dir is missing). */
  private static List<File> encryptedJars(File dir) {
    File[] found = dir.listFiles((d, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(ENCRYPTED));
    if (found == null) return Collections.emptyList();
    List<File> out = new ArrayList<>();
    Collections.addAll(out, found);
    out.sort((a, b) -> a.getName().compareTo(b.getName()));
    return out;
  }

  // ---- a random per-install id (never a Minecraft account), handed to the game for its crash reports ----

  /** Generated once and kept next to the other luka files; the same id every launch, this install only. */
  private String installId() {
    File file = new File(root, "install-id.txt");
    try {
      if (file.isFile()) {
        String existing = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim().toLowerCase();
        if (existing.matches("[0-9a-f-]{36}")) return existing;
      }
      String id = UUID.randomUUID().toString();
      Files.write(file.toPath(), id.getBytes(StandardCharsets.UTF_8));
      return id;
    } catch (IOException cannotPersist) {
      return UUID.randomUUID().toString(); // a fresh id this one launch beats failing to start over a write error
    }
  }

  private static void download(String address, File target) throws IOException {
    IOException last = null;
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        target.getParentFile().mkdirs();
        File part = new File(target.getPath() + ".part");
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "luka-launcher/1.0");
        if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode() + " from " + address);
        try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
          byte[] buffer = new byte[16384];
          int n;
          while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        }
        if (part.length() == 0) throw new IOException("Empty download from " + address);
        Files.move(part.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return;
      } catch (IOException e) {
        last = e;
        try { Thread.sleep(700L * (attempt + 1)); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("Interrupted", interrupted); }
      }
    }
    throw last;
  }
}
