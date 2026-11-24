package dev.cbui.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.cbui.Cbui;
import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.GlobalStyle;
import dev.cbui.hud.Setting;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Named looks for the STYLE tab: each preset holds the global style values ({@link GlobalStyle}) and the client-wide
 * text shadow, one JSON file per preset in {@code config/cbui/style-presets/} (next to the profiles). The name of the
 * preset last applied or saved is kept in {@code active.txt} in the same folder. Dark and Light are written the first
 * time the folder is made; after that they are ordinary presets the player can rename or delete.
 */
public final class StylePresets {
  /** The client-wide shadow switch shown on the STYLE tab; also every module's own "Text shadow". */
  public static final String TEXT_SHADOW = "Text shadow";
  private static ProfileFiles files;
  private static String active = "";

  private StylePresets() {}

  private static synchronized ProfileFiles files() {
    if (files != null) return files;
    File dir = new File(Minecraft.getMinecraft().mcDataDir, "config/cbui/style-presets");
    files = new ProfileFiles(dir, "Preset");
    if (!dir.isDirectory()) {
      try {
        files.save("Dark", dark());
        files.save("Light", light());
      } catch (IOException error) {
        Cbui.reportOnce("style presets: defaults", error);
      }
    }
    try {
      File marker = activeFile();
      if (marker.isFile()) active = new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8).trim();
    } catch (IOException error) {
      Cbui.reportOnce("style presets: active", error);
    }
    return files;
  }

  private static File activeFile() {
    return new File(files.directory(), "active.txt");
  }

  public static List<String> list() {
    try {
      return files().list();
    } catch (Throwable error) {
      Cbui.reportOnce("style presets: list", error);
      return Collections.emptyList();
    }
  }

  /** The preset last applied or saved, or "" when none. */
  public static String active() {
    files();
    return active;
  }

  private static void active(String name) {
    active = name == null ? "" : name;
    try {
      ProfileFiles f = files();
      File dir = f.directory();
      if (!dir.isDirectory() && !dir.mkdirs()) return;
      Files.write(activeFile().toPath(), active.getBytes(StandardCharsets.UTF_8));
    } catch (Throwable error) {
      Cbui.reportOnce("style presets: active", error);
    }
  }

  /** Saves the current look as a new preset and marks it active. */
  public static void create(String name, List<AbstractHudModule> modules) throws IOException {
    files().create(name, snapshot(modules));
    active(name);
  }

  public static void rename(String from, String to) throws IOException {
    files().rename(from, to);
    if (from.equals(active)) active(to);
  }

  public static void delete(String name) throws IOException {
    files().delete(name);
    if (name.equals(active)) active("");
  }

  /** Writes a preset's values to every HUD module (and the text shadow switch) and marks it active. */
  public static void apply(String name, List<AbstractHudModule> modules) throws IOException {
    JsonObject root = files().load(name);
    JsonObject values = root == null ? null : root.getAsJsonObject("style");
    if (values == null) throw new IOException("Not a style preset: " + name);
    for (Setting<?> global : GlobalStyle.build(modules)) {
      if (!values.has(global.name())) continue;
      if (read(global, values.get(global.name()))) GlobalStyle.apply(global, modules);
    }
    if (values.has(TEXT_SHADOW))
      try {
        textShadow(values.get(TEXT_SHADOW).getAsBoolean(), modules);
      } catch (RuntimeException badValue) {
        // a hand-edited file: keep the current shadow
      }
    active(name);
  }

  /** The STYLE tab's text shadow row: the client-wide switch plus every module's own shadow. */
  public static void textShadow(boolean on, List<AbstractHudModule> modules) {
    GlobalOptions.textShadow = on;
    for (AbstractHudModule m : modules)
      for (Setting<?> s : m.settings())
        if (s instanceof Setting.Toggle && s.name().equals(TEXT_SHADOW)) ((Setting.Toggle) s).set(on);
  }

  /** Sets one copy from a saved value; false when the value does not fit the setting (e.g. a toggle that became a choice). */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private static boolean read(Setting setting, JsonElement value) {
    try {
      if (setting instanceof Setting.Toggle) {
        if (!value.getAsJsonPrimitive().isBoolean()) return false;
        setting.set(value.getAsBoolean());
      } else if (setting instanceof Setting.Slider) setting.set(value.getAsFloat());
      else if (setting instanceof Setting.Color) setting.set(value.getAsInt());
      else if (setting instanceof Setting.Choice || setting instanceof Setting.Text) setting.set(value.getAsString());
      else return false;
      return true;
    } catch (RuntimeException badValue) {
      return false;
    }
  }

  private static JsonObject snapshot(List<AbstractHudModule> modules) {
    JsonObject values = new JsonObject();
    for (Setting<?> s : GlobalStyle.build(modules)) {
      Object v = s.get();
      if (v instanceof Boolean) values.addProperty(s.name(), (Boolean) v);
      else if (v instanceof Number) values.addProperty(s.name(), (Number) v);
      else if (v instanceof String) values.addProperty(s.name(), (String) v);
    }
    values.addProperty(TEXT_SHADOW, GlobalOptions.textShadow);
    return wrap(values);
  }

  private static JsonObject wrap(JsonObject values) {
    JsonObject root = new JsonObject();
    root.addProperty("version", 1);
    root.add("style", values);
    return root;
  }

  /** luka's own look: dark see-through boxes, white text. */
  private static JsonObject dark() {
    JsonObject v = new JsonObject();
    v.addProperty("Background", true);
    v.addProperty("Background color", 0x6F000000);
    v.addProperty("Corner radius", 2f);
    v.addProperty("Padding", 4f);
    v.addProperty("Border", false);
    v.addProperty("Border color", 0x9F8641D5);
    v.addProperty("Chroma border", false);
    v.addProperty("Text color", 0xFFFFFFFF);
    v.addProperty("Chroma text", false);
    v.addProperty("Chroma speed", 4f);
    v.addProperty("Minecraft font", true);
    v.addProperty(TEXT_SHADOW, true);
    return wrap(v);
  }

  /** Light boxes with dark text and a soft outline; no shadow, which smears dark letters. */
  private static JsonObject light() {
    JsonObject v = new JsonObject();
    v.addProperty("Background", true);
    v.addProperty("Background color", 0xC8F0F0F4);
    v.addProperty("Corner radius", 3f);
    v.addProperty("Padding", 4f);
    v.addProperty("Border", true);
    v.addProperty("Border color", 0x60FFFFFF);
    v.addProperty("Chroma border", false);
    v.addProperty("Text color", 0xFF1C1A22);
    v.addProperty("Chroma text", false);
    v.addProperty("Chroma speed", 4f);
    v.addProperty("Minecraft font", true);
    v.addProperty(TEXT_SHADOW, false);
    return wrap(v);
  }
}
