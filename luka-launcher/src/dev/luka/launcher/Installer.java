package dev.luka.launcher;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** Downloads the redistributable-free game installation from its official repositories. */
public final class Installer {
  private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
  private static final String[] REPOS = {"https://libraries.minecraft.net/", "https://maven.fabricmc.net/",
      "https://maven.ornithemc.net/releases/", "https://maven.legacyfabric.net/releases/",
      "https://maven.legacyfabric.net/", "https://repo1.maven.org/maven2/"};
  private static final String CLIENT = "com/mojang/minecraft/1.8.9/minecraft-1.8.9-client.jar";
  private static final String TEMURIN = "https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jre&os=windows&vendor=eclipse";
  // Adoptium's files live on GitHub, so when GitHub is down the first run needs Java from somewhere else
  private static final String CORRETTO = "https://corretto.aws/downloads/latest/amazon-corretto-25-x64-windows-jdk.zip";
  private static final String CORRETTO_SHA256 = "https://corretto.aws/downloads/latest_sha256/amazon-corretto-25-x64-windows-jdk.zip";
  private static final int ATTEMPTS = 4;
  private static final Pattern JAVA_VERSION = Pattern.compile("(?:openjdk|java) version \\\"([^\\\"]+)\\\"");
  private static final String[] NATIVES = {
      "org/lwjgl/lwjgl/lwjgl-platform/2.9.4+legacyfabric.15/lwjgl-platform-2.9.4+legacyfabric.15-natives-windows.jar",
      "net/java/jinput/jinput-platform/2.0.5/jinput-platform-2.0.5-natives-windows.jar"};

  public interface Progress { void update(String status, double fraction); }
  private Installer() {}

  public static void install(File root, Progress progress) throws Exception { install(root, progress, false); }
  /** Repair performs the same installation but hashes every file for which an authoritative hash is available. */
  public static void repair(File root, Progress progress) throws Exception { install(root, progress, true); }

  private static void install(File root, Progress p, boolean repair) throws Exception {
    root.mkdirs();
    ensureRuntime(root, p);
    ensureClasspath(root);
    ensureOptionalResource(root, "jvmargs.txt");
    p.update(repair ? "Repairing Minecraft 1.8.9 and mods" : "Installing Minecraft 1.8.9 and mods (first run only)", .01);
    Map<String,Object> version = versionJson();
    Map<String,String> known = new HashMap<String,String>();
    List<Job> jobs = new ArrayList<Job>();
    Map<String,Object> downloads = obj(version.get("downloads"));
    Map<String,Object> client = obj(downloads.get("client"));
    jobs.add(new Job(str(client.get("url")), new File(root, "libs/" + CLIENT), str(client.get("sha1")), true));
    known.put(CLIENT, str(client.get("sha1")));
    // only the libraries this client actually runs with (classpath.txt): Mojang's version json also lists its own LWJGL, which Legacy Fabric's replaces
    final Set<String> wanted = new HashSet<String>(lines(new File(root, "classpath.txt")));
    for (Object value : arr(version.get("libraries"))) {
      Map<String,Object> lib = obj(value);
      if (!applies(arr(lib.get("rules")))) continue;
      Map<String,Object> d = obj(lib.get("downloads"));
      Map<String,Object> artifact = obj(d.get("artifact"));
      if (wanted.contains(str(artifact.get("path")))) addArtifact(jobs, known, root, artifact);
    }
    final Set<String> paths = new HashSet<String>();
    for (Job j : jobs) paths.add(relative(new File(root, "libs"), j.file));
    for (String entry : lines(new File(root, "classpath.txt"))) {
      if (!paths.contains(entry)) jobs.add(new Job(null, new File(root, "libs/" + entry), null, true, entry));
    }
    runJobs(jobs, repair, p, .03, .62, "Downloading libraries");
    downloadNatives(root, repair, p);
    installAssetIndex(root, version, repair);
    syncMods(root, repair, p);
    String stamp = "{\n  \"minecraft\": \"1.8.9\",\n  \"launcher\": \"1.0\",\n  \"installedAt\": \"" +
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(new Date()) + "\"\n}\n";
    writeAtomic(new File(root, "installed.json"), stamp.getBytes(StandardCharsets.UTF_8));
    p.update(repair ? "Repair complete" : "Installation complete", 1);
  }

  /** Mods luka no longer ships; an install that still has one of these jars gets it removed before the game starts. */
  private static final String[] RETIRED_MOD_PREFIXES = { "modmenu-" };

