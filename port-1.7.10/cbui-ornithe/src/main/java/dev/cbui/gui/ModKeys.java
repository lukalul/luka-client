package dev.cbui.gui;

import dev.cbui.oneconfig.ConfigSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;

/**
 * Puts the keybinds that mods register with Minecraft (the ones otherwise only reachable in Options > Controls) into the
 * mods menu: onto the card of the mod they belong to, or onto a card named after their category when no mod matches.
 */
final class ModKeys {
  private ModKeys() {}

  static List<ConfigSnapshot.Mod> merge(List<ConfigSnapshot.Mod> mods) {
    Map<String, List<ConfigSnapshot.Row>> byMod = new LinkedHashMap<>();
    Map<String, List<ConfigSnapshot.Row>> byCategory = new LinkedHashMap<>();
    KeyBinding[] all = Minecraft.getMinecraft().gameSettings.keyBindings;
    for (KeyBinding binding : all) {
      String category = binding.getKeyCategory();
      if (category == null || category.startsWith("key.categories")) continue;
      ConfigSnapshot.Row row = row(binding);
      ConfigSnapshot.Mod owner = ownerOf(mods, category, binding.getKeyDescription());
      Map<String, List<ConfigSnapshot.Row>> target = owner != null ? byMod : byCategory;
      String id = owner != null ? owner.id : readable(category);
      List<ConfigSnapshot.Row> rows = target.get(id);
      if (rows == null) {
        rows = new ArrayList<>();
        target.put(id, rows);
      }
      rows.add(row);
    }
    List<ConfigSnapshot.Mod> out = new ArrayList<>();
    for (ConfigSnapshot.Mod mod : mods) {
      List<ConfigSnapshot.Row> rows = byMod.get(mod.id);
      if (rows == null) {
        out.add(mod);
        continue;
      }
      List<ConfigSnapshot.Section> sections = new ArrayList<>(mod.sections);
      sections.add(new ConfigSnapshot.Section("Keybinds", rows));
      out.add(new ConfigSnapshot.Mod(mod.id, mod.name, mod.category, sections));
    }
    // Binds that belong to no mod card go onto ONE "Keybinds" card, a heading per category. (A card per category used to be created,
    // named after the mod: "Motion Blur" next to "Motion Blur keys" looked like the same mod twice.)
    List<ConfigSnapshot.Section> loose = new ArrayList<>();
    for (Map.Entry<String, List<ConfigSnapshot.Row>> entry : byCategory.entrySet()) {
      if (entry.getKey().equalsIgnoreCase("Envoy")) continue; // Envoy's keys are already on the Waypoints card
      loose.add(new ConfigSnapshot.Section(entry.getKey(), entry.getValue()));
    }
    if (!loose.isEmpty()) out.add(new ConfigSnapshot.Mod("keys:all", "Keybinds", "Mods", loose));
    return out;
  }

  private static ConfigSnapshot.Mod ownerOf(List<ConfigSnapshot.Mod> mods, String category, String description) {
    String haystack = flat(category) + "|" + flat(description);
    ConfigSnapshot.Mod best = null;
    for (ConfigSnapshot.Mod mod : mods) {
      String name = flat(mod.name);
      if (name.length() < 4 || !haystack.contains(name)) continue;
      if (best == null || name.length() > flat(best.name).length()) best = mod;
    }
    return best;
  }

  private static ConfigSnapshot.Row row(final KeyBinding binding) {
    ConfigSnapshot.Accessor accessor = new ConfigSnapshot.Accessor() {
      public Object get() {
        return binding.getKeyCode();
      }

      public boolean set(Object value) {
        int key = ((Number) value).intValue();
        if (key == binding.getKeyCode()) return false;
        binding.setKeyCode(key);
        KeyBinding.resetKeyBindingArrayAndHash();
        return true;
      }

      public ConfigSnapshot.Display display() {
        return ConfigSnapshot.Display.SHOWN;
      }

      public void save() {
        Minecraft.getMinecraft().gameSettings.saveOptions();
      }

      public void openFallback() {}
    };
    return new ConfigSnapshot.Row(
        ConfigSnapshot.Kind.KEYBIND, "key:" + binding.getKeyDescription(), I18n.format(binding.getKeyDescription()),
        "Click, then press the key to use. Escape clears it.", Double.NaN, Double.NaN, Double.NaN, null, false,
        Integer.valueOf(binding.getKeyCodeDefault()), accessor);
  }

  private static String readable(String category) {
    String text = I18n.format(category);
    return text == null || text.isEmpty() ? category : text;
  }

  private static String flat(String text) {
    return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
  }
}
