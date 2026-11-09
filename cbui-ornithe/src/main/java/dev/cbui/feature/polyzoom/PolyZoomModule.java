package dev.cbui.feature.polyzoom;

import dev.cbui.feature.FeatureModule;
import dev.cbui.hud.Setting;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/** Provides configurable primary and secondary camera magnification with smooth FOV transitions. */
public final class PolyZoomModule extends FeatureModule {
  private static final String[] TRANSITIONS = {
      "instant", "linear", "ease_in_sine", "ease_out_sine", "ease_in_out_sine", "ease_in_quad", "ease_out_quad",
      "ease_in_out_quad", "ease_in_cubic", "ease_out_cubic", "ease_in_out_cubic", "ease_in_exp", "ease_out_exp",
      "ease_in_out_exp"};

  public final Setting.Choice zoomKeyBehaviour = grouped("Keybinds", add(Setting.tip(new Setting.Choice(
      "Zoom Key Behaviour", "hold", "hold", "toggle"), "Hold the zoom key, or press it to switch zoom on and off")));

  public final Setting.Slider initialZoom = grouped("Zoom", add(new Setting.Slider(
      "Initial Zoom", 4f, 1f, 50f, 1f, "How many times closer the initial zoom is")));
  public final Setting.Slider zoomInTime = grouped("Zoom", add(new Setting.Slider(
      "Zoom In Time", 1f, 0f, 5f, 0.05f, "Seconds taken to reach full zoom")));
  public final Setting.Slider zoomOutTime = grouped("Zoom", add(new Setting.Slider(
      "Zoom Out Time", 0.5f, 0f, 5f, 0.05f, "Seconds taken to return to normal")));
  public final Setting.Choice zoomInTransition = grouped("Zoom", add(Setting.tip(new Setting.Choice(
      "Zoom In Transition", "ease_out_exp", TRANSITIONS), "Easing curve used while zooming in")));
  public final Setting.Choice zoomOutTransition = grouped("Zoom", add(Setting.tip(new Setting.Choice(
      "Zoom Out Transition", "ease_out_exp", TRANSITIONS), "Easing curve used while zooming out")));
  public final Setting.Toggle affectHandFov = grouped("Zoom", add(new Setting.Toggle(
      "Affect Hand FOV", true, "Zoom the held item along with the world")));

  public final Setting.Toggle retainZoomSteps = grouped("Scrolling", add(new Setting.Toggle(
      "Retain Zoom Steps", false, "Keep scroll steps after letting go of the zoom key")));
  public final Setting.Toggle scrollZoom = grouped("Scrolling", add(new Setting.Toggle(
      "Scroll Zoom", true, "Scroll while zooming to zoom further")));
  public final Setting.Toggle keybindScrolling = grouped("Scrolling", add(new Setting.Toggle(
      "Keybind Scrolling", false, "Use the Zoom In/Out keys instead of the scroll wheel")));
  public final Setting.Slider scrollStepCount = grouped("Scrolling", add(new Setting.Slider(
      "Scroll Step Count", 10f, 0f, 50f, 1f, "How many scroll steps are available")));
  public final Setting.Slider zoomPerStep = grouped("Scrolling", add(new Setting.Slider(
      "Zoom Per Step", 150f, 100f, 500f, 1f, "Percentage zoom added per scroll step")));
  public final Setting.Slider scrollZoomSmoothness = grouped("Scrolling", add(new Setting.Slider(
      "Scroll Zoom Smoothness", 70f, 0f, 100f, 1f, "How smoothly scroll steps animate (100 is instant)")));

  public final Setting.Slider relativeSensitivity = grouped("Camera", add(new Setting.Slider(
      "Relative Sensitivity", 100f, 0f, 100f, 1f, "How much mouse sensitivity scales down with zoom")));
  public final Setting.Toggle relativeViewBobbing = grouped("Camera", add(new Setting.Toggle(
      "Relative View Bobbing", true, "Scale view bobbing down with zoom")));
  public final Setting.Slider cinematicCamera = grouped("Camera", add(new Setting.Slider(
      "Cinematic Camera", 0f, 0f, 100f, 1f, "Amount of cinematic camera smoothing while zooming (0 disables it)")));

  public final Setting.Slider secondaryZoomAmount = grouped("Secondary Zoom", add(new Setting.Slider(
      "Secondary Zoom Amount", 4f, 1f, 50f, 1f, "How many times closer the secondary zoom is")));
  public final Setting.Slider secondaryZoomInTime = grouped("Secondary Zoom", add(new Setting.Slider(
      "Secondary Zoom In Time", 10f, 0f, 30f, 0.5f, "Seconds taken to reach full secondary zoom")));
  public final Setting.Slider secondaryZoomOutTime = grouped("Secondary Zoom", add(new Setting.Slider(
      "Secondary Zoom Out Time", 1f, 0f, 30f, 0.5f, "Seconds taken to return to normal")));
  public final Setting.Toggle secondaryHideHud = grouped("Secondary Zoom", add(new Setting.Toggle(
      "Hide HUD On Secondary Zoom", true, "Hide the HUD while the secondary zoom is held")));

  private static final String[] KEY_NAMES = {"Zoom", "Secondary Zoom", "Zoom In", "Zoom Out"};
  private static final int[] KEY_DEFAULTS = {Keyboard.KEY_C, Keyboard.KEY_NONE, Keyboard.KEY_NONE, Keyboard.KEY_NONE};

  private KeyBinding[] keys;
  private boolean keysTried;

