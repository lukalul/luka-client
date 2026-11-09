package dev.luka.launcher;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Fetches and atomically installs signed-by-hash launcher, mod and dependency updates. */
final class Updater {
  interface Listener {
    void status(String text);
    void clientId(String id);
    /** The new launcher is staged: close this one and start it. */
    void restart() throws IOException;
  }
  private Updater() {}

  /** Updates are not optional: when the launcher on disk is not the published one, the new one is fetched and started. */
  static synchronized void check(File root, String manifestUrl, String currentVersion, Listener listener) throws Exception {
    if (manifestUrl == null || manifestUrl.trim().isEmpty()) { listener.status("Updates off"); return; }
    listener.status("Checking for updates...");
    String json = Manifests.open(Http.get(manifestUrl.trim(), null), new File(root, "manifest.json"));
    Map<String,String> launcher = object(json, "launcher");
    String clientId = Http.str(json, "msClientId");
    if (clientId != null && !clientId.trim().isEmpty()) listener.clientId(clientId.trim());
    String app = System.getProperty("luka.app");
    File dist = app == null ? root.getParentFile() : new File(app);
    // mods are brought up to date by Installer.syncMods on every Play; only the launcher itself updates from here
    File current = new File(dist, "luka-launcher.jar");
    if (launcher.isEmpty() || !current.isFile() || matches(current, required(launcher, "sha512"))) { listener.status("Up to date"); return; }
    if (System.getProperty("luka.updated") != null) throw new IllegalStateException("The launcher update could not be applied. Close luka and open it again.");
    listener.status("Updating launcher...");
    install(new Install(required(launcher, "url"), required(launcher, "sha512"), new File(dist, "luka-launcher.jar.new"), null, true));
    listener.restart();
  }

  private static void install(Install i) throws IOException {
    File staging = new File(i.first.getPath() + ".download");
    download(i.url, staging, i.sha, i.jar);
    replace(staging, i.first);
    if (i.second != null) {
      File part = new File(i.second.getPath() + ".download");
      part.getParentFile().mkdirs();
      Files.copy(i.first.toPath(), part.toPath(), StandardCopyOption.REPLACE_EXISTING);
      if (!matches(part, i.sha)) throw new IOException("Hash mismatch while copying " + i.second.getName());
      replace(part, i.second);
    }
  }

