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
  private static String temurin() {
    return "https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jre&os=" + Platform.vendorOs() + "&vendor=eclipse";
  }
  // Adoptium's files live on GitHub, so when GitHub is down the first run needs Java from somewhere else
  private static String correttoFile() { return "amazon-corretto-25-x64-" + Platform.vendorOs() + "-jdk." + Platform.runtimeArchive(); }
  private static String corretto() { return "https://corretto.aws/downloads/latest/" + correttoFile(); }
  private static String correttoSha256() { return "https://corretto.aws/downloads/latest_sha256/" + correttoFile(); }
  private static final int ATTEMPTS = 4;
  private static final Pattern JAVA_VERSION = Pattern.compile("(?:openjdk|java) version \\\"([^\\\"]+)\\\"");
  /** LWJGL 2's and jinput's native libraries for this system. */
  static String[] natives() {
    String c = Platform.nativesClassifier();
    return new String[] {
        "org/lwjgl/lwjgl/lwjgl-platform/2.9.4+legacyfabric.15/lwjgl-platform-2.9.4+legacyfabric.15-" + c + ".jar",
        "net/java/jinput/jinput-platform/2.0.5/jinput-platform-2.0.5-" + c + ".jar"};
  }

  public interface Progress { void update(String status, double fraction); }
  private Installer() {}

  public static void install(File root, Progress progress) throws Exception { install(root, GameVersion.V1_8_9, progress, false); }
  public static void install(File root, GameVersion v, Progress progress) throws Exception { install(root, v, progress, false); }
  /** Repair performs the same installation but hashes every file for which an authoritative hash is available. */
  public static void repair(File root, Progress progress) throws Exception { install(root, GameVersion.V1_8_9, progress, true); }
  public static void repair(File root, GameVersion v, Progress progress) throws Exception { install(root, v, progress, true); }

  private static void install(File root, GameVersion v, Progress p, boolean repair) throws Exception {
    File home = v.home(root);
    root.mkdirs();
    home.mkdirs();
    ensureRuntime(root, p);
    // a version the update list has no mods for would be a plain game: say so before downloading it
    if (!v.primary()) section(manifest(root), v);
    ensureClasspath(root, v);
    ensureOptionalResource(home, "jvmargs.txt", v.jvmargsResource);
    p.update(repair ? "Repairing Minecraft " + v.id + " and mods" : "Installing Minecraft " + v.id + " and mods (first run only)", .01);
    Map<String,Object> version = versionJson(v.id);
    Map<String,String> known = new HashMap<String,String>();
    List<Job> jobs = new ArrayList<Job>();
    Map<String,Object> downloads = obj(version.get("downloads"));
    Map<String,Object> client = obj(downloads.get("client"));
    jobs.add(new Job(str(client.get("url")), new File(home, "libs/" + v.clientPath()), str(client.get("sha1")), true));
    known.put(v.clientPath(), str(client.get("sha1")));
    // only the libraries this client actually runs with (classpath.txt): Mojang's version json also lists its own LWJGL, which Legacy Fabric's replaces
    final Set<String> wanted = new HashSet<String>(lines(new File(home, "classpath.txt")));
    for (Object value : arr(version.get("libraries"))) {
      Map<String,Object> lib = obj(value);
      if (!applies(arr(lib.get("rules")))) continue;
      Map<String,Object> d = obj(lib.get("downloads"));
      Map<String,Object> artifact = obj(d.get("artifact"));
      if (wanted.contains(str(artifact.get("path")))) addArtifact(jobs, known, home, artifact);
    }
    final Set<String> paths = new HashSet<String>();
    for (Job j : jobs) paths.add(relative(new File(home, "libs"), j.file));
    for (String entry : lines(new File(home, "classpath.txt"))) {
      if (!paths.contains(entry)) jobs.add(new Job(null, new File(home, "libs/" + entry), null, true, entry));
    }
    runJobs(jobs, repair, p, .03, .62, "Downloading libraries");
    downloadNatives(home, repair, p);
    installAssetIndex(root, version, repair);
    syncMods(root, v, repair, p);
    String stamp = "{\n  \"minecraft\": \"" + v.id + "\",\n  \"launcher\": \"1.0\",\n  \"installedAt\": \"" +
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(new Date()) + "\"\n}\n";
    writeAtomic(new File(home, "installed.json"), stamp.getBytes(StandardCharsets.UTF_8));
    p.update(repair ? "Repair complete" : "Installation complete", 1);
  }

  /** The part of the update list for one version: the list itself for 1.8.9, its entry under "versions" for the others. */
  static Map<String,Object> section(Map<String,Object> manifest, GameVersion v) throws IOException {
    if (v.primary()) return manifest;
    Map<String,Object> set = obj(obj(manifest.get("versions")).get(v.id));
    if (set.isEmpty()) throw new IOException("luka for Minecraft " + v.id + " is not out yet. Pick 1.8.9 to play.");
    return set;
  }

  /** Mods luka no longer ships; an install that still has one of these jars gets it removed before the game starts. */
  private static final String[] RETIRED_MOD_PREFIXES = { "modmenu-" };

  /** Deletes retired mod jars (the Fabric mod-list screen) from the game folder. Returns how many were removed. */
  public static int retireMods(File root) { return retireMods(root, GameVersion.V1_8_9); }

  public static int retireMods(File root, GameVersion v) {
    int removed = 0;
    File[] jars = new File(v.home(root), "game/mods").listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".jar"));
    if (jars == null) return 0;
    for (File jar : jars) {
      String name = jar.getName().toLowerCase(Locale.ROOT);
      for (String prefix : RETIRED_MOD_PREFIXES) if (name.startsWith(prefix) && jar.delete()) removed++;
    }
    return removed;
  }

  public static boolean isInstalled(File root) { return isInstalled(root, GameVersion.V1_8_9); }

  public static boolean isInstalled(File root, GameVersion v) {
    try {
      if (findUsableRuntime(root, new Settings(new File(root, "launcher.properties"))) == null) return false;
      File home = v.home(root);
      File client = new File(home, "libs/" + v.clientPath());
      if (!validJar(client)) return false;
      List<String> cp = lines(new File(home, "classpath.txt"));
      if (cp.isEmpty()) return false;
      for (String s : cp) if (!validJar(new File(home, "libs/" + s))) return false;
      for (String path : natives()) if (!validJar(new File(home, "natives/" + path.substring(path.lastIndexOf('/') + 1)))) return false;
      if (!new File(home, "jvmargs.txt").isFile() || !new File(root, "assets/indexes/" + v.assetIndex + ".json").isFile()) return false;
      return new File(home, "installed.json").isFile();
    } catch (Exception e) { return false; }
  }

  /** Selects an existing Java 25+ runtime, or installs the current Temurin 25 x64 JRE for this system. */
  public static File ensureRuntime(File root, Progress p) throws Exception {
    Settings settings = new Settings(new File(root, "launcher.properties"));
    File selected = findUsableRuntime(root, settings);
    if (selected != null) return selected;

    p.update("Downloading Java 25 (first run only)", 0);
    try {
      return installTemurin(root, p);
    } catch (Exception temurin) { // an answer it cannot read counts too, not only a failed connection
      try {
        String checksum = getText(correttoSha256()).trim();
        if (!checksum.matches("[0-9a-fA-F]{64}")) throw new IOException("Corretto returned no usable checksum");
        return installRuntime(root, p, corretto(), checksum, -1, "corretto-25." + Platform.runtimeArchive());
      } catch (IOException corretto) {
        temurin.addSuppressed(corretto);
        throw temurin;
      }
    }
  }

  private static File installTemurin(File root, Progress p) throws Exception {
    List<Object> assets = arr(Json.parse(getText(temurin())));
    if (assets.isEmpty()) throw new IOException("Adoptium returned no Temurin 25 x64 JRE for " + Platform.vendorOs());
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
      if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) extractRuntimeTarGz(archive, staging);
      else extractRuntimeZip(archive, staging);
      File java = Platform.javaCli(staging);
      File javaw = Platform.javaw(staging);
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
      File installed = Platform.javaw(runtime);
      if (javaMajor(Platform.javaCli(runtime)) < 25) throw new IOException("Installed Java failed its version check");
      p.update("Java 25 ready", 1);
      return installed;
    } finally {
      Files.deleteIfExists(archive.toPath());
    }
  }

  static File findUsableRuntime(File root, Settings settings) {
    File bundled = Platform.javaw(new File(root, "runtime"));
    if (javaMajor(bundled) >= 25) return bundled;
    File own = Platform.javaw(new File(System.getProperty("java.home")));
    return javaMajor(own) >= 25 ? own : null;
  }

  /** luka runs on one exact Java build, named in the signed update list. Anything else, older or the backup from an outage, is replaced here. */
  private static void syncJava(File root, Map<String,Object> manifest, Progress p) throws Exception {
    if (System.getProperty("luka.gameDir") != null) return; // a Prism instance runs on Prism's Java
    Map<String,Object> java = javaEntry(manifest);
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

  /**
   * The Java build for this system. "javaByOs" holds one per system; "java" is the Windows one, the only key
   * launchers before Linux support read, so it stays as it was. A system with no entry keeps the Java it has.
   */
  static Map<String,Object> javaEntry(Map<String,Object> manifest) {
    Map<String,Object> mine = obj(obj(manifest.get("javaByOs")).get(Platform.id()));
    if (!mine.isEmpty()) return mine;
    return Platform.windows() ? obj(manifest.get("java")) : Collections.<String,Object>emptyMap();
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

  /**
   * Linux runtimes come as tar.gz. Plain files, folders and symbolic links (the licence folder has some) are
   * unpacked; the executable bits are kept, so bin/java and lib/jspawnhelper can run. Like the zip, the archive's
   * single top-level folder is dropped and nothing may land outside the target.
   */
  static void extractRuntimeTarGz(File archive, File target) throws IOException {
    target.mkdirs();
    String rootPath = target.getCanonicalPath() + File.separator;
    try (InputStream in = new java.util.zip.GZIPInputStream(new BufferedInputStream(new FileInputStream(archive)), 65536)) {
      byte[] header = new byte[512];
      String longName = null, paxPath = null;
      while (true) {
        if (!readFully(in, header)) throw new IOException("Java archive ends early");
        boolean empty = true;
        for (byte b : header) if (b != 0) { empty = false; break; }
        if (empty) break;
        String name = tarString(header, 0, 100);
        String prefix = tarString(header, 345, 155);
        if (!prefix.isEmpty() && "ustar".equals(tarString(header, 257, 5))) name = prefix + "/" + name;
        int mode = (int) tarNumber(header, 100, 8);
        long size = tarNumber(header, 124, 12);
        char type = (char) header[156];
        String link = tarString(header, 157, 100);
        if (type == 'L' || type == 'x' || type == 'g') { // a long name or a pax header for the next entry
          byte[] data = readBytes(in, size);
          skip(in, pad(size));
          if (type == 'L') longName = new String(data, StandardCharsets.UTF_8).replace("\0", "").trim();
          else if (type == 'x') paxPath = paxValue(data, "path");
          continue;
        }
        if (longName != null) { name = longName; longName = null; }
        if (paxPath != null) { name = paxPath; paxPath = null; }
        name = name.replace('\\', '/');
        while (name.startsWith("./")) name = name.substring(2);
        int slash = name.indexOf('/');
        String inner = slash < 0 ? "" : name.substring(slash + 1);
        if (inner.endsWith("/")) inner = inner.substring(0, inner.length() - 1);
        boolean file = type == '0' || type == 0 || type == '7';
        if (inner.isEmpty()) { skip(in, (file ? size : 0) + (file ? pad(size) : 0)); continue; }
        File out = new File(target, inner);
        String outPath = out.getCanonicalPath();
        if (!outPath.startsWith(rootPath)) throw new IOException("Unsafe path in Java archive: " + name);
        if (type == '5') { out.mkdirs(); continue; }
        File parent = out.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not create " + parent);
        if (type == '2') { // symbolic link: never created; a copy of the file it names, when that file was unpacked
          // already and lies inside the runtime (in Temurin's archives only licence texts are links)
          if (link.isEmpty() || link.startsWith("/") || link.startsWith("\\") || link.contains(":")) continue;
          File resolved = new File(parent, link);
          if (resolved.getCanonicalPath().startsWith(rootPath) && resolved.isFile())
            Files.copy(resolved.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
          continue;
        }
        if (type == '1') { // hard link to a file unpacked before, named from the archive root
          if (link.isEmpty() || link.startsWith("/") || link.contains("..") || link.contains(":")) continue;
          File source = new File(target, link.indexOf('/') < 0 ? link : link.substring(link.indexOf('/') + 1));
          if (source.getCanonicalPath().startsWith(rootPath) && source.isFile())
            Files.copy(source.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
          continue;
        }
        if (!file) { skip(in, size + pad(size)); continue; }
        try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(out))) {
          byte[] buffer = new byte[32768];
          long left = size;
          while (left > 0) {
            int n = in.read(buffer, 0, (int) Math.min(buffer.length, left));
            if (n < 0) throw new IOException("Java archive ends early");
            stream.write(buffer, 0, n);
            left -= n;
          }
        }
        skip(in, pad(size));
        if ((mode & 0111) != 0) out.setExecutable(true, false);
      }
    }
  }

  private static boolean readFully(InputStream in, byte[] buffer) throws IOException {
    int at = 0;
    while (at < buffer.length) {
      int n = in.read(buffer, at, buffer.length - at);
      if (n < 0) return false;
      at += n;
    }
    return true;
  }

  private static byte[] readBytes(InputStream in, long size) throws IOException {
    if (size < 0 || size > (1 << 20)) throw new IOException("Unexpected header size in Java archive");
    byte[] data = new byte[(int) size];
    if (!readFully(in, data)) throw new IOException("Java archive ends early");
    return data;
  }

  private static void skip(InputStream in, long count) throws IOException {
    byte[] buffer = new byte[8192];
    while (count > 0) {
      int n = in.read(buffer, 0, (int) Math.min(buffer.length, count));
      if (n < 0) throw new IOException("Java archive ends early");
      count -= n;
    }
  }

  private static long pad(long size) { return (512 - size % 512) % 512; }

  private static String tarString(byte[] header, int at, int length) {
    int end = at;
    while (end < at + length && header[end] != 0) end++;
    return new String(header, at, end - at, StandardCharsets.UTF_8);
  }

  private static long tarNumber(byte[] header, int at, int length) {
    if ((header[at] & 0x80) != 0) { // base-256 for big sizes
      long value = 0;
      for (int k = at + 1; k < at + length; k++) value = value << 8 | (header[k] & 0xFF);
      return value;
    }
    String s = tarString(header, at, length).trim();
    return s.isEmpty() ? 0 : Long.parseLong(s, 8);
  }

  private static String paxValue(byte[] data, String key) {
    for (String record : new String(data, StandardCharsets.UTF_8).split("\n")) {
      int space = record.indexOf(' '), equals = record.indexOf('=');
      if (space > 0 && equals > space && record.substring(space + 1, equals).equals(key)) return record.substring(equals + 1);
    }
    return null;
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

  private static Map<String,Object> versionJson(String id) throws Exception {
    Map<String,Object> manifest = obj(Json.parse(getText(MANIFEST)));
    for (Object v : arr(manifest.get("versions"))) {
      Map<String,Object> version = obj(v);
      if (id.equals(str(version.get("id")))) return obj(Json.parse(getText(str(version.get("url")))));
    }
    throw new IOException("Minecraft " + id + " is absent from Mojang's version manifest");
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
      boolean matches = (name.length() == 0 || Platform.ruleName().equals(name)) && archMatches && versionMatches && featuresMatch;
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

  private static void downloadNatives(File home, boolean repair, Progress p) throws Exception {
    List<Job> jobs = new ArrayList<Job>();
    for (String path : natives()) jobs.add(new Job(null, new File(home, "natives/" + path.substring(path.lastIndexOf('/') + 1)), null, true, path));
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
  public static void syncMods(File root, boolean repair, Progress p) throws Exception { syncMods(root, GameVersion.V1_8_9, repair, p); }

  public static void syncMods(File root, GameVersion v, boolean repair, Progress p) throws Exception {
    Settings settings = new Settings(new File(root, "launcher.properties"));
    Map<String,Object> manifest = manifest(root);
    syncJava(root, manifest, p);
    Map<String,Object> set = section(manifest, v);
    File home = v.home(root);
    File mods = new File(gameDir(home), "mods");
    List<ModFile> wanted = new ArrayList<ModFile>();
    for (Object o : arr(set.get("mods"))) wanted.add(new ModFile(obj(o), mods));
    for (Object o : arr(set.get("dependencies"))) wanted.add(new ModFile(obj(o), mods));
    Map<String,Object> renderers = obj(set.get("renderers"));
    File rendererDir = new File(home, "renderers/" + settings.renderer());
    for (Object o : arr(renderers.get(settings.renderer()))) wanted.add(new ModFile(obj(o), rendererDir));
    removeRetired(home, mods, rendererDir, wanted);
    // a renderer jar left in the shared folder would load on both paths
    for (Map.Entry<String,Object> group : renderers.entrySet())
      for (Object o : arr(group.getValue())) Files.deleteIfExists(new File(mods, safeName(str(obj(o).get("file")))).toPath());
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
    installDefaults(home, set);
    writeFirstRunOptions(home);
  }

  /** Vanilla starts a new player at 120 FPS with VSync on; this client should not. Never touches an options file that already exists. */
  private static void writeFirstRunOptions(File home) throws IOException {
    File options = new File(gameDir(home), "options.txt");
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
  private static void removeRetired(File home, File mods, File rendererDir, List<ModFile> wanted) throws IOException {
    Set<String> names = new HashSet<String>();
    StringBuilder managed = new StringBuilder();
    for (ModFile mod : wanted) {
      names.add(mod.file);
      if (mod.target.getParentFile().equals(mods)) managed.append(mod.file).append('\n');
    }
    File list = new File(home, "managed-mods.txt");
    for (String old : lines(list)) if (!names.contains(old)) Files.deleteIfExists(new File(mods, safeName(old)).toPath());
    writeAtomic(list, managed.toString().getBytes(StandardCharsets.UTF_8));
    File[] installed = rendererDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".jar"));
    if (installed != null) for (File jar : installed) if (!names.contains(jar.getName())) Files.deleteIfExists(jar.toPath());
  }

  /** The default settings zip fills in only what the player does not already have. */
  private static void installDefaults(File home, Map<String,Object> manifest) throws IOException {
    Map<String,Object> defaults = obj(manifest.get("defaults"));
    String url = str(defaults.get("url")), sha = str(defaults.get("sha512"));
    if (url.length() == 0 || new File(gameDir(home), "config/.luka-defaults").isFile()) return;
    File zip = new File(home, "defaults.zip.download");
    download(url, zip, sha, true, "SHA-512");
    File config = new File(gameDir(home), "config");
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

  private static void ensureClasspath(File root, GameVersion v) throws IOException {
    File target = new File(v.home(root), "classpath.txt"); if (target.isFile()) return;
    File external = new File(distributionDir(root), "classpath.txt");
    if (v.primary() && external.isFile()) { copy(external, target, false); return; }
    try (InputStream in = Installer.class.getResourceAsStream("/" + v.classpathResource)) {
      if (in == null) throw new IOException("Missing embedded " + v.classpathResource);
      writeAtomic(target, bytes(in));
    }
  }

  private static void ensureOptionalResource(File home, String name, String resource) throws IOException {
    File target = new File(home, name); if (target.exists()) return;
    try (InputStream in = Installer.class.getResourceAsStream("/" + resource)) {
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
  private static File gameDir(File home) {
    String override = System.getProperty("luka.gameDir");
    return override != null ? new File(override) : new File(home, "game");
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
