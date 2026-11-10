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
    List<String> command = command(libs, assets, natives);
    log.accept("Command: " + String.join(" ", command).replace(identity.accessToken, "***"));
    NvidiaProfile.apply(settings, new File(command.get(0)), log);
    gameDir().mkdirs();
    ProcessBuilder builder = new ProcessBuilder(command).directory(gameDir()).redirectErrorStream(true);
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

  private List<String> command(File libs, File assets, File natives) throws Exception {
    List<String> command = new ArrayList<>();
    command.add(javaExecutable());
    for (String arg : lines(new File(home, "jvmargs.txt"))) {
      // the Windows toolkit package only exists on Windows; opening it elsewhere only prints a warning
      if (!Platform.windows() && arg.contains("sun.awt.windows")) continue;
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
    File renderer = new File(home, "renderers/" + settings.renderer());
    if (renderer.isDirectory()) command.add("-Dfabric.addMods=" + renderer.getAbsolutePath());
    StringBuilder classpath = new StringBuilder();
    for (String entry : lines(new File(home, "classpath.txt"))) {
      if (classpath.length() > 0) classpath.append(File.pathSeparatorChar);
      classpath.append(new File(libs, entry).getAbsolutePath());
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
