package dev.cbui.feature.zoom;

import dev.cbui.Cbui;
import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/**
 * Card "Zoom": a spyglass key plus a slow cinematic zoom. Both run as 0..1 ramps advanced once per client tick
 * (also while the card is off, so they can ease out); every render hook only reads the current state.
 */
public final class ZoomModule extends FeatureModule implements Features.Synced {
  private static final String HOLD = "Hold", SWITCH = "Press to switch";
  private static final float MOST = 500f;

  private final Setting.Choice keyMode = grouped("Keys", add(Setting.tip(new Setting.Choice("Key mode", HOLD, HOLD, SWITCH),
      "Keep the key down, or tap it to start and stop zooming")));

  private final Setting.Slider power = grouped("Main zoom", add(new Setting.Slider("Magnification", 4, 1, 50, 1,
      "How many times closer the main zoom brings things")));
  private final Setting.Slider inSeconds = grouped("Main zoom", add(new Setting.Slider("Ease-in time", 1, 0, 5, 0.05f,
      "Seconds to reach full magnification; 0 snaps")));
  private final Setting.Slider outSeconds = grouped("Main zoom", add(new Setting.Slider("Ease-out time", 0.5f, 0, 5, 0.05f,
      "Seconds to return to the normal view; 0 snaps")));
  private final Setting.Choice inCurve = grouped("Main zoom", add(Setting.tip(
      new Setting.Choice("Ease-in curve", "Expo out", ZoomRamp.CURVES), "Shape of the zoom-in motion")));
  private final Setting.Choice outCurve = grouped("Main zoom", add(Setting.tip(
      new Setting.Choice("Ease-out curve", "Expo out", ZoomRamp.CURVES), "Shape of the zoom-out motion")));
  private final Setting.Toggle bigHand = grouped("Main zoom", add(new Setting.Toggle("Magnify held item", true,
      "Scale the hand and held item with the world")));

  private final Setting.Toggle wheel = grouped("Wheel steps", add(new Setting.Toggle("Mouse wheel refines zoom", true,
      "Scroll while zoomed to go closer or further")));
  private final Setting.Toggle keysOnly = grouped("Wheel steps", add(new Setting.Toggle("Refine with keys only", false,
      "Leave the wheel alone and use the step keys instead")));
  private final Setting.Toggle keepSteps = grouped("Wheel steps", add(new Setting.Toggle("Keep wheel steps", false,
      "Start the next zoom at the last stepped magnification")));
  private final Setting.Slider maxSteps = grouped("Wheel steps", add(new Setting.Slider("Max wheel steps", 10, 0, 50, 1,
      "How many steps closer than the base magnification you can go")));
  private final Setting.Slider stepPercent = grouped("Wheel steps", add(new Setting.Slider("Step factor (%)", 150, 100, 500, 1,
      "How much each step multiplies the magnification")));
  private final Setting.Slider glide = grouped("Wheel steps", add(new Setting.Slider("Step glide speed", 70, 0, 100, 1,
      "How fast a step settles; 100 jumps straight there")));

  private final Setting.Slider slowAim = grouped("Aim and camera", add(new Setting.Slider("Slow aim when magnified (%)", 100, 0, 100, 1,
      "How strongly mouse speed drops as you zoom in")));
  private final Setting.Toggle calmBob = grouped("Aim and camera", add(new Setting.Toggle("Steady head-bob", true,
      "Reduce walking bob while zoomed")));
  private final Setting.Slider smoothAim = grouped("Aim and camera", add(new Setting.Slider("Smooth aim while zoomed", 0, 0, 100, 1,
      "Mouse smoothing during the main zoom; 0 turns it off")));

  private final Setting.Slider cinePower = grouped("Cinematic zoom", add(new Setting.Slider("Cinematic zoom magnification", 4, 1, 50, 1,
      "How many times closer the cinematic zoom brings things")));
  private final Setting.Slider cineIn = grouped("Cinematic zoom", add(new Setting.Slider("Cinematic zoom in time", 10, 0, 30, 0.5f,
      "Seconds the cinematic zoom takes to arrive")));
  private final Setting.Slider cineOut = grouped("Cinematic zoom", add(new Setting.Slider("Cinematic zoom out time", 1, 0, 30, 0.5f,
      "Seconds it takes to pull back")));
  private final Setting.Toggle cineHideHud = grouped("Cinematic zoom", add(new Setting.Toggle("Hide HUD in cinematic zoom", true,
      "Clear the screen while the cinematic zoom is active")));

