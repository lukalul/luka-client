package dev.cbui.feature.staff;

import dev.cbui.Cbui;
import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/**
 * The Staff Mode card. It is only listed in the mods menu while staff mode is on (Envoy detects it from Apollo's staff-mod message or
 * the "Mod Mode" tag). It holds the sticky-nametag options (merged in from Envoy, see BridgeFeatures) and Lunar's X-Ray staff mod,
 * which only works while the server has granted X-Ray through Apollo.
 */
public final class StaffModeModule extends FeatureModule implements Features.Synced {
  private static final String STATE = "dev.envoy.module.staff.StaffState";

  public final Setting.Toggle xray = grouped("X-Ray", add(new Setting.Toggle("X-Ray", false,
      "Makes the terrain see-through while ores, spawners, chests and lava stay visible. Only works while the server has granted X-Ray (Apollo staff mods)")));
  public final Setting.Slider opacity = grouped("X-Ray", add(new Setting.Slider("Block opacity", 0.15f, 0.03f, 0.6f, 0.01f, "How solid the terrain stays while X-Ray is on (low = easier to see through)")));
  private final Setting.Toggle[] kinds = new Setting.Toggle[XRayState.NAMES.length];

  /** The two staff keybinds. They exist in Controls (and in the mods menu) only while staff mode is on. */
  private static final String[] KEY_LABELS = {"Toggle X-Ray", "Toggle Staff Nametags"};
  private static final int XRAY_KEY = 0, TAGS_KEY = 1;
  private final KeyBinding[] keys = new KeyBinding[KEY_LABELS.length];
  /** The key the player chose for each, kept in config/luka-staff-keys.txt because Minecraft forgets a bind that is not registered. */
  private final int[] chosenCodes = new int[KEY_LABELS.length];
  private boolean keysRegistered, codesLoaded, keyWasDown, started;
  private final java.util.Map<String, Method> stateCalls = new java.util.HashMap<>();

  public StaffModeModule() {
    super("Staff Mode", true);
    for (int i = 0; i < kinds.length; i++)
      kinds[i] = grouped("X-Ray blocks", add(new Setting.Toggle(XRayState.NAMES[i], true, "Show " + XRayState.NAMES[i].toLowerCase() + " in X-Ray")));
  }

  @Override public String category() { return "UTILITY"; }

  /** Listed in the menu only while staff mode is on. */
  @Override
  public boolean visible() {
    return call("active");
  }

  /** Asks Envoy's StaffState a yes/no question ("active", "allowsXray", "allowsNametags"); false if Envoy is missing. */
  private boolean call(String method) {
    try {
      Method m = stateCalls.get(method);
      if (m == null) {
        m = Class.forName(STATE, true, getClass().getClassLoader()).getMethod(method);
        stateCalls.put(method, m);
      }
      return Boolean.TRUE.equals(m.invoke(null));
    } catch (Throwable error) {
      return false;
    }
  }

  private boolean xrayOn() {
    return enabled() && xray.get() && call("allowsXray");
  }

  /** One chat line so a keybind press is never silent. */
  private void announce(String what, boolean on, boolean granted) {
    String note = on && !granted ? " §7(the server has not granted it, so nothing shows yet)" : "";
    say(what + ": " + (on ? "§aon" : "§coff") + note);
  }

