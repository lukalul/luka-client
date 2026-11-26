package dev.cbui.feature.chatmacros;

import com.google.gson.JsonObject;
import dev.cbui.config.JsonFiles;
import dev.cbui.feature.FeatureModule;
import dev.cbui.hud.Setting;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;

/**
 * Eight configurable, rate-limited chat and command hotkeys. Each macro's key is set on this card only: the keys are not
 * listed in Options > Controls or on the Keybinds card, and are kept in {@code config/cbui/macro-keys.json} (until
 * 1.6.8 they were Controls keys in options.txt; those codes are read once when that file does not exist yet).
 */
public final class ChatMacrosModule extends FeatureModule {
  @SuppressWarnings("unchecked")
  public final Setting.Text[] macros = new Setting.Text[8];
  /** The key of each macro, edited on the card (mouse buttons are -100 + button, as in Controls). */
  private final Setting.Key[] keyRows = new Setting.Key[8];
  private final int[] codes = new int[8];
  public final Setting.Slider cooldown = add(new Setting.Slider(
      "Cooldown (ms)", 1000f, 200f, 5000f, 100f,
      "Send preset chat messages or /commands with eight keys; this is the shared delay between sends"));
  public final Setting.Toggle sendInMenus = add(new Setting.Toggle(
      "Send in menus", false, "Allow macro keys while a menu or chat screen is open"));
  public final Setting.Toggle showBlockedMessage = add(new Setting.Toggle(
      "Show a message when a macro is blocked by the cooldown", false,
      "Show a local status message when the shared cooldown blocks a macro"));

  private KeyBinding[] keys;
  private long lastSent;
  private long lastServerBlocked;

  public ChatMacrosModule() {
    super("Chat Macros", true);
    for (int i = 0; i < macros.length; i++) {
      String tip = i == 0
          ? "Send preset chat messages or /commands with a key; placeholders: {x} {y} {z} {name} {health}"
          : "Text sent by Macro " + (i + 1) + "; leave empty to disable this slot";
      macros[i] = grouped("Macro " + (i + 1), add(new Setting.Text("Macro " + (i + 1), "", tip)));
      keyRows[i] = grouped("Macro " + (i + 1), add(new Setting.Key("Macro " + (i + 1) + " key", 0,
          "Click, then press the key that sends this macro; Escape clears it")));
    }
    loadCodes();
    for (int i = 0; i < keyRows.length; i++) {
      final int slot = i;
      keyRows[i].bind(new Setting.Key.Binding() {
        @Override public int get() { return codes[slot]; }
        @Override public void set(int key) {
          if (codes[slot] == key) return;
          codes[slot] = key;
          if (keys != null) {
            keys[slot].setKeyCode(key);
            KeyBinding.resetKeyBindingArrayAndHash();
          }
          saveCodes();
        }
      });
    }
  }

  @Override public String category() { return "UTILITY"; }

  @Override public void tick() {
    ensureKeys();
    if (keys == null || mc.thePlayer == null || mc.theWorld == null) return;
    for (int i = 0; i < keys.length; i++) {
      if (!keys[i].isPressed()) continue;
      String source = macros[i].get();
      if (source == null || source.isEmpty()) continue;
      if (!sendInMenus.get() && mc.currentScreen != null) continue;
      long now = System.currentTimeMillis();
      if (lastSent != 0L && now >= lastSent && now - lastSent < (long) cooldown.get().floatValue()) {
        if (showBlockedMessage.get()) blocked();
        break;
      }
      String message = MacroText.expand(
          source,
          MathHelper.floor_double(mc.thePlayer.posX),
          MathHelper.floor_double(mc.thePlayer.posY),
          MathHelper.floor_double(mc.thePlayer.posZ),
          mc.thePlayer.getCommandSenderName(),
          mc.thePlayer.getHealth());
      if (!message.isEmpty()) {
        if (dev.cbui.server.ServerRulesBridge.macrosBlocked(message)) {
          if (now - lastServerBlocked >= 1000L) {
            lastServerBlocked = now;
            try { mc.thePlayer.addChatMessage(new ChatComponentText("\u00a75[luka] \u00a7fThis server does not allow that macro")); }
            catch (Throwable ignored) { }
          }
          break;
        }
        mc.thePlayer.sendChatMessage(message);
        lastSent = now;
      }
      break;
    }
  }

  private void blocked() {
    try {
      if (mc.ingameGUI != null) mc.ingameGUI.setRecordPlaying("Chat macro blocked by cooldown", false);
      else mc.thePlayer.addChatMessage(new ChatComponentText("Chat macro blocked by cooldown"));
    } catch (Throwable ignored) { }
  }

  /**
   * The macro keys exist as key bindings (so presses are seen like any other key) but are not added to the game's
   * key list, which is what Options > Controls and the Keybinds card show.
   */
  private void ensureKeys() {
    if (keys != null || mc.gameSettings == null) return;
    try {
      KeyBinding[] created = new KeyBinding[8];
      for (int i = 0; i < created.length; i++) created[i] = new KeyBinding("Macro " + (i + 1), codes[i], "luka"); // an existing category: no empty Controls heading
      KeyBinding.resetKeyBindingArrayAndHash();
      keys = created;
    } catch (Throwable ignored) { keys = null; }
  }

  private File codesFile() {
    return new File(mc.mcDataDir, "config/cbui/macro-keys.json");
  }

  private void loadCodes() {
    try {
      File file = codesFile();
      if (file.isFile()) {
        JsonObject root = JsonFiles.read(file);
        for (int i = 0; i < codes.length; i++) {
          String id = "Macro " + (i + 1);
          if (root.has(id)) codes[i] = root.get(id).getAsInt();
        }
        return;
      }
      // first start with this version: take the keys the macros had in Controls (options.txt)
      File options = new File(mc.mcDataDir, "options.txt");
      if (!options.isFile()) return;
      boolean found = false;
      for (String line : Files.readAllLines(options.toPath(), StandardCharsets.UTF_8)) {
        for (int i = 0; i < codes.length; i++) {
          String prefix = "key_Macro " + (i + 1) + ":";
          if (line.startsWith(prefix)) try {
            codes[i] = Integer.parseInt(line.substring(prefix.length()).trim());
            found = true;
          } catch (NumberFormatException ignored) { }
        }
      }
      if (found) saveCodes();
    } catch (Throwable error) {
      dev.cbui.Cbui.reportOnce("macro keys load", error);
    }
  }

  private void saveCodes() {
    try {
      JsonObject root = new JsonObject();
      for (int i = 0; i < codes.length; i++) if (codes[i] != 0) root.addProperty("Macro " + (i + 1), codes[i]);
      File file = codesFile();
      if (!file.getParentFile().isDirectory()) file.getParentFile().mkdirs();
      JsonFiles.writeAtomic(file, root);
    } catch (Throwable error) {
      dev.cbui.Cbui.reportOnce("macro keys save", error);
    }
  }
}
