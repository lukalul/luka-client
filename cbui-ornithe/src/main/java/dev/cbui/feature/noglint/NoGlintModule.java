package dev.cbui.feature.noglint;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.minecraft.client.settings.KeyBinding;

/**
 * Turns the enchantment shine off. Every enchanted item is otherwise drawn twice (the item, then the moving shine over it),
 * so in fights full of enchanted armour and swords this saves a lot of drawing. The hooks are the Glint distance ones
 * ({@link dev.cbui.render.GlintDistance#skip()}).
 *
 * <p>A key on the card switches it on and off (shine hidden / shown), also while the card is off. Like the Chat Macros keys it
 * is not listed in Controls; its code is kept in {@code config/cbui/glint-key.txt}.
 */
public final class NoGlintModule extends FeatureModule implements Features.Synced {
  private static volatile NoGlintModule instance;

  public final Setting.Toggle hand = add(new Setting.Toggle("Hand and inventory", true,
      "No shine on the item in your hand, the hotbar and items in menus"));
  public final Setting.Toggle world = add(new Setting.Toggle("Players, armour and dropped items", true,
      "No shine on armour and held items of every player (you in third person too), mobs, item frames and items on the ground. This is the part that saves FPS"));
  private final Setting.Key keyRow = grouped("Key", add(new Setting.Key("Glint toggle key", 0,
      "Click, then press a key; pressing it in game hides or shows the enchantment shine. Escape clears it")));

  private int code;
  private KeyBinding key;

  public NoGlintModule() {
    super("No Glint", false);
    instance = this;
    code = loadCode();
    keyRow.bind(new Setting.Key.Binding() {
      @Override public int get() { return code; }

      @Override public void set(int value) {
        if (code == value) return;
        code = value;
        if (key != null) {
          key.setKeyCode(value);
          KeyBinding.resetKeyBindingArrayAndHash();
        }
        saveCode();
      }
    });
  }

  @Override public String category() { return "VISUALS"; }

  /** The module when it is on, else null. */
  public static NoGlintModule active() {
    NoGlintModule m = instance;
    return m != null && m.enabled() ? m : null;
  }

  /** Every client tick, also while the card is off: the toggle key. Key presses only count outside menus. */
  @Override
  public void sync() {
    if (key == null) {
      if (mc.gameSettings == null) return;
      key = new KeyBinding("Toggle enchant glint", code, "luka");
      KeyBinding.resetKeyBindingArrayAndHash();
    }
    boolean pressed = false;
    while (key.isPressed()) pressed = true;
    if (!pressed || code == 0 || mc.thePlayer == null) return;
    enabled(!userEnabled());
    try {
      if (dev.cbui.Cbui.config() != null && dev.cbui.Cbui.hud() != null) dev.cbui.Cbui.config().save(dev.cbui.Cbui.hud());
    } catch (Throwable error) {
      dev.cbui.Cbui.reportOnce("glint key save", error);
    }
    try {
      if (mc.ingameGUI != null) mc.ingameGUI.setRecordPlaying("Enchant glint: " + (userEnabled() ? "hidden" : "shown"), false);
    } catch (Throwable ignored) { }
  }

  private File codeFile() {
    return new File(mc.mcDataDir, "config/cbui/glint-key.txt");
  }

  private int loadCode() {
    try {
      File file = codeFile();
      if (file.isFile()) return Integer.parseInt(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim());
    } catch (Throwable ignored) { }
    return 0;
  }

  private void saveCode() {
    try {
      File file = codeFile();
      if (!file.getParentFile().isDirectory()) file.getParentFile().mkdirs();
      Files.write(file.toPath(), Integer.toString(code).getBytes(StandardCharsets.UTF_8));
    } catch (Throwable error) {
      dev.cbui.Cbui.reportOnce("glint key", error);
    }
  }
}