  /** Deletes retired mod jars (the Fabric mod-list screen) from the game folder. Returns how many were removed. */
  public static int retireMods(File root) {
    int removed = 0;
    File[] jars = new File(root, "game/mods").listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".jar"));
    if (jars == null) return 0;
    for (File jar : jars) {
      String name = jar.getName().toLowerCase(Locale.ROOT);
      for (String prefix : RETIRED_MOD_PREFIXES) if (name.startsWith(prefix) && jar.delete()) removed++;
    }
    return removed;
  }

  public static boolean isInstalled(File root) {
    try {
      if (findUsableRuntime(root, new Settings(new File(root, "launcher.properties"))) == null) return false;
      File client = new File(root, "libs/" + CLIENT);
      if (!validJar(client)) return false;
      List<String> cp = lines(new File(root, "classpath.txt"));
      if (cp.isEmpty()) return false;
      for (String s : cp) if (!validJar(new File(root, "libs/" + s))) return false;
      for (String path : NATIVES) if (!validJar(new File(root, "natives/" + path.substring(path.lastIndexOf('/') + 1)))) return false;
      if (!new File(root, "jvmargs.txt").isFile() || !new File(root, "assets/indexes/1.8.json").isFile()) return false;
      return new File(root, "installed.json").isFile();
    } catch (Exception e) { return false; }
  }

  /** Selects an existing Java 25+ runtime, or installs the current Temurin 25 Windows x64 JRE. */
  public static File ensureRuntime(File root, Progress p) throws Exception {
    Settings settings = new Settings(new File(root, "launcher.properties"));
    File selected = findUsableRuntime(root, settings);
    if (selected != null) return selected;

    p.update("Downloading Java 25 (first run only)", 0);
    try {
      return installTemurin(root, p);
    } catch (Exception temurin) { // an answer it cannot read counts too, not only a failed connection
      try {
        String checksum = getText(CORRETTO_SHA256).trim();
        if (!checksum.matches("[0-9a-fA-F]{64}")) throw new IOException("Corretto returned no usable checksum");
        return installRuntime(root, p, CORRETTO, checksum, -1, "corretto-25.zip");
      } catch (IOException corretto) {
        temurin.addSuppressed(corretto);
        throw temurin;
      }
    }
  }

  private static File installTemurin(File root, Progress p) throws Exception {
    List<Object> assets = arr(Json.parse(getText(TEMURIN)));
    if (assets.isEmpty()) throw new IOException("Adoptium returned no Temurin 25 Windows x64 JRE");
    Map<String,Object> binary = obj(obj(assets.get(0)).get("binary"));
    Map<String,Object> pack = obj(binary.get("package"));
    String link = str(pack.get("link"));
    String checksum = str(pack.get("checksum"));
    String name = safeName(str(pack.get("name")));
    long size = longValue(pack.get("size"));
    if (link.length() == 0 || checksum.length() == 0 || name.length() == 0)
      throw new IOException("Adoptium response is missing the JRE package link, checksum, or name");
    return installRuntime(root, p, link, checksum, size, name);
  }

  private static File installRuntime(File root, Progress p, String link, String checksum, long size, String name) throws Exception {
    File archive = new File(root, name + ".part");
    try {
      downloadRuntime(link, archive, checksum, size, p);
      File staging = new File(root, "runtime.installing");
      deleteTree(staging);
      extractRuntimeZip(archive, staging);
      File java = new File(staging, "bin/java.exe");
      File javaw = new File(staging, "bin/javaw.exe");
      if (!javaw.isFile() || javaMajor(java) < 25)
        throw new IOException("Downloaded Java runtime is incomplete or is not Java 25+");
      File runtime = new File(root, "runtime");
      File backup = new File(root, "runtime.old");
      deleteTree(backup);
      if (runtime.exists()) Files.move(runtime.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
      try { Files.move(staging.toPath(), runtime.toPath(), StandardCopyOption.REPLACE_EXISTING); }
      catch (IOException e) {
        if (backup.exists() && !runtime.exists()) Files.move(backup.toPath(), runtime.toPath());
        throw e;
      }
      deleteTree(backup);
      File installed = new File(runtime, "bin/javaw.exe");
      if (javaMajor(new File(runtime, "bin/java.exe")) < 25) throw new IOException("Installed Java failed its version check");
      p.update("Java 25 ready", 1);
      return installed;
    } finally {
      Files.deleteIfExists(archive.toPath());
    }
  }

  static File findUsableRuntime(File root, Settings settings) {
    File bundled = new File(root, "runtime/bin/javaw.exe");
    if (javaMajor(bundled) >= 25) return bundled;
    boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    File own = new File(System.getProperty("java.home"), "bin/" + (windows ? "javaw.exe" : "java"));
    return javaMajor(own) >= 25 ? own : null;
  }

  /** luka runs on one exact Java build, named in the signed update list. Anything else, older or the backup from an outage, is replaced here. */
  private static void syncJava(File root, Map<String,Object> manifest, Progress p) throws Exception {
    if (System.getProperty("luka.gameDir") != null) return; // a Prism instance runs on Prism's Java
    Map<String,Object> java = obj(manifest.get("java"));
    String version = str(java.get("version")), implementor = str(java.get("implementor")), link = str(java.get("url"));
    String checksum = str(java.get("sha256")), file = str(java.get("file"));
    if (version.isEmpty() || implementor.isEmpty() || link.isEmpty() || checksum.isEmpty() || file.isEmpty()) return;
    File release = new File(root, "runtime/release");
    if (release.isFile()) {
      List<String> lines = Files.readAllLines(release.toPath(), StandardCharsets.UTF_8);
      if (lines.contains("JAVA_RUNTIME_VERSION=\"" + version + "\"") && lines.contains("IMPLEMENTOR=\"" + implementor + "\"")) return;
    }
    p.update("Updating Java", .66);
    try {
      installRuntime(root, p, link, checksum, longValue(java.get("size")), safeName(file));
    } catch (Exception failed) {
      // a slow or broken download must not lock out someone who already has a working Java
      if (findUsableRuntime(root, new Settings(new File(root, "launcher.properties"))) == null) throw failed;
    }
  }

  static int parseJavaMajor(String output) {
    Matcher matcher = JAVA_VERSION.matcher(output == null ? "" : output);
    if (!matcher.find()) return -1;
    String version = matcher.group(1);
    try {
      if (version.startsWith("1.")) {
        Matcher legacy = Pattern.compile("^1\\.(\\d+)").matcher(version);
        return legacy.find() ? Integer.parseInt(legacy.group(1)) : -1;
      }
      Matcher modern = Pattern.compile("^(\\d+)").matcher(version);
      return modern.find() ? Integer.parseInt(modern.group(1)) : -1;
    } catch (NumberFormatException e) { return -1; }
  }

  static int javaMajor(File executable) {
    if (executable == null || !executable.isFile()) return -1;
    try {
      Process process = new ProcessBuilder(executable.getAbsolutePath(), "-version").redirectErrorStream(true).start();
      String output;
      try (InputStream in = process.getInputStream()) { output = read(in); }
      if (!process.waitFor(15, TimeUnit.SECONDS)) { process.destroyForcibly(); return -1; }
      return parseJavaMajor(output);
    } catch (Exception e) { return -1; }
  }

  static void extractRuntimeZip(File archive, File target) throws IOException {
    target.mkdirs();
    String rootPath = target.getCanonicalPath() + File.separator;
    try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        String name = entry.getName().replace('\\', '/');
        int slash = name.indexOf('/');
        if (slash < 0) continue; // the archive's single top-level directory
        name = name.substring(slash + 1);
        if (name.length() == 0) continue;
        File out = new File(target, name);
        String outPath = out.getCanonicalPath();
        if (!outPath.startsWith(rootPath)) throw new IOException("Unsafe path in Java archive: " + entry.getName());
        if (entry.isDirectory()) { out.mkdirs(); continue; }
        File parent = out.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not create " + parent);
        try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(out))) {
          byte[] buffer = new byte[32768]; int n;
          while ((n = zip.read(buffer)) > 0) stream.write(buffer, 0, n);
        }
      }
    }
  }

  private static void downloadRuntime(String url, File target, String checksum, long expectedSize, Progress p) throws IOException {
    IOException last = null;
    for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
      try { fetchRuntime(url, target, checksum, expectedSize, p); return; }
      catch (IOException e) { last = e; if (attempt + 1 < ATTEMPTS) backoff(attempt); }
    }
    throw last;
  }

  private static void backoff(int attempt) throws IOException {
    try { Thread.sleep(1000L << attempt); }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Interrupted", e); }
  }

  private static void fetchRuntime(String url, File target, String checksum, long expectedSize, Progress p) throws IOException {
    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
    c.setConnectTimeout(15000); c.setReadTimeout(60000); c.setInstanceFollowRedirects(true);
    c.setRequestProperty("User-Agent", "luka-launcher/1.0");
    int code = c.getResponseCode();
    if (code != 200) throw new IOException("HTTP " + code + " while downloading Java 25");
    long total = expectedSize > 0 ? expectedSize : c.getContentLengthLong();
    target.getParentFile().mkdirs();
    long done = 0;
    try (InputStream in = c.getInputStream(); OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
      byte[] buffer = new byte[32768]; int n;
      while ((n = in.read(buffer)) > 0) {
        out.write(buffer, 0, n); done += n;
        p.update("Downloading Java 25 (first run only)", total > 0 ? Math.min(1, (double) done / total) : 0);
      }
    }
    if (expectedSize > 0 && target.length() != expectedSize) throw new IOException("Java download size mismatch");
    if (!checksum.equalsIgnoreCase(hash(target, "SHA-256"))) throw new IOException("Java download SHA-256 mismatch");
  }

  private static long longValue(Object value) {
    return value instanceof Number ? ((Number) value).longValue() : -1;
  }

  private static void deleteTree(File file) throws IOException {
    if (!file.exists()) return;
    File[] children = file.listFiles();
    if (children != null) for (File child : children) deleteTree(child);
    if (!file.delete()) throw new IOException("Could not remove " + file);
  }

  private static boolean validJar(File file) {
    if (!file.isFile() || file.length() == 0) return false;
    try (ZipFile zip = new ZipFile(file)) { return zip.size() > 0; }
    catch (IOException e) { return false; }
  }

  private static Map<String,Object> versionJson() throws Exception {
    Map<String,Object> manifest = obj(Json.parse(getText(MANIFEST)));
    for (Object v : arr(manifest.get("versions"))) {
      Map<String,Object> version = obj(v);
      if ("1.8.9".equals(str(version.get("id")))) return obj(Json.parse(getText(str(version.get("url")))));
    }
    throw new IOException("Minecraft 1.8.9 is absent from Mojang's version manifest");
  }

  private static void addArtifact(List<Job> jobs, Map<String,String> known, File root, Map<String,Object> a) {
    String path = str(a.get("path")), url = str(a.get("url")), sha1 = str(a.get("sha1"));
    if (path.length() > 0 && url.length() > 0) {
      jobs.add(new Job(url, new File(root, "libs/" + path), sha1, true));
      known.put(path, sha1);
    }
  }

  private static boolean applies(List<Object> rules) {
    if (rules.isEmpty()) return true;
    boolean allowed = false;
    for (Object item : rules) {
      Map<String,Object> rule = obj(item), os = obj(rule.get("os"));
      String name = str(os.get("name"));
      String arch = str(os.get("arch")), version = str(os.get("version"));
      Map<String,Object> features = obj(rule.get("features"));
      String actualArch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
      boolean archMatches = arch.length() == 0 || arch.equalsIgnoreCase(actualArch)
          || ("x86".equals(arch) && (actualArch.equals("x86") || actualArch.equals("i386")));
      boolean versionMatches;
      try { versionMatches = version.length() == 0 || System.getProperty("os.version", "").matches(version); }
      catch (java.util.regex.PatternSyntaxException e) { versionMatches = false; }
      boolean featuresMatch = true;
      for (Object desired : features.values()) if (Boolean.TRUE.equals(desired)) featuresMatch = false; // launcher enables no optional Mojang features
      boolean matches = (name.length() == 0 || "windows".equals(name)) && archMatches && versionMatches && featuresMatch;
      if (matches) allowed = "allow".equals(str(rule.get("action")));
    }
    return allowed;
  }

  private static void runJobs(List<Job> jobs, boolean repair, Progress p, double from, double to, String label) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(6);
    List<Throwable> errors = Collections.synchronizedList(new ArrayList<Throwable>());
    AtomicInteger done = new AtomicInteger();
    for (final Job job : jobs) pool.submit(() -> {
      try { fetchJob(job, repair); } catch (Throwable e) { errors.add(e); }
      int n = done.incrementAndGet();
      p.update(label + " " + n + "/" + jobs.size(), from + (to - from) * n / Math.max(1, jobs.size()));
    });
    pool.shutdown();
    if (!pool.awaitTermination(30, TimeUnit.MINUTES)) pool.shutdownNow();
    if (!errors.isEmpty()) throw new IOException(errors.size() + " download(s) failed: " + errors.get(0).getMessage(), errors.get(0));
  }

  private static void fetchJob(Job j, boolean repair) throws Exception {
    if (j.mavenPath != null) {
      IOException last = null;
      for (String repo : reposFor(j.mavenPath)) try {
        String url = repo + j.mavenPath;
        String sha = optionalText(url + ".sha1");
        String expected = sha == null ? "" : sha.trim().split("\\s+")[0];
        if (j.file.isFile() && (!repair || expected.length() == 0 || expected.equalsIgnoreCase(hash(j.file, "SHA-1")))) {
          try (ZipFile ignored = new ZipFile(j.file)) { if (ignored.size() > 0) return; }
        }
        download(url, j.file, expected, true);
        return;
      } catch (IOException e) { last = e; }
      throw new IOException("No repository contained " + j.mavenPath, last);
    }
    if (j.file.isFile() && (!repair || j.sha1.length() == 0 || j.sha1.equalsIgnoreCase(hash(j.file, "SHA-1")))) return;
    download(j.url, j.file, j.sha1, j.jar);
  }

  /** Asks the repository that has the file first; going through the list in order cost most of the install's library time. */
  private static List<String> reposFor(String path) {
    List<String> order = new ArrayList<String>(Arrays.asList(REPOS));
    String best = path.startsWith("net/fabricmc/") ? "https://maven.fabricmc.net/"
        : path.startsWith("net/ornithemc/") ? "https://maven.ornithemc.net/releases/"
        : path.contains("legacyfabric") ? "https://maven.legacyfabric.net/releases/"
        : path.startsWith("com/mojang/") || path.startsWith("net/java/") || path.startsWith("tv/twitch/") ? "https://libraries.minecraft.net/"
        : "https://repo1.maven.org/maven2/";
    order.remove(best);
    order.add(0, best);
    return order;
  }

  private static void downloadNatives(File root, boolean repair, Progress p) throws Exception {
    List<Job> jobs = new ArrayList<Job>();
    for (String path : NATIVES) jobs.add(new Job(null, new File(root, "natives/" + path.substring(path.lastIndexOf('/') + 1)), null, true, path));
    runJobs(jobs, repair, p, .62, .69, "Downloading native libraries");
  }

  private static void installAssetIndex(File root, Map<String,Object> version, boolean repair) throws Exception {
    Map<String,Object> index = obj(version.get("assetIndex"));
    String id = str(index.get("id"));
    File target = new File(root, "assets/indexes/" + id + ".json");
    String sha1 = str(index.get("sha1"));
    if (!target.isFile() || (repair && sha1.length() > 0 && !sha1.equalsIgnoreCase(hash(target, "SHA-1"))))
      download(str(index.get("url")), target, sha1, false);
  }

  /** The list of what to download, from client.luka.wtf. The last good copy is kept so an installed game still starts offline. */
  private static Map<String,Object> manifest(File root) throws Exception {
    Settings settings = new Settings(new File(root, "launcher.properties"));
    File cached = new File(root, "manifest.json");
    try {
      String address = settings.updateUrl();
      URL url = new URL(address);
      boolean local = "127.0.0.1".equals(url.getHost()) || "localhost".equals(url.getHost());
      if (!"https".equalsIgnoreCase(url.getProtocol()) && !local) throw new IOException("The download list must use HTTPS");
      String text = getText(address);
      Map<String,Object> parsed = obj(Json.parse(Manifests.open(text, cached)));
      writeAtomic(cached, text.getBytes(StandardCharsets.UTF_8));
      return parsed;
    } catch (Exception offline) {
      if (!cached.isFile()) throw new IOException("Could not reach the luka download server: " + offline.getMessage(), offline);
      // a short outage must not lock everyone out, but luka has to have checked for updates recently to start
      if (System.currentTimeMillis() - cached.lastModified() > OFFLINE_GRACE_MS)
        throw new IOException("luka could not check for updates. Connect to the internet and press PLAY again.", offline);
      return obj(Json.parse(Manifests.open(new String(Files.readAllBytes(cached.toPath()), StandardCharsets.UTF_8), null)));
    }
  }

  private static final long OFFLINE_GRACE_MS = 24L * 60 * 60 * 1000;

  private static final class ModFile {
    final String file, url, mirror, sha512; final File target;
    ModFile(Map<String,Object> entry, File dir) throws IOException {
      file = safeName(str(entry.get("file"))); url = str(entry.get("url")); mirror = str(entry.get("mirror")); sha512 = str(entry.get("sha512"));
      if (file.length() == 0 || url.length() == 0 || sha512.length() == 0) throw new IOException("Invalid download entry (file, url and sha512 are required)");
      target = new File(dir, file);
    }
  }

  /** Brings every mod to what the manifest says for the chosen renderer. Called on every Play, so updates arrive by themselves. */
  public static void syncMods(File root, boolean repair, Progress p) throws Exception {
    Settings settings = new Settings(new File(root, "launcher.properties"));
    Map<String,Object> manifest = manifest(root);
    syncJava(root, manifest, p);
    File mods = new File(gameDir(root), "mods");
    List<ModFile> wanted = new ArrayList<ModFile>();
    for (Object o : arr(manifest.get("mods"))) wanted.add(new ModFile(obj(o), mods));
    for (Object o : arr(manifest.get("dependencies"))) wanted.add(new ModFile(obj(o), mods));
    Map<String,Object> renderers = obj(manifest.get("renderers"));
    File rendererDir = new File(root, "renderers/" + settings.renderer());
    for (Object o : arr(renderers.get(settings.renderer()))) wanted.add(new ModFile(obj(o), rendererDir));
    removeRetired(root, mods, rendererDir, wanted);
    // a renderer jar left in the shared folder would load on both paths
    for (Map.Entry<String,Object> set : renderers.entrySet())
      for (Object o : arr(set.getValue())) Files.deleteIfExists(new File(mods, safeName(str(obj(o).get("file")))).toPath());
    ExecutorService pool = Executors.newFixedThreadPool(8);
    List<Throwable> errors = Collections.synchronizedList(new ArrayList<Throwable>());
    AtomicInteger done = new AtomicInteger();
    for (final ModFile mod : wanted) pool.submit(() -> {
      try {
        if (!mod.target.isFile() || !mod.sha512.equalsIgnoreCase(hash(mod.target, "SHA-512"))) fetchMod(mod);
      } catch (Throwable e) { errors.add(e); }
      int n = done.incrementAndGet();
      p.update("Downloading mods " + n + "/" + wanted.size(), .70 + .26 * n / Math.max(1, wanted.size()));
    });
    pool.shutdown();
    if (!pool.awaitTermination(30, TimeUnit.MINUTES)) pool.shutdownNow();
    if (!errors.isEmpty()) throw new IOException(errors.size() + " download(s) failed: " + errors.get(0).getMessage(), errors.get(0));
    installDefaults(root, manifest);
    writeFirstRunOptions(root);
  }

  /** Vanilla starts a new player at 120 FPS with VSync on; this client should not. Never touches an options file that already exists. */
  private static void writeFirstRunOptions(File root) throws IOException {
    File options = new File(gameDir(root), "options.txt");
    if (!options.exists()) writeAtomic(options, String.join(System.lineSeparator(), "maxFps:260", "enableVsync:false", "").getBytes(StandardCharsets.UTF_8));
  }

  private static void fetchMod(ModFile mod) throws IOException {
    try { download(mod.url, mod.target, mod.sha512, true, "SHA-512"); }
    catch (IOException main) {
      if (mod.mirror.length() == 0) throw main;
      download(mod.mirror, mod.target, mod.sha512, true, "SHA-512");
    }
  }

  /** Deletes what an earlier release installed and this one no longer lists. Mods the player added themselves are left alone. */
  private static void removeRetired(File root, File mods, File rendererDir, List<ModFile> wanted) throws IOException {
    Set<String> names = new HashSet<String>();
    StringBuilder managed = new StringBuilder();
    for (ModFile mod : wanted) {
      names.add(mod.file);
      if (mod.target.getParentFile().equals(mods)) managed.append(mod.file).append('\n');
    }
    File list = new File(root, "managed-mods.txt");
    for (String old : lines(list)) if (!names.contains(old)) Files.deleteIfExists(new File(mods, safeName(old)).toPath());
    writeAtomic(list, managed.toString().getBytes(StandardCharsets.UTF_8));
    File[] installed = rendererDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".jar"));
    if (installed != null) for (File jar : installed) if (!names.contains(jar.getName())) Files.deleteIfExists(jar.toPath());
  }

  /** The default settings zip fills in only what the player does not already have. */
  private static void installDefaults(File root, Map<String,Object> manifest) throws IOException {
    Map<String,Object> defaults = obj(manifest.get("defaults"));
    String url = str(defaults.get("url")), sha = str(defaults.get("sha512"));
    if (url.length() == 0 || new File(gameDir(root), "config/.luka-defaults").isFile()) return;
    File zip = new File(root, "defaults.zip.download");
    download(url, zip, sha, true, "SHA-512");
    File config = new File(gameDir(root), "config");
    String base = config.getCanonicalPath() + File.separator;
    try (ZipInputStream in = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
      for (ZipEntry entry; (entry = in.getNextEntry()) != null; ) {
        File out = new File(config, entry.getName());
        if (!out.getCanonicalPath().startsWith(base)) throw new IOException("Unsafe path in defaults: " + entry.getName());
        if (entry.isDirectory()) { out.mkdirs(); continue; }
        if (out.exists()) continue;
        out.getParentFile().mkdirs();
        Files.copy(in, out.toPath());
      }
    }
    Files.deleteIfExists(zip.toPath());
    config.mkdirs();
    new File(config, ".luka-defaults").createNewFile();
  }

  private static void ensureClasspath(File root) throws IOException {
    File target = new File(root, "classpath.txt"); if (target.isFile()) return;
    File external = new File(distributionDir(root), "classpath.txt");
    if (external.isFile()) { copy(external, target, false); return; }
    try (InputStream in = Installer.class.getResourceAsStream("/classpath.txt")) {
      if (in == null) throw new IOException("Missing embedded classpath.txt");
      writeAtomic(target, bytes(in));
    }
  }

  private static void ensureOptionalResource(File root, String name) throws IOException {
    File target = new File(root, name); if (target.exists()) return;
    try (InputStream in = Installer.class.getResourceAsStream("/" + name)) {
      if (in != null) writeAtomic(target, bytes(in));
    }
  }

  private static File distributionDir(File root) { return root.getParentFile() == null ? root : root.getParentFile(); }
  private static String safeName(String name) throws IOException {
    if (!name.equals(new File(name).getName()) || name.contains("..")) throw new IOException("Unsafe dependency filename: " + name);
    return name;
  }
  private static String relative(File base, File file) { return base.toPath().relativize(file.toPath()).toString().replace('\\', '/'); }
  private static List<String> lines(File file) throws IOException {
    List<String> out = new ArrayList<String>(); if (!file.isFile()) return out;
    for (String s : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) { s=s.trim(); if (!s.isEmpty() && !s.startsWith("#")) out.add(s.replace('\\','/')); }
    return out;
  }

  private static void download(String url, File target, String hash, boolean jar) throws IOException { download(url,target,hash,jar,"SHA-1"); }
  private static void download(String url, File target, String expected, boolean jar, String algorithm) throws IOException {
    IOException last = null;
    for (int attempt=0; attempt<ATTEMPTS; attempt++) try {
      target.getParentFile().mkdirs(); File part = new File(target.getPath()+".part");
      HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
      c.setConnectTimeout(15000); c.setReadTimeout(45000); c.setRequestProperty("User-Agent","luka-launcher/1.0");
      c.setInstanceFollowRedirects(true); int code=c.getResponseCode(); if (code==404 || code==403 || code==410) throw new java.io.FileNotFoundException("HTTP "+code+" from "+url);
      if (code != 200) throw new IOException("HTTP "+code+" from "+url);
      try(InputStream in=c.getInputStream(); OutputStream out=new FileOutputStream(part)){ byte[] b=new byte[32768]; int n; while((n=in.read(b))>=0) if(n>0) out.write(b,0,n); }
      if (part.length()==0) throw new IOException("Empty download from "+url);
      if (expected!=null && !expected.isEmpty() && !expected.equalsIgnoreCase(hash(part,algorithm))) throw new IOException("Hash mismatch for "+target.getName());
      if (jar) try(ZipFile ignored=new ZipFile(part)) { if(ignored.size()==0) throw new IOException("Empty jar "+target.getName()); }
      Files.move(part.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING); return;
    } catch(java.io.FileNotFoundException gone) { throw gone; // the next repository may have it, and asking this one again will not help
    } catch(IOException e) { last=e; if (attempt+1<ATTEMPTS) backoff(attempt); }
    throw last;
  }

  private static String getText(String url) throws IOException { return readUrl(url, false); }
  private static String optionalText(String url) { try { return readUrl(url, true); } catch(IOException e) { return null; } }
  private static String readUrl(String url, boolean missingOkay) throws IOException {
    IOException last=null; for(int a=0;a<ATTEMPTS;a++) try {
      HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setRequestProperty("User-Agent","luka-launcher/1.0");
      int code=c.getResponseCode(); if(missingOkay && (code==404 || code==403)) return null; if(code!=200) throw new IOException("HTTP "+code+" from "+url); return read(c.getInputStream());
    } catch(IOException e){last=e; if(a+1<ATTEMPTS) backoff(a);}
    throw last;
  }
  private static String hash(File f,String algorithm) throws IOException { try { MessageDigest md=MessageDigest.getInstance(algorithm); try(InputStream in=new FileInputStream(f)){byte[] b=new byte[32768];int n;while((n=in.read(b))>0)md.update(b,0,n);} StringBuilder s=new StringBuilder();for(byte b:md.digest())s.append(String.format("%02x",b&255));return s.toString(); } catch (java.security.GeneralSecurityException e) { throw new IOException(e); } }
  private static void copy(File a,File b,boolean replace)throws IOException{b.getParentFile().mkdirs();if(replace)Files.copy(a.toPath(),b.toPath(),StandardCopyOption.REPLACE_EXISTING);else Files.copy(a.toPath(),b.toPath());}
  private static void writeAtomic(File f,byte[] b)throws IOException{f.getParentFile().mkdirs();File p=new File(f.getPath()+".part");Files.write(p.toPath(),b);Files.move(p.toPath(),f.toPath(),StandardCopyOption.REPLACE_EXISTING);}
  private static byte[] bytes(InputStream in)throws IOException{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[]b=new byte[8192];int n;while((n=in.read(b))>0)o.write(b,0,n);return o.toByteArray();}
  private static String read(InputStream in)throws IOException{return new String(bytes(in),StandardCharsets.UTF_8);}
  private static String str(Object o){return o instanceof String?(String)o:"";}
  @SuppressWarnings("unchecked") private static Map<String,Object> obj(Object o){return o instanceof Map?(Map<String,Object>)o:Collections.<String,Object>emptyMap();}
  @SuppressWarnings("unchecked") private static List<Object> arr(Object o){return o instanceof List?(List<Object>)o:Collections.<Object>emptyList();}

  private static final class Job {
    final String url; final File file; final String sha1; final boolean jar; final String mavenPath;
    Job(String u,File f,String s,boolean j){this(u,f,s,j,null);} Job(String u,File f,String s,boolean j,String m){url=u;file=f;sha1=s==null?"":s;jar=j;mavenPath=m;}
  }

  /** Minimal standards-compliant JSON reader for Mojang metadata and dependencies.json. */
  /** Prism instances keep the game in their own folder; the launcher keeps it next to its files. */
  private static File gameDir(File root) {
    String override = System.getProperty("luka.gameDir");
    return override != null ? new File(override) : new File(root, "game");
  }

  static final class Json {
    final String s; int p; Json(String s){this.s=s;}
    static Object parse(String s)throws IOException{Json j=new Json(s);Object v=j.value();j.ws();if(j.p!=s.length())throw j.bad();return v;}
    Object value()throws IOException{ws();if(p>=s.length())throw bad();char c=s.charAt(p);if(c=='{')return object();if(c=='[')return array();if(c=='\"')return string();if(c=='t'&&take("true"))return Boolean.TRUE;if(c=='f'&&take("false"))return Boolean.FALSE;if(c=='n'&&take("null"))return null;return number();}
    Map<String,Object> object()throws IOException{Map<String,Object>m=new LinkedHashMap<String,Object>();p++;ws();if(ch('}'))return m;do{ws();String k=string();ws();need(':');m.put(k,value());ws();}while(ch(','));need('}');return m;}
    List<Object> array()throws IOException{List<Object>a=new ArrayList<Object>();p++;ws();if(ch(']'))return a;do{a.add(value());ws();}while(ch(','));need(']');return a;}
    String string()throws IOException{need('\"');StringBuilder b=new StringBuilder();while(p<s.length()){char c=s.charAt(p++);if(c=='\"')return b.toString();if(c=='\\'){if(p>=s.length())throw bad();char e=s.charAt(p++);if(e=='u'){if(p+4>s.length())throw bad();b.append((char)Integer.parseInt(s.substring(p,p+4),16));p+=4;}else{int i="\"\\/bfnrt".indexOf(e);if(i<0)throw bad();b.append("\"\\/\b\f\n\r\t".charAt(i));}}else b.append(c);}throw bad();}
    Object number()throws IOException{int a=p;while(p<s.length()&&"-+0123456789.eE".indexOf(s.charAt(p))>=0)p++;try{return Double.valueOf(s.substring(a,p));}catch(Exception e){throw bad();}}
    boolean take(String x){if(s.startsWith(x,p)){p+=x.length();return true;}return false;}boolean ch(char c){if(p<s.length()&&s.charAt(p)==c){p++;return true;}return false;}void need(char c)throws IOException{if(!ch(c))throw bad();}void ws(){while(p<s.length()&&Character.isWhitespace(s.charAt(p)))p++;}IOException bad(){return new IOException("Invalid JSON near character "+p);}
  }
}