  private final ZoomHelper zoom = new ZoomHelper(this, false);
  private final ZoomHelper secondaryZoom = new ZoomHelper(this, true);
  private boolean zooming, secondaryZooming;
  private int scrollSteps;
  private double divisor = 1.0, lastWorldDivisor = 1.0;
  private boolean handPass;

  public PolyZoomModule() { super("PolyZoom", true); }

  @Override public String category() { return "VISUALS"; }

  /** Registers the four key bindings once the settings object exists (also called while the card is off, so the keys stay listed). */
  public void ensureKeys() {
    if (keys != null || keysTried || mc.gameSettings == null) return;
    keysTried = true;
    try {
      KeyBinding[] made = new KeyBinding[KEY_NAMES.length];
      for (int i = 0; i < made.length; i++) made[i] = new KeyBinding(KEY_NAMES[i], KEY_DEFAULTS[i], "luka");
      File options = new File(mc.mcDataDir, "options.txt");
      if (options.isFile()) for (String line : Files.readAllLines(options.toPath(), StandardCharsets.UTF_8)) {
        for (int i = 0; i < made.length; i++) {
          String prefix = "key_" + KEY_NAMES[i] + ":";
          if (line.startsWith(prefix)) {
            try { made[i].setKeyCode(Integer.parseInt(line.substring(prefix.length()).trim())); }
            catch (NumberFormatException ignored) { }
          }
        }
      }
      KeyBinding[] old = mc.gameSettings.keyBindings;
      KeyBinding[] all = Arrays.copyOf(old, old.length + made.length);
      System.arraycopy(made, 0, all, old.length, made.length);
      mc.gameSettings.keyBindings = all;
      KeyBinding.resetKeyBindingArrayAndHash();
      keys = made;
    } catch (Throwable ignored) { keys = null; }
  }

  private boolean toggling() { return "toggle".equals(zoomKeyBehaviour.get()); }

  /** Once per client tick (Minecraft.runTick return), whether or not the card is on, so a disabled mod eases back to normal. */
  public void clientTick() {
    ensureKeys();
    boolean on = enabled();
    if (on && keys != null) {
      boolean mainPressed = keys[0].isPressed();
      boolean mainDown = keys[0].isKeyDown();
      boolean secPressed = keys[1].isPressed();
      boolean secDown = keys[1].isKeyDown();
      boolean inPressed = keys[2].isPressed();
      boolean outPressed = keys[3].isPressed();
      if (!toggling()) {
        zooming = mainDown || mainPressed;
        secondaryZooming = secDown || secPressed;
      } else {
        if (mainPressed) zooming = !zooming;
        if (secPressed) secondaryZooming = !secondaryZooming;
      }
      if (inPressed) addScrollStep(1);
      if (outPressed) addScrollStep(-1);
    }
    if (!on) {
      zooming = secondaryZooming = false;
      scrollSteps = 0;
    }
    addScrollStep(0);
    zoom.tick(zooming, scrollSteps, 0.05);
    secondaryZoom.tick(secondaryZooming, scrollSteps, 0.05);
  }

  private void addScrollStep(int delta) {
    int max = Math.round(scrollStepCount.get());
    int min = zoom.minTiers();
    int next = scrollSteps + delta;
    scrollSteps = next < min ? min : (next > max ? max : next);
  }

  /** Mouse wheel hook: returns 0 when the wheel was spent on zoom steps, otherwise the wheel unchanged. */
  public int consumeScroll(int dWheel) {
    if (dWheel == 0 || !enabled() || !zooming || !scrollZoom.get() || keybindScrolling.get()) return dWheel;
    addScrollStep(dWheel > 0 ? 1 : -1);
    return 0;
  }

  /** EntityRenderer.getFOVModifier return hook; runs after FOV Changer because it only sees the finished value. */
  public float zoomFov(float fov, float tickDelta, boolean worldPass) {
    if (!zooming) {
      if (!retainZoomSteps.get()) scrollSteps = 0;
      zoom.reset();
    }
    divisor = zoom.getZoomDivisor(tickDelta) * secondaryZoom.getZoomDivisor(tickDelta);
    handPass = !worldPass;
    if (!worldPass) return affectHandFov.get() ? (float) (fov / divisor) : fov;
    if (divisor != lastWorldDivisor) {
      lastWorldDivisor = divisor;
      try { if (mc.renderGlobal != null) mc.renderGlobal.setDisplayListEntitiesDirty(); } catch (Throwable ignored) { }
    }
    return (float) (fov / divisor);
  }

  public boolean useCinematicCamera() { return secondaryZooming || (zooming && cinematicCamera.get() > 0f); }

  public float cinematicSmoothness(float smoother) {
    float amount = cinematicCamera.get();
    if (!zooming || amount <= 0f) return smoother;
    return (float) (smoother / (amount / 100.0));
  }

  public float relativeSensitivity(float sensitivity) {
    if (divisor == 1.0) return sensitivity;
    return (float) (sensitivity / ZoomHelper.lerp(relativeSensitivity.get() / 100.0, 1.0, divisor));
  }

  public float relativeViewBobbing(float bob) {
    if (!relativeViewBobbing.get()) return bob;
    if (handPass) return affectHandFov.get() ? (float) (bob / divisor) : bob;
    return (float) (bob / ZoomHelper.lerp(0.2, 1.0, divisor));
  }

  public boolean hideHud() { return secondaryZooming && secondaryHideHud.get(); }
}
