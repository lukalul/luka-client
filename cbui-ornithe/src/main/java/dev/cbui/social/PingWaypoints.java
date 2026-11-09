package dev.cbui.social;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Puts a friend's ping on the map as a temporary Envoy waypoint (reflection: luka works without Envoy, it just shows no marker). */
final class PingWaypoints {
  private static boolean tried;
  private static Method add, remove, current;
  private static Field nameField;
  private static Field idField;

  private PingWaypoints() {}

  private static boolean ready() {
    if (!tried) {
      tried = true;
      try {
        Class<?> store = Class.forName("dev.envoy.module.waypoint.LocalWaypoints");
        add = store.getMethod("add", String.class, int.class, int.class, int.class, int.class);
        remove = store.getMethod("remove", String.class);
        Class<?> waypoint = Class.forName("dev.envoy.module.waypoint.LocalWaypoints$Waypoint");
        idField = waypoint.getField("id");
        nameField = waypoint.getField("name");
        current = store.getMethod("current");
      } catch (Throwable t) {
        add = null;
      }
    }
    return add != null;
  }

  /** Adds the waypoint in the current server and dimension; returns its id, or null when Envoy is not there. */
  static String add(String name, int x, int y, int z, int argb) {
    if (!ready()) return null;
    try {
      Object waypoint = add.invoke(null, name, x, y, z, argb);
      return waypoint == null ? null : String.valueOf(idField.get(waypoint));
    } catch (Throwable t) {
      return null;
    }
  }

  /** Removes ping markers left in this server's waypoint list (the game closed before they ran out). */
  static int purgeLeftovers() {
    if (!ready()) return 0;
    int removed = 0;
    try {
      for (Object w : (java.util.List<?>) current.invoke(null)) {
        Object name = nameField.get(w);
        if (name instanceof String && ((String) name).startsWith("Ping: ")) {
          remove(String.valueOf(idField.get(w)));
          removed++;
        }
      }
    } catch (Throwable ignored) {
      // leave them
    }
    return removed;
  }

  static void remove(String id) {
    if (!ready() || id == null) return;
    try {
      remove.invoke(null, id);
    } catch (Throwable ignored) {
      // already gone
    }
  }
}
