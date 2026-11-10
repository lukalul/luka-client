package dev.cbui.feature.polytime;

import dev.cbui.Cbui;
import dev.cbui.feature.FeatureModule;
import dev.cbui.hud.Setting;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.settings.KeyBinding;

public final class PolyTimeModule extends FeatureModule implements dev.cbui.feature.Features.Synced {
  /**
   * Runs every tick even while the module is off, so its two keybinds are always registered. A bind that is only registered while the
   * module is enabled gets dropped from options.txt the next time Minecraft saves, and the player's key is lost.
   */
  @Override public void sync() { installControls(); }

  public final Setting.Toggle irlTime = add(new Setting.Toggle("Use IRL time", false, "Follow the real sun where you live (sunrise, noon, sunset and midnight match the game day). Needs internet to find your rough location."));
  public final Setting.Toggle irlLunar = add(new Setting.Toggle("Use IRL lunar phase", false, "Show today's real moon phase instead of the game's."));
  public final Setting.Choice moonPhase = add(Setting.tip(new Setting.Choice("Moon phase", "Default", "Default", "Full Moon", "Waning Gibbous", "Last Quarter", "Waning Crescent", "New Moon", "Waxing Crescent", "First Quarter", "Waxing Gibbous"), "Force a moon phase. Default leaves the game's own phase. Ignored while Use IRL lunar phase is on."));
  public final Setting.Slider time = add(new Setting.Slider("Time", 12f, 0f, 24f, .5f, "Fixed time of day on a 24-hour clock: 0 midnight, 6 sunrise, 12 noon, 18 sunset. Ignored with Use IRL time or Fast Time."));
  public final Setting.Toggle fastTime = add(new Setting.Toggle("Fast Time", false, "Let the day run quickly by itself instead of staying fixed. Ignored with Use IRL time."));
  public final Setting.Slider fastSpeed = add(new Setting.Slider("Speed", 10f, .1f, 100f, .1f, "How fast the day runs with Fast Time (higher is faster)."));
  public final Setting.Slider keybindSpeed = add(new Setting.Slider("Key Bind Speed", 1f, .1f, 12f, .1f, "In-game hours per second while a time key is held (keys are in Options > Controls > luka; they start unbound)."));

  private static final long KEEP_SERVER_TIME = Long.MIN_VALUE;
  private KeyBinding laterKey;
  private KeyBinding earlierKey;
  private boolean controlsInstalled;
  private float preciseHour = 12f;
  private float sliderSnapshot = 12f;

  public PolyTimeModule() { super("PolyTime", false); }
  @Override public String category() { return "VISUALS"; }

  public long clientTime(long serverTicks) {
    long chosen = selectedDayTick();
    if (chosen == KEEP_SERVER_TIME) return serverTicks;
    long dayStart = serverTicks - Math.floorMod(serverTicks, 24000L);
    return dayStart + Math.floorMod(chosen, 24000L);
  }

  public int selectedMoonPhase() {
    if (irlLunar.get()) return RealTime.lunarPhase();
    String selected = moonPhase.get();
    List<String> values = moonPhase.choices();
    int position = values.indexOf(selected);
    return position < 1 ? -1 : position - 1;
  }

  private long selectedDayTick() {
    if (irlTime.get()) {
      float localHour = RealTime.gameHours();
      return localHour < 0f ? KEEP_SERVER_TIME : hourToTick(localHour);
    }
    if (fastTime.get()) {
      double moving = System.currentTimeMillis() * .02d * fastSpeed.get();
      return (long) (moving % 24000d);
    }
    return hourToTick(readPreciseHour());
  }

  private static long hourToTick(float hour) { return (long) (hour * 1000f + 18000f); }

  private float readPreciseHour() {
    float sliderHour = time.get();
    if (Float.compare(sliderHour, sliderSnapshot) != 0) {
      preciseHour = sliderHour;
      sliderSnapshot = sliderHour;
    }
    return preciseHour;
  }

  @Override public void tick() {
    try {
      installControls();
      if (irlTime.get()) return;
      int movement = keyDirection();
      if (movement == 0) return;
      float next = readPreciseHour() + movement * keybindSpeed.get() / 20f;
      preciseHour = Math.min(24f, Math.max(0f, next));
      time.set(preciseHour);
      sliderSnapshot = time.get();
    } catch (Throwable error) { Cbui.reportOnce("PolyTime tick", error); }
  }

  private int keyDirection() {
    int direction = isHeld(laterKey) ? 1 : 0;
    return direction - (isHeld(earlierKey) ? 1 : 0);
  }

  private static boolean isHeld(KeyBinding key) {
    return key != null && key.getKeyCode() != 0 && key.getIsKeyPressed();
  }

  private void installControls() {
    if (controlsInstalled || mc.gameSettings == null) return;
    controlsInstalled = true;
    try {
      KeyBinding later = new KeyBinding("PolyTime: time forward (hold)", 0, "luka");
      KeyBinding earlier = new KeyBinding("PolyTime: time backward (hold)", 0, "luka");
      restoreKeyCodes(later, earlier);
      List<KeyBinding> combined = new ArrayList<KeyBinding>();
      for (KeyBinding existing : mc.gameSettings.keyBindings) combined.add(existing);
      combined.add(later);
      combined.add(earlier);
      mc.gameSettings.keyBindings = combined.toArray(new KeyBinding[combined.size()]);
      KeyBinding.resetKeyBindingArrayAndHash();
      laterKey = later;
      earlierKey = earlier;
    } catch (Throwable error) { Cbui.reportOnce("PolyTime keys", error); }
  }

  private void restoreKeyCodes(KeyBinding later, KeyBinding earlier) throws Exception {
    File options = new File(mc.mcDataDir, "options.txt");
    if (!options.isFile()) return;
    List<String> lines = Files.readAllLines(options.toPath(), StandardCharsets.UTF_8);
    applySavedCode(lines, later);
    applySavedCode(lines, earlier);
  }

  private static void applySavedCode(List<String> lines, KeyBinding key) {
    String marker = "key_" + key.getKeyDescription() + ":";
    for (String line : lines) {
      if (!line.startsWith(marker)) continue;
      try { key.setKeyCode(Integer.parseInt(line.substring(marker.length()).trim())); }
      catch (NumberFormatException ignored) { }
      return;
    }
  }
}