  private static void download(String address, File target, String sha, boolean jar) throws IOException {
    target.getParentFile().mkdirs();
    URL url = secureUrl(address);
    HttpURLConnection c = null;
    for (int redirects = 0; redirects <= 5; redirects++) {
      c = (HttpURLConnection) url.openConnection();
      c.setConnectTimeout(15000); c.setReadTimeout(45000); c.setInstanceFollowRedirects(false);
      c.setRequestProperty("User-Agent", "luka-launcher/1.0");
      int code = c.getResponseCode();
      if (code >= 300 && code < 400) {
        String location = c.getHeaderField("Location");
        if (location == null || redirects == 5) throw new IOException("Unsafe or excessive redirect from " + url);
        url = secureUrl(new URL(url, location).toString()); c.disconnect(); continue;
      }
      if (code != 200) throw new IOException("HTTP " + code + " from " + url);
      break;
    }
    try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(target)) {
      byte[] buffer = new byte[32768]; int n; while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
    }
    if (!matches(target, sha)) { target.delete(); throw new IOException("SHA-512 mismatch for " + target.getName()); }
    if (jar) try (ZipFile z = new ZipFile(target)) { if (z.size() == 0) throw new IOException("Empty jar " + target.getName()); }
  }

  private static URL secureUrl(String address) throws IOException {
    URL url = new URL(address);
    boolean local = "127.0.0.1".equals(url.getHost()) || "localhost".equals(url.getHost());
    if (!"https".equalsIgnoreCase(url.getProtocol()) && !local) throw new IOException("Update URL must use HTTPS");
    return url;
  }

  private static boolean matches(File file, String expected) throws IOException {
    if (!file.isFile()) return false;
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-512");
      try (InputStream in = new FileInputStream(file)) { byte[] b = new byte[32768]; int n; while ((n = in.read(b)) > 0) md.update(b, 0, n); }
      StringBuilder s = new StringBuilder(); for (byte b : md.digest()) s.append(String.format("%02x", b & 255));
      return s.toString().equalsIgnoreCase(expected);
    } catch (GeneralSecurityException e) { throw new IOException(e); }
  }

  private static void replace(File from, File to) throws IOException {
    to.getParentFile().mkdirs();
    try { Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
    catch (AtomicMoveNotSupportedException e) { Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING); }
  }

  private static boolean newer(String candidate, String current) {
    if (candidate == null) return false;
    String[] a = candidate.split("[.-]"), b = current.split("[.-]");
    for (int i=0; i<Math.max(a.length,b.length); i++) {
      int x = number(a,i), y = number(b,i); if (x != y) return x > y;
    }
    return !candidate.equals(current);
  }
  private static int number(String[] a, int i) { try { return i<a.length?Integer.parseInt(a[i].replaceAll("\\D.*", "")):0; } catch(Exception e){return 0;} }
  private static String required(Map<String,String> m, String k) throws IOException { String v=m.get(k); if(v==null||v.isEmpty())throw new IOException("Update entry is missing "+k); return v; }
  private static String safe(String n) throws IOException {
    if (n == null || n.isEmpty() || n.length() > 240 || !n.equals(new File(n).getName()) || n.contains("..")
        || n.indexOf('/') >= 0 || n.indexOf('\\') >= 0 || reserved(n)) throw new IOException("Unsafe update filename");
    return n;
  }
  private static boolean reserved(String n) {
    String base = n.toUpperCase(Locale.ROOT).split("\\.", 2)[0];
    return base.equals("CON") || base.equals("PRN") || base.equals("AUX") || base.equals("NUL")
        || base.matches("COM[1-9]") || base.matches("LPT[1-9]");
  }

  private static Map<String,String> object(String json, String key) throws IOException {
    int start = valueStart(json,key); if(start<0 || json.charAt(start)!='{') return Collections.emptyMap();
    return fields(json.substring(start, balancedEnd(json,start)+1));
  }
  private static List<Map<String,String>> array(String json,String key) throws IOException {
    int start=valueStart(json,key); List<Map<String,String>> out=new ArrayList<Map<String,String>>();
    if(start<0||json.charAt(start)!='[') return out;
    int end=balancedEnd(json,start), i=start+1;
    while(i<end){ if(json.charAt(i)=='{'){int e=balancedEnd(json,i);out.add(fields(json.substring(i,e+1)));i=e+1;}else i++; }
    return out;
  }
  private static Map<String,String> fields(String object) {
    Map<String,String> m=new HashMap<String,String>();
    for(String k:new String[]{"file","url","sha512","version"}){String v=Http.str(object,k);if(v!=null)m.put(k,v);} return m;
  }
  private static int valueStart(String s,String key){int i=s.indexOf("\""+key+"\"");if(i<0)return -1;i=s.indexOf(':',i);if(i<0)return -1;do{i++;}while(i<s.length()&&Character.isWhitespace(s.charAt(i)));return i;}
  private static int balancedEnd(String s,int start)throws IOException{char open=s.charAt(start),close=open=='{'?'}':']';int depth=0;boolean quote=false,escape=false;for(int i=start;i<s.length();i++){char c=s.charAt(i);if(quote){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='\"')quote=false;}else if(c=='\"')quote=true;else if(c==open)depth++;else if(c==close&&--depth==0)return i;}throw new IOException("Malformed update JSON");}

  private static final class Install {
    final String url,sha; final File first,second; final boolean jar;
    Install(String u,String s,File a,File b,boolean j){url=u;sha=s;first=a;second=b;jar=j;}
  }
}
