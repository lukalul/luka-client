package dev.cbui.feature.soundmixer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sound names mapped to values, either by full name or by the start of the name. A lookup prefers a full-name
 * entry; otherwise the longest matching start wins. Names are compared without regard to case. No game classes,
 * so the plain-Java checks can use it.
 */
public final class NameTable<V> {
  private final Map<String, V> whole = new HashMap<String, V>();
  private final Map<String, V> starts = new HashMap<String, V>();
  /** Every start in {@link #starts}, longest first, so the first hit is the most specific one. */
  private final List<String> startOrder = new ArrayList<String>();

  public void whole(String name, V value) {
    whole.put(normal(name), value);
  }

  public void startingWith(String start, V value) {
    String key = normal(start);
    if (starts.put(key, value) != null) return;
    int at = 0;
    while (at < startOrder.size() && startOrder.get(at).length() >= key.length()) at++;
    startOrder.add(at, key);
  }

  public boolean isEmpty() {
    return whole.isEmpty() && starts.isEmpty();
  }

  public int size() {
    return whole.size() + starts.size();
  }

  /** The value for this sound name, or null when nothing matches. */
  public V find(String name) {
    if (name == null) return null;
    String key = normal(name);
    V hit = whole.get(key);
    if (hit != null) return hit;
    for (int i = 0, n = startOrder.size(); i < n; i++) {
      String start = startOrder.get(i);
      if (key.startsWith(start)) return starts.get(start);
    }
    return null;
  }

  /** Lower case, without a leading "minecraft:" namespace and surrounding blanks. */
  public static String normal(String name) {
    String s = name.trim().toLowerCase(Locale.ROOT);
    if (s.startsWith("minecraft:")) s = s.substring("minecraft:".length());
    return s;
  }
}
