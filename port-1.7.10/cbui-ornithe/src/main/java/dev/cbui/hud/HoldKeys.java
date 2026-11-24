package dev.cbui.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.cbui.Cbui;
import dev.cbui.config.JsonFiles;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * The "Hold to show" key of each HUD module, kept in {@code config/cbui/hold-keys.json} (module name to key code; mouse
 * buttons are -100 + button, as in Controls). It has its own file because the main config does not read key settings
 * back. Only the chosen key is stored here; whether it is held right now is never saved.
 */
public final class HoldKeys {
  private static Map<String, Integer> keys;

  private HoldKeys() {}

  private static File file() {
    return new File(Minecraft.getMinecraft().mcDataDir, "config/cbui/hold-keys.json");
  }

  private static synchronized Map<String, Integer> keys() {
    if (keys != null) return keys;
    keys = new HashMap<String, Integer>();
    try {
      File f = file();
      if (f.isFile())
        for (Map.Entry<String, JsonElement> e : JsonFiles.read(f).entrySet()) keys.put(e.getKey(), e.getValue().getAsInt());
    } catch (Throwable error) {
      Cbui.reportOnce("hold keys load", error);
    }
    dev.cbui.config.CardRenames.moveCards(keys); // keys saved under a card's name before it was renamed
    return keys;
  }

  /** The module's hold key, 0 when it has none. */
  public static int get(String module) {
    Integer code = keys().get(module);
    return code == null ? 0 : code;
  }

  /** Sets (or with 0 clears) the module's hold key and writes the file. */
  public static synchronized void set(String module, int code) {
    Map<String, Integer> map = keys();
    Integer old = code == 0 ? map.remove(module) : map.put(module, code);
    if (old != null && old == code) return;
    if (old == null && code == 0) return;
    try {
      JsonObject root = new JsonObject();
      for (Map.Entry<String, Integer> e : map.entrySet()) root.addProperty(e.getKey(), e.getValue());
      File f = file();
      if (!f.getParentFile().isDirectory()) f.getParentFile().mkdirs();
      JsonFiles.writeAtomic(f, root);
    } catch (Throwable error) {
      Cbui.reportOnce("hold keys save", error);
    }
  }
}
