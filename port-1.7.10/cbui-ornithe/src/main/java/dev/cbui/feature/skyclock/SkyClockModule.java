package dev.cbui.feature.skyclock;

import dev.cbui.Cbui;
import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Arrays;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

/**
 * Time Changer: the time of day and the moon the player sees, on the client only. The server's day number is kept;
 * only the time within the day is swapped, by one of three modes: the real sun at the player's location, a fast
 * running day, or a fixed hour that two hold keys can move.
 */
public final class SkyClockModule extends FeatureModule implements Features.Synced {
  static final String KEY_LATER = "Time Changer - push clock ahead";
  static final String KEY_EARLIER = "Time Changer - pull clock back";
  private static final String[] MOONS = {
      "Game's own", "Full", "Waning gibbous", "Third quarter", "Waning crescent", "New", "Waxing crescent",
      "First quarter", "Waxing gibbous",
  };
  private static final long MOON_REFRESH_MS = 3_600_000L;

  private final Setting.Toggle realSun = add(new Setting.Toggle("Follow real sun", false,
      "Match the sky to the sun where you are; looks up your rough location online"));
  private final Setting.Slider hour = add(new Setting.Slider("Hour of day", 12, 0, 24, 0.5f,
      "The time shown: 0 midnight, 6 dawn, 12 midday, 18 dusk"));
  private final Setting.Toggle fastDay = add(new Setting.Toggle("Run the day fast", false,
      "Let the day cycle by itself instead of holding one hour"));
  private final Setting.Slider daySpeed = add(new Setting.Slider("Day speed", 10, 0.1f, 100, 0.1f,
      "Times faster than a normal day while the day runs fast"));
  private final Setting.Slider keySpeed = add(new Setting.Slider("Hold-key speed", 1, 0.1f, 12, 0.1f,
      "Game hours per second while a clock key is held; bind the keys under Controls"));
  private final Setting.Choice moon = add(Setting.tip(new Setting.Choice("Moon", MOONS[0], MOONS), "Which moon to draw"));
  private final Setting.Toggle realMoon = add(new Setting.Toggle("Use today's real moon phase", false,
      "Draw the moon as it is in the real sky today; overrides Moon"));

  private KeyBinding later;
  private KeyBinding earlier;

  // fixed hour with smooth key movement
  private double exactHour = 12;
  private int heldDirection;
  private long heldSinceNanos;
  private double heldFromHour;

  // fast day
  private double fastTick = -1;
  private long fastLastNanos;

  // real sun
  private final RoughLocation location = new RoughLocation();
  private double[] sunFor;
  private SunTimes sunA;
  private SunTimes sunB;

  // real moon
  private int moonPhase = -1;
  private long moonAt;

  /** While above zero, reads of the world time return the stored value (the world's own clock step). */
  private int rawReads;

  public SkyClockModule() {
    super("Time Changer", false);
  }

  @Override
  public String category() {
    return "VISUALS";
  }

  /** The module while it should change the sky, else null. */
  public static SkyClockModule active() {
    SkyClockModule module = Features.get(SkyClockModule.class);
    return module != null && module.enabled() ? module : null;
  }

  // ---- hooks ---------------------------------------------------------------------------------------------------

  /** The time the client world should report: same day, chosen time of day. Other worlds keep theirs. */
  public long worldTime(Object worldInfo, long stored) {
    if (rawReads > 0) return stored;
    World world = mc.theWorld;
    if (world == null || world.getWorldInfo() != worldInfo) return stored;
    int tick = tickOfDay();
    if (tick < 0) return stored;
    return Math.floorDiv(stored, (long) SkyMath.DAY_TICKS) * SkyMath.DAY_TICKS + tick;
  }

  /** The moon phase the client world should report (0 full ... 4 new ... 7). */
  public int moonPhase(Object world, int phase) {
    if (world != mc.theWorld) return phase;
    if (realMoon.get()) {
      long now = System.currentTimeMillis();
      if (moonPhase < 0 || now - moonAt > MOON_REFRESH_MS || now < moonAt) {
        moonPhase = SkyMath.realMoonPhase(now);
        moonAt = now;
      }
      return moonPhase;
    }
    int chosen = Arrays.asList(MOONS).indexOf(moon.get()) - 1;
    return chosen < 0 ? phase : chosen;
  }

  /** Brackets the world's own clock step, which must read and write the stored time. */
  public void rawBegin() {
    rawReads++;
  }

  public void rawEnd() {
    if (rawReads > 0) rawReads--;
  }

  // ---- time of day ---------------------------------------------------------------------------------------------

  /** Tick within the day (0..23999), or -1 to keep the server's time. */
  private int tickOfDay() {
    if (realSun.get()) return sunTick();
    if (fastDay.get()) return fastTick();
    return SkyMath.hourToTick(currentHour());
  }

  private int fastTick() {
    long now = System.nanoTime();
    double speed = daySpeed.get();
    if (fastTick < 0) fastTick = (System.currentTimeMillis() / 50.0 * speed) % SkyMath.DAY_TICKS;
    else fastTick = (fastTick + (now - fastLastNanos) / 5.0e7 * speed) % SkyMath.DAY_TICKS;
    fastLastNanos = now;
    return (int) fastTick;
  }

