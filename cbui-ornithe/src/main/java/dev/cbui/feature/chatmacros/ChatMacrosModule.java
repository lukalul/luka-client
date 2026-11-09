package dev.cbui.feature.chatmacros;

import dev.cbui.feature.FeatureModule;
import dev.cbui.hud.Setting;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;

/** Eight configurable, rate-limited chat and command hotkeys. */
public final class ChatMacrosModule extends FeatureModule {
  @SuppressWarnings("unchecked")
  public final Setting.Text[] macros = new Setting.Text[8];
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
      macros[i] = add(new Setting.Text("Macro " + (i + 1), "", tip));
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
          mc.thePlayer.getName(),
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

  private void ensureKeys() {
    if (keys != null || mc.gameSettings == null) return;
    try {
      KeyBinding[] created = new KeyBinding[8];
      for (int i = 0; i < created.length; i++)
        created[i] = new KeyBinding("Macro " + (i + 1), Keyboard.KEY_NONE, "Chat Macros");
      restoreCodes(created);
      KeyBinding[] old = mc.gameSettings.keyBindings;
      KeyBinding[] all = Arrays.copyOf(old, old.length + created.length);
      System.arraycopy(created, 0, all, old.length, created.length);
      mc.gameSettings.keyBindings = all;
      KeyBinding.resetKeyBindingArrayAndHash();
      keys = created;
    } catch (Throwable ignored) { keys = null; }
  }

  private void restoreCodes(KeyBinding[] bindings) throws Exception {
    File options = new File(mc.mcDataDir, "options.txt");
    if (!options.isFile()) return;
    for (String line : Files.readAllLines(options.toPath(), StandardCharsets.UTF_8)) {
      for (KeyBinding key : bindings) {
        String prefix = "key_" + key.getKeyDescription() + ":";
        if (line.startsWith(prefix)) try {
          key.setKeyCode(Integer.parseInt(line.substring(prefix.length()).trim()));
        } catch (NumberFormatException ignored) { }
      }
    }
  }
}