  private final KeyBinding magnifyKey = new KeyBinding("Zoom: magnify", Keyboard.KEY_C, "luka");
  private final KeyBinding cinematicKey = new KeyBinding("Zoom: cinematic zoom", Keyboard.KEY_NONE, "luka");
  private final KeyBinding closerKey = new KeyBinding("Zoom: step closer", Keyboard.KEY_NONE, "luka");
  private final KeyBinding backKey = new KeyBinding("Zoom: step back", Keyboard.KEY_NONE, "luka");
  private boolean keysListed;

  private final ZoomRamp mainRamp = new ZoomRamp();
  private final ZoomRamp cineRamp = new ZoomRamp();
  private long ticks;
  private boolean mainOn, cineOn;
  private int steps;
  private float stepNow = 1f, stepBefore = 1f;
  private float lastTotal = 1f;
  /** Which bob is being drawn right now: 0 none, 1 world, 2 hand (set around vanilla's bob call). */
  private int bobPass;

  public ZoomModule() {
    super("Zoom", true);
  }

  @Override
  public String category() {
    return "VISUALS";
  }

  /** The module whether or not it is switched on (it still eases out while off), or null when not registered. */
  public static ZoomModule instance() {
    return Features.get(ZoomModule.class);
  }

  @Override
  public void sync() {
    try {
      listKeys();
      step();
    } catch (Throwable error) {
      Cbui.reportOnce("zoom tick", error);
    }
  }

  private void listKeys() throws Exception {
    if (keysListed || mc.gameSettings == null) return;
    keysListed = true;
    List<KeyBinding> mine = Arrays.asList(magnifyKey, cinematicKey, closerKey, backKey);
    File options = new File(mc.mcDataDir, "options.txt");
    if (options.isFile()) {
      List<String> lines = Files.readAllLines(options.toPath(), StandardCharsets.UTF_8);
      for (KeyBinding key : mine) {
        // also finds a key saved under its name before the card was renamed
        Integer code = dev.cbui.config.CardRenames.savedKeyCode(lines, key.getKeyDescription());
        if (code != null) key.setKeyCode(code); // a missing or damaged line keeps the default key
      }
    }
    KeyBinding[] old = mc.gameSettings.keyBindings;
    KeyBinding[] all = Arrays.copyOf(old, old.length + mine.size());
    for (int i = 0; i < mine.size(); i++) all[old.length + i] = mine.get(i);
    mc.gameSettings.keyBindings = all;
    KeyBinding.resetKeyBindingArrayAndHash();
  }

  private void step() {
    ticks++;
    boolean live = enabled() && mc.thePlayer != null && mc.theWorld != null;
    boolean wasMain = mainOn;
    if (!live) {
      mainOn = cineOn = false;
      steps = 0;
      drain();
    } else if (SWITCH.equals(keyMode.get())) {
      while (magnifyKey.isPressed()) mainOn = !mainOn;
      while (cinematicKey.isPressed()) cineOn = !cineOn;
    } else {
      mainOn = magnifyKey.getIsKeyPressed();
      cineOn = cinematicKey.getIsKeyPressed();
      while (magnifyKey.isPressed()) { /* only the held state matters here */ }
      while (cinematicKey.isPressed()) { /* same */ }
    }
    while (closerKey.isPressed()) if (mainOn) nudge(1);
    while (backKey.isPressed()) if (mainOn) nudge(-1);
    if (wasMain && !mainOn && !keepSteps.get()) steps = 0;
    clampSteps();

    if (mainRamp.target() != (mainOn ? 1f : 0f)) {
      mainRamp.aim(ticks, mainOn ? 1f : 0f, mainOn ? inSeconds.get() : outSeconds.get(), mainOn ? inCurve.get() : outCurve.get());
    }
    if (cineRamp.target() != (cineOn ? 1f : 0f)) {
      cineRamp.aim(ticks, cineOn ? 1f : 0f, cineOn ? cineIn.get() : cineOut.get(), "Linear");
    }

    stepBefore = stepNow;
    float goal = stepGoal();
    float speed = glide.get() / 100f;
    if (speed >= 0.999f) stepNow = goal;
    else stepNow += (goal - stepNow) * Math.max(0.03f, speed);
    if (Math.abs(goal - stepNow) < 1.0E-4f) stepNow = goal;

    float total = magnification(1f);
    if (Math.abs(total - lastTotal) > 1.0E-4f && mc.renderGlobal != null && mc.theWorld != null) mc.renderGlobal.rebuildDisplayListEntities();
    lastTotal = total;
  }