  private void say(String text) {
    try {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("§5[luka] §f" + text));
    } catch (Throwable error) {
      Cbui.reportOnce("staff key message", error);
    }
  }

  private int wantedMask() {
    int mask = 0;
    for (int i = 0; i < kinds.length; i++) if (kinds[i].get()) mask |= 1 << i;
    return mask;
  }

  @Override
  public void sync() {
    Minecraft mc = Minecraft.getMinecraft();
    if (!started) { started = true; xray.set(false); } // X-Ray is always off at launch; only the keybind (or the switch) turns it on
    if (mc.gameSettings != null) {
      loadChosenCodes(mc);
      boolean staffMode = call("active");
      if (staffMode && !keysRegistered) registerKeys(mc);
      else if (!staffMode && keysRegistered) unregisterKeys(mc);
      if (keysRegistered) pollKeys(mc);
    }
    boolean running = xrayOn();
    int mask = wantedMask();
    XRayState.opacity = Math.max(0.03f, Math.min(0.6f, opacity.get()));
    // The terrain mesh differs while X-Ray is on (forced ore faces, ores in the translucent pass), so a change of state or categories re-meshes it.
    boolean remesh = running != XRayState.active || (running && mask != XRayState.mask);
    XRayState.mask = mask;
    XRayState.active = running;
    if (remesh) {
      try {
        mc.addScheduledTask(new Runnable() {
          @Override public void run() {
            try {
              if (mc.renderGlobal != null && mc.theWorld != null) mc.renderGlobal.loadRenderers();
            } catch (Throwable error) {
              Cbui.reportOnce("x-ray remesh", error);
            }
          }
        });
      } catch (Throwable error) {
        Cbui.reportOnce("x-ray remesh", error);
      }
    }
  }

  /** Flips the "Sticky nametags" switch on this card (the staff nametags), like the X-Ray key flips X-Ray. */
  private void toggleStickyTags() {
    for (dev.cbui.hud.Setting<?> setting : settings()) {
      if (setting instanceof Setting.Toggle && "Sticky nametags".equals(setting.name())) {
        Setting.Toggle toggle = (Setting.Toggle) setting;
        toggle.set(!toggle.get());
        announce("Staff nametags", toggle.get(), call("allowsNametags"));
        return;
      }
    }
    say("Staff nametags: the sticky-nametag switch is missing from this card, so nothing changed");
  }

  // ---- the keybinds: present only while staff mode is on ----

  /** Reads the chosen keys once at start-up, taking them from the old place (options.txt) if this is the first run with this version. */
  private void loadChosenCodes(Minecraft mc) {
    if (codesLoaded) return;
    codesLoaded = true;
    boolean[] found = new boolean[KEY_LABELS.length];
    try {
      File file = new File(mc.mcDataDir, "config/luka-staff-keys.txt");
      if (file.isFile()) {
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
          int split = line.lastIndexOf('=');
          if (split <= 0) continue;
          String label = line.substring(0, split).trim();
          for (int i = 0; i < KEY_LABELS.length; i++) {
            if (!KEY_LABELS[i].equals(label)) continue;
            try { chosenCodes[i] = Integer.parseInt(line.substring(split + 1).trim()); found[i] = true; } catch (NumberFormatException ignored) { }
          }
        }
      }
      File options = new File(mc.mcDataDir, "options.txt");
      if (options.isFile()) {
        for (String line : Files.readAllLines(options.toPath(), StandardCharsets.UTF_8)) {
          for (int i = 0; i < KEY_LABELS.length; i++) {
            String prefix = "key_" + KEY_LABELS[i] + ":";
            if (found[i] || !line.startsWith(prefix)) continue;
            try { chosenCodes[i] = Integer.parseInt(line.substring(prefix.length()).trim()); } catch (NumberFormatException ignored) { }
          }
        }
      }
      saveChosenCodes(mc);
    } catch (Throwable error) {
      Cbui.reportOnce("staff key codes", error);
    }
  }

  private void saveChosenCodes(Minecraft mc) {
    try {
      StringBuilder text = new StringBuilder();
      for (int i = 0; i < KEY_LABELS.length; i++) text.append(KEY_LABELS[i]).append('=').append(chosenCodes[i]).append('\n');
      File file = new File(mc.mcDataDir, "config/luka-staff-keys.txt");
      File parent = file.getParentFile();
      if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return;
      Files.write(file.toPath(), text.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Throwable error) {
      Cbui.reportOnce("staff key codes", error);
    }
  }

  /** Adds the two keybinds to Controls (staff mode just started). */
  private void registerKeys(Minecraft mc) {
    KeyBinding[] old = mc.gameSettings.keyBindings;
    KeyBinding[] all = Arrays.copyOf(old, old.length + KEY_LABELS.length);
    for (int i = 0; i < KEY_LABELS.length; i++) {
      if (keys[i] == null) keys[i] = new KeyBinding(KEY_LABELS[i], Keyboard.KEY_NONE, name());
      keys[i].setKeyCode(chosenCodes[i]);
      all[old.length + i] = keys[i];
    }
    mc.gameSettings.keyBindings = all;
    KeyBinding.resetKeyBindingArrayAndHash();
    keysRegistered = true;
  }

  /** Takes them out again (staff mode ended), remembering any key the player picked meanwhile. */
  private void unregisterKeys(Minecraft mc) {
    for (int i = 0; i < KEY_LABELS.length; i++) {
      if (keys[i] != null) {
        chosenCodes[i] = keys[i].getKeyCode();
        keys[i].setKeyCode(Keyboard.KEY_NONE); // so the hidden bind can never swallow a key press
      }
    }
    saveChosenCodes(mc);
    java.util.List<KeyBinding> kept = new java.util.ArrayList<>(Arrays.asList(mc.gameSettings.keyBindings));
    kept.removeAll(Arrays.asList(keys));
    mc.gameSettings.keyBindings = kept.toArray(new KeyBinding[kept.size()]);
    KeyBinding.resetKeyBindingArrayAndHash();
    keysRegistered = false;
    keyWasDown = false;
  }

  private void pollKeys(Minecraft mc) {
    boolean changed = false;
    for (int i = 0; i < KEY_LABELS.length; i++) {
      int code = keys[i].getKeyCode();
      if (code != chosenCodes[i]) { chosenCodes[i] = code; changed = true; }
    }
    if (changed) saveChosenCodes(mc);
    KeyBinding xrayKey = keys[XRAY_KEY], tagKey = keys[TAGS_KEY];
    if (xrayKey.getKeyCode() != 0 && mc.currentScreen == null) {
      boolean down = xrayKey.isKeyDown(); // KeyBinding state, so a mouse button works too (Keyboard.isKeyDown would throw on a negative code)
      if (down && !keyWasDown) {
        xray.set(!xray.get());
        announce("X-Ray", xray.get(), call("allowsXray"));
      }
      keyWasDown = down;
    }
    if (tagKey.getKeyCode() != 0 && mc.currentScreen == null) {
      while (tagKey.isPressed()) toggleStickyTags();
    }
  }
}
