package dev.cbui.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;

/**
 * Puts the keybinds that mods register with Minecraft (the ones otherwise only reachable in Options > Controls) into the
 * mods menu: one "Keybinds" card, a heading per key category.
 */
final class ModKeys {
  private ModKeys() {}

  /** The "Keybinds" card, or null when no mod registered a key of its own. */
  static ConfigSnapshot.Mod card() {
    Map<String, List<ConfigSnapshot.Row>> byCategory = new LinkedHashMap<>();
    KeyBinding[] all = Minecraft.getMinecraft().gameSettings.keyBindings;
    for (KeyBinding binding : all) {
      String category = binding.getKeyCategory();
      if (category == null || category.startsWith("key.categories")) continue;
      String id = readable(category);
      if (id.equalsIgnoreCase("Envoy")) continue; // Envoy's keys are already on the Waypoints card
      List<ConfigSnapshot.Row> rows = byCategory.get(id);
      if (rows == null) {
        rows = new ArrayList<>();
        byCategory.put(id, rows);
      }
      rows.add(row(binding));
    }
    // ONE card with a heading per category. (A card per category used to be created, named after the mod: "Motion
    // Blur" next to "Motion Blur keys" looked like the same mod twice.)
    List<ConfigSnapshot.Section> sections = new ArrayList<>();
    for (Map.Entry<String, List<ConfigSnapshot.Row>> entry : byCategory.entrySet())
      sections.add(new ConfigSnapshot.Section(entry.getKey(), entry.getValue()));
    return sections.isEmpty() ? null : new ConfigSnapshot.Mod("keys:all", "Keybinds", "Mods", sections);
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
}