  private void drain() {
    while (magnifyKey.isPressed()) { /* forget presses made while unusable */ }
    while (cinematicKey.isPressed()) { /* same */ }
  }

  private float stepFactor() {
    return stepPercent.get() / 100f;
  }

  private float stepGoal() {
    return (float) Math.pow(stepFactor(), steps);
  }

  /** Fewest steps allowed: the first step that reaches 1x (magnification() clamps anything below to 1x). */
  private int fewestSteps() {
    double factor = stepFactor();
    if (factor <= 1.0001) return 0;
    return -(int) Math.ceil(Math.log(power.get()) / Math.log(factor) - 1.0E-6);
  }

  private void clampSteps() {
    int most = Math.round(maxSteps.get());
    if (steps > most) steps = most;
    int least = fewestSteps();
    if (steps < least) steps = least;
  }

  private void nudge(int direction) {
    steps += direction;
    clampSteps();
  }

  /** Total world magnification for this frame (1 = none). Pure: reads state only. */
  public float magnification(float partialTicks) {
    float stepped = stepBefore + (stepNow - stepBefore) * partialTicks;
    float mainFull = Math.max(1f, power.get() * stepped);
    float main = 1f + (mainFull - 1f) * mainRamp.at(ticks, partialTicks);
    float cine = 1f + (cinePower.get() - 1f) * cineRamp.at(ticks, partialTicks);
    float total = main * cine;
    return total < 1f ? 1f : Math.min(MOST, total);
  }

  /** The FOV after zoom; {@code worldPass} false is the hand pass. */
  public float zoomedFov(float fov, float partialTicks, boolean worldPass) {
    if (!worldPass && !bigHand.get()) return fov;
    float total = magnification(partialTicks);
    return total <= 1f ? fov : fov / total;
  }

  /** Called with the mouse wheel's delta; true when it was spent on zoom steps (the game must not see it). */
  public boolean takeWheel(int delta) {
    if (delta == 0 || !mainOn || !enabled() || !wheel.get() || keysOnly.get() || mc.currentScreen != null) return false;
    nudge(delta > 0 ? 1 : -1);
    return true;
  }

  /** Vanilla's mouse sensitivity value, lowered so aim slows down with the magnification. */
  public float sensitivity(float vanilla) {
    float total = lastTotal;
    float strength = slowAim.get() / 100f;
    if (total <= 1.0001f || strength <= 0f) return vanilla;
    float speed = 1f + (1f / total - 1f) * strength;
    float curve = vanilla * 0.6f + 0.2f;
    float slowed = curve * (float) Math.cbrt(speed);
    return (slowed - 0.2f) / 0.6f;
  }

  /** True while the cinematic camera should be forced on. */
  public boolean forcesSmoothCamera() {
    return cineOn || (mainOn && smoothAim.get() > 0f);
  }

  /** Smoothing step handed to the mouse filter; lower "smooth aim" values let it follow the mouse faster. */
  public float smoothingStep(float vanilla) {
    if (cineOn || !mainOn || smoothAim.get() <= 0f || !(vanilla > 0f) || vanilla >= 1f) return vanilla;
    return (float) Math.pow(vanilla, smoothAim.get() / 100f);
  }

  /** True while the cinematic zoom wants the HUD gone. */
  public boolean hidesHud() {
    return cineOn && cineHideHud.get();
  }

  public void bobStart(boolean hand) {
    bobPass = hand ? 2 : 1;
  }

  public void bobEnd() {
    bobPass = 0;
  }

  /** Scale for the walking bob currently being drawn. */
  public float bobScale() {
    if (bobPass == 0 || !calmBob.get()) return 1f;
    float total = lastTotal;
    if (bobPass == 2 && !bigHand.get()) return 1f;
    return total <= 1.0001f ? 1f : (float) Math.pow(total, -0.75);
  }
}