  private int sunTick() {
    double[] where = location.latLon();
    if (where == null) return -1;
    long now = System.currentTimeMillis();
    long day = SkyMath.solarDay(now, where[1]);
    SunTimes sun = sun(day, where);
    long half = SunTimes.DAY_MS / 2;
    if (now < sun.noon - half) sun = sun(day - 1, where);
    else if (now >= sun.noon + half) sun = sun(day + 1, where);
    return SkyMath.tickForSun(sun, now);
  }

  /** Sun times for a UTC day, keeping the last two days so a day change does not recompute every frame. */
  private SunTimes sun(long day, double[] where) {
    if (sunFor != where) {
      sunFor = where;
      sunA = null;
      sunB = null;
    }
    if (sunA != null && sunA.day == day) return sunA;
    if (sunB != null && sunB.day == day) return sunB;
    SunTimes fresh = SunTimes.forDay(day, where[0], where[1]);
    sunB = sunA;
    sunA = fresh;
    return fresh;
  }

  private double currentHour() {
    if (heldDirection != 0) {
      double moved = keySpeed.get() * (System.nanoTime() - heldSinceNanos) / 1.0e9;
      return clampHour(heldFromHour + heldDirection * moved);
    }
    float shown = hour.get();
    if (Math.abs(Math.round(exactHour * 2) / 2.0 - shown) > 0.01) exactHour = shown; // moved in the menu
    return exactHour;
  }

  private static double clampHour(double h) {
    return Math.max(0, Math.min(24, h));
  }

  // ---- keys ----------------------------------------------------------------------------------------------------

  @Override
  public void tick() {
    if (realSun.get()) {
      release(false);
      location.refresh(LocalDate.now().toEpochDay());
      return;
    }
    int direction = (down(later) ? 1 : 0) - (down(earlier) ? 1 : 0);
    if (direction != heldDirection) {
      double h = currentHour();
      exactHour = h;
      heldFromHour = h;
      heldSinceNanos = System.nanoTime();
      boolean released = direction == 0;
      heldDirection = direction;
      hour.set((float) h);
      if (released) saveConfig();
    } else if (direction != 0) {
      exactHour = currentHour();
      hour.set((float) exactHour);
    }
  }

  private void release(boolean save) {
    if (heldDirection == 0) return;
    exactHour = currentHour();
    heldDirection = 0;
    hour.set((float) exactHour);
    if (save) saveConfig();
  }

  private static boolean down(KeyBinding key) {
    return key != null && key.getKeyCode() != Keyboard.KEY_NONE && key.getIsKeyPressed();
  }

  private void saveConfig() {
    try {
      if (Cbui.config() != null && Cbui.hud() != null) Cbui.config().save(Cbui.hud());
    } catch (Throwable error) {
      Cbui.reportOnce("Time Changer save hour", error);
    }
  }

  @Override
  public void sync() {
    if (later == null) registerKeys();
    if (!enabled()) release(true);
  }

  private void registerKeys() {
    if (mc.gameSettings == null || mc.gameSettings.keyBindings == null) return;
    KeyBinding[] old = mc.gameSettings.keyBindings;
    KeyBinding foundLater = null;
    KeyBinding foundEarlier = null;
    for (KeyBinding k : old) {
      if (k == null) continue;
      if (KEY_LATER.equals(k.getKeyDescription())) foundLater = k;
      if (KEY_EARLIER.equals(k.getKeyDescription())) foundEarlier = k;
    }
    int extra = (foundLater == null ? 1 : 0) + (foundEarlier == null ? 1 : 0);
    if (foundLater == null) foundLater = new KeyBinding(KEY_LATER, Keyboard.KEY_NONE, "luka");
    if (foundEarlier == null) foundEarlier = new KeyBinding(KEY_EARLIER, Keyboard.KEY_NONE, "luka");
    if (extra > 0) {
      readSavedCode(foundLater);
      readSavedCode(foundEarlier);
      KeyBinding[] all = Arrays.copyOf(old, old.length + extra);
      int at = old.length;
      if (!contains(old, foundLater)) all[at++] = foundLater;
      if (!contains(old, foundEarlier)) all[at] = foundEarlier;
      mc.gameSettings.keyBindings = all;
      KeyBinding.resetKeyBindingArrayAndHash();
    }
    earlier = foundEarlier;
    later = foundLater;
  }

  private static boolean contains(KeyBinding[] keys, KeyBinding key) {
    for (KeyBinding k : keys) if (k == key) return true;
    return false;
  }

  /** options.txt was read before these keys existed, so their saved codes are read here. */
  private void readSavedCode(KeyBinding key) {
    try {
      File options = new File(mc.mcDataDir, "options.txt");
      if (!options.isFile()) return;
      // also finds a key saved under its name before the card was renamed
      Integer code = dev.cbui.config.CardRenames.savedKeyCode(
          Files.readAllLines(options.toPath(), StandardCharsets.UTF_8), key.getKeyDescription());
      if (code != null) key.setKeyCode(code); // a missing or broken line keeps the key unbound
    } catch (Throwable error) {
      Cbui.reportOnce("Time Changer saved keys", error);
    }
  }
}
