package dev.cbui.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * "Global style": one set of look settings that is applied to every HUD module at once. The settings
 * here are copies, seeded from the first module that has each one; changing a copy writes the value to
 * every module's setting of the same name and type.
 */
public final class GlobalStyle {
  private static final String[] NAMES = {
    "Background", "Background color", "Corner radius", "Padding", "Border", "Border color", "Chroma border",
    "Text color", "Chroma text", "Chroma speed", "Minecraft font", "Brackets"
  };

  public static List<Setting<?>> build(List<AbstractHudModule> modules) {
    List<Setting<?>> out = new ArrayList<>();
    for (String name : NAMES) {
      Setting<?> source = null;
      for (AbstractHudModule m : modules) {
        for (Setting<?> s : m.settings()) if (s.name().equals(name)) { source = s; break; }
        if (source != null) break;
      }
      if (source == null) continue;
      Setting<?> copy = copyOf(source);
      if (copy != null) {
        copy.setGroup(source.group());
        out.add(copy);
      }
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  private static Setting<?> copyOf(Setting<?> s) {
    if (s instanceof Setting.Toggle) {
      Setting.Toggle c = new Setting.Toggle(s.name(), (Boolean) s.defaultValue(), s.tooltip());
      c.set((Boolean) s.get());
      return c;
    }
    if (s instanceof Setting.Slider) {
      Setting.Slider o = (Setting.Slider) s;
      Setting.Slider c = new Setting.Slider(o.name(), o.defaultValue(), o.min(), o.max(), o.step(), o.tooltip());
      c.set(o.get());
      return c;
    }
    if (s instanceof Setting.Color) {
      Setting.Color c = new Setting.Color(s.name(), (Integer) s.defaultValue(), s.tooltip());
      c.set((Integer) s.get());
      return c;
    }
    if (s instanceof Setting.Choice) {
      Setting.Choice o = (Setting.Choice) s;
      Setting.Choice c = Setting.tip(new Setting.Choice(o.name(), o.defaultValue(), o.choices().toArray(new String[0])), o.tooltip());
      c.set(o.get());
      return c;
    }
    return null;
  }

  /** Writes a changed global setting to every module that has a setting of the same name and kind. */
  @SuppressWarnings("unchecked")
  public static void apply(Setting<?> global, List<AbstractHudModule> modules) {
    for (AbstractHudModule m : modules)
      for (Setting<?> s : m.settings())
        if (s.name().equals(global.name()) && s.getClass() == global.getClass())
          ((Setting<Object>) s).set(global.get());
  }

  private GlobalStyle() {}
}
