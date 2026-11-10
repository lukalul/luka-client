package dev.hcfopt;

import java.lang.reflect.Field;
import net.minecraft.server.MinecraftServer;

/**
 * Ornithe Standard Libraries keep the running server in one static field: a server's thread sets it when it starts
 * and clears it when it has stopped. Leaving a singleplayer world and opening another one at once starts the new
 * server while the old one is still saving, so the old one clears the field under the new one; the new world then
 * crashes while it loads ("server" is null in the library's config code).
 *
 * <p>The library's config code also refuses to set up a world while the last world's setup still exists, which happens
 * when the new server starts before the old one has even begun to stop.
 *
 * <p>The fix: every server thread remembers the server thread that ran before it, and a new singleplayer server
 * waits (at most 10 s) for that thread to end before it loads its worlds, so the old server is fully gone, as it is
 * when the player waits a moment. The new server then points the library at itself, also on every tick, in case anything else
 * cleared it. -Dluka.oslServerFix=false turns all of this off.
 */
public final class OslServerRace {
  private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("luka.oslServerFix"));
  private static final long WAIT_MS = 10_000L;
  private static volatile Thread last;
  private static final ThreadLocal<Thread> PREVIOUS = new ThreadLocal<>();
  /** How many times a new server had to wait for an old one (read by the test bot). */
  public static volatile int waits;
  private static Field field;
  private static boolean looked;

  private OslServerRace() {
  }

  /** A server's thread starts: remember the thread of the server before it. */
  public static void running(MinecraftServer server) {
    if (!ENABLED) return;
    Thread current = Thread.currentThread();
    Thread before = last;
    if (before != null && before != current) PREVIOUS.set(before);
    last = current;
  }

  /** A new singleplayer server is about to load its worlds: wait for the old server's thread, then claim the field. */
  public static void starting(MinecraftServer server) {
    if (!ENABLED) return;
    Thread old = PREVIOUS.get();
    PREVIOUS.remove();
    Thread current = Thread.currentThread();
    if (old != null && old != current && old.isAlive()) {
      waits++;
      try {
        old.join(WAIT_MS);
      } catch (InterruptedException e) {
        current.interrupt();
      }
    }
    keep(server);
  }

  public static void keep(MinecraftServer server) {
    if (!ENABLED || server == null) return;
    try {
      if (!looked) {
        looked = true;
        Field f = Class.forName("net.ornithemc.osl.lifecycle.impl.server.MinecraftServerAccess").getField("INSTANCE");
        f.setAccessible(true);
        field = f;
      }
      if (field != null && field.get(null) != server) field.set(null, server);
    } catch (Throwable missing) {
      field = null; // no Ornithe Standard Libraries, or a version without the field: nothing to fix
    }
  }
}
