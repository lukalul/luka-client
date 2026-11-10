package dev.cbui.feature.betterhurtcam;

import dev.cbui.feature.FeatureModule;
import dev.cbui.hud.Setting;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/** Configurable 1.8 hurt-camera strength and style, with an on/off hotkey (the strength is a slider, so it has no keys). */
public final class BetterHurtCamModule extends FeatureModule {
  public final Setting.Toggle enableHurtCam = add(new Setting.Toggle(
      "Enable HurtCam", true, "Turn the damage-camera rotation on or off"));
  public final Setting.Slider multiplier = add(new Setting.Slider(
      "HurtCam multiplier", 1f, -10f, 10f, .1f,
      "Multiplies the damage-camera rotation; 0 removes it and negative values reverse it"));
  public final Setting.Toggle heartBlink = add(new Setting.Toggle(
      "Health bar blinking", true, "Allow the vanilla hearts to blink after damage"));
  public final Setting.Choice type = add(Setting.tip(
      new Setting.Choice("HurtCam type", "YAW_BASED", "OLD", "YAW_BASED"),
      "OLD removes the horizontal damage-direction rotation"));
  public final Setting.Toggle oldTypeIn18 = add(new Setting.Toggle(
      "Use OLD HurtCam type", true,
      "Force the legacy type on Minecraft 1.8 regardless of the type above"));

  private KeyBinding toggleKey;

  public BetterHurtCamModule() {
    super("BetterHurtCam", true);
  }

  @Override public String category() { return "VISUALS"; }

  @Override public void tick() { ensureKeys(); }

  /** Called independently of module enablement so the toggle key can turn the module back on. */
  public void pollKeys() {
    ensureKeys();
    if (toggleKey != null) while (toggleKey.isPressed()) {
      enableHurtCam.set(!enableHurtCam.get());
      status("BetterHurtCam " + (enableHurtCam.get() ? "enabled" : "disabled"));
    }
  }

  private void status(String message) {
    try {
      if (mc.thePlayer != null && mc.ingameGUI != null) mc.ingameGUI.setRecordPlaying(message, false);
    } catch (Throwable ignored) { }
  }

  private void ensureKeys() {
    if (toggleKey != null || mc.gameSettings == null) return;
    try {
      KeyBinding toggle = new KeyBinding("Toggle HurtCam", Keyboard.KEY_NONE, "BetterHurtCam");
      restoreCodes(toggle);
      KeyBinding[] old = mc.gameSettings.keyBindings;
      KeyBinding[] all = Arrays.copyOf(old, old.length + 1);
      all[old.length] = toggle;
      mc.gameSettings.keyBindings = all;
      KeyBinding.resetKeyBindingArrayAndHash();
      toggleKey = toggle;
    } catch (Throwable ignored) {
      toggleKey = null;
    }
  }

  private void restoreCodes(KeyBinding... keys) throws Exception {
    File options = new File(mc.mcDataDir, "options.txt");
    if (!options.isFile()) return;
    for (String line : Files.readAllLines(options.toPath(), StandardCharsets.UTF_8)) {
      for (KeyBinding key : keys) {
        String prefix = "key_" + key.getKeyDescription() + ":";
        if (line.startsWith(prefix)) try {
          key.setKeyCode(Integer.parseInt(line.substring(prefix.length()).trim()));
        } catch (NumberFormatException ignored) { }
      }
    }
  }
}
