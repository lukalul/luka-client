package dev.luka.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Opens the signed update list. A list that was changed, unsigned, or older than one already seen is refused. */
final class Manifests {
  /** The public half of the release key. Only the private half, which is kept off the servers, can sign an update. */
  private static final String PUBLIC_KEY = "MCowBQYDK2VwAyEAHPyd06dCDCobjc1fNfbHnunJtLszznHCY+fi7PJu18o=";

  private static volatile List<List<String>> changelog = Collections.emptyList();
  private static volatile Set<String> versions = Collections.emptySet();

  private Manifests() {}

  /** Checks the signature and returns the list inside. {@code seen} is the last accepted copy, or null to skip the age check. */
  static String open(String signed, File seen) throws IOException {
    String body = verify(signed);
    if (seen != null && seen.isFile()) {
      double known = 0;
      try { known = sequence(verify(new String(Files.readAllBytes(seen.toPath()), StandardCharsets.UTF_8))); }
      catch (IOException unreadable) { /* a damaged copy says nothing about age */ }
      if (sequence(body) < known) throw new IOException("The update list is older than the one this launcher already has");
    }
    readChangelog(body);
    readVersions(body);
    return body;
  }

  /** True when the last accepted list has mods for this Minecraft version (1.8.9 always does). */
  static boolean offers(GameVersion v) { return v.primary() || versions.contains(v.id); }

  /**
   * The same, also when no list was fetched this run (offline, or updates off): then the copy saved by the last
   * successful check ({@code <root>/manifest.json}, signature checked again) answers.
   */
  static boolean offers(GameVersion v, File root) {
    if (offers(v)) return true;
    File cached = new File(root, "manifest.json");
    if (!cached.isFile()) return false;
    try {
      readVersions(verify(new String(Files.readAllBytes(cached.toPath()), StandardCharsets.UTF_8)));
    } catch (IOException unreadable) {
      return false;
    }
    return offers(v);
  }

  private static void readVersions(String body) throws IOException {
    Set<String> out = new HashSet<String>();
    out.addAll(map(map(Installer.Json.parse(body)).get("versions")).keySet());
    versions = out;
  }

  /** Newest release first; each entry is the version followed by its notes. */
  static List<List<String>> changelog() { return changelog; }

  private static String verify(String signed) throws IOException {
    Map<String,Object> envelope = map(Installer.Json.parse(signed));
    Object signature = envelope.get("signature"), manifest = envelope.get("manifest");
    if (!(signature instanceof String) || !(manifest instanceof String)) throw new IOException("The update list is not signed");
    try {
      PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY)));
      Signature check = Signature.getInstance("Ed25519");
      check.initVerify(key);
      check.update(((String) manifest).getBytes(StandardCharsets.UTF_8));
      if (!check.verify(Base64.getDecoder().decode((String) signature))) throw new IOException("The update list's signature is wrong");
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IOException("The update list's signature could not be checked: " + e.getMessage(), e);
    }
    return (String) manifest;
  }

  private static double sequence(String body) throws IOException {
    Object value = map(Installer.Json.parse(body)).get("sequence");
    return value instanceof Number ? ((Number) value).doubleValue() : 0;
  }

  private static void readChangelog(String body) throws IOException {
    Object list = map(Installer.Json.parse(body)).get("changelog");
    if (!(list instanceof List)) return;
    List<List<String>> out = new ArrayList<List<String>>();
    for (Object entry : (List<?>) list) {
      Map<String,Object> release = map(entry);
      List<String> lines = new ArrayList<String>();
      lines.add(String.valueOf(release.get("version")));
      if (release.get("notes") instanceof List) for (Object note : (List<?>) release.get("notes")) lines.add(String.valueOf(note));
      out.add(lines);
    }
    changelog = out;
  }

  @SuppressWarnings("unchecked")
  private static Map<String,Object> map(Object o) {
    return o instanceof Map ? (Map<String,Object>) o : Collections.<String,Object>emptyMap();
  }
}
