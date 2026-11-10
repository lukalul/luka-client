package dev.cbui.feature.bridge;

import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

/**
 * A luka card that edits another mod's own settings (static fields of its settings class) directly, so that mod needs no
 * config library of its own: the values live in luka's config and are pushed into the mod every tick they change.
 * If the other mod is not installed the card quietly does nothing.
 */
public final class BridgeModule extends FeatureModule implements Features.Synced {
  private enum Kind { TOGGLE, SLIDER, CHOICE, COLOR, KEY }

  private static final class Bind {
    final Kind kind;
    final Setting<?> setting;
    final String owner, field;
    final int keyDefault;
    Field target;
    KeyBinding key;
    Object applied;

    Bind(Kind kind, Setting<?> setting, String owner, String field, int keyDefault) {
      this.kind = kind;
      this.setting = setting;
      this.owner = owner;
      this.field = field;
      this.keyDefault = keyDefault;
    }
  }

  private final String settingsClass, enabledField, enabledMethodClass, enabledMethod, category;
  private final List<Bind> binds = new ArrayList<>();
  private final List<String[]> after = new ArrayList<>();
  private Field enabledTarget;
  private Boolean enabledApplied;
  private boolean resolved, unavailable;
  private long lastAfter;
  private boolean afterPending;
  private String currentGroup = "";

  /** The card's on/off switch is a boolean field of {@code settingsClass}. */
  public BridgeModule(String name, String category, boolean enabledByDefault, String settingsClass, String enabledField) {
    super(name, enabledByDefault);
    this.category = category;
    this.settingsClass = settingsClass;
    this.enabledField = enabledField;
    this.enabledMethodClass = null;
    this.enabledMethod = null;
  }

  /** The card's on/off switch calls {@code methodClass.method(boolean)}. */
  public BridgeModule(String name, String category, boolean enabledByDefault, String settingsClass, String methodClass, String method) {
    super(name, enabledByDefault);
    this.category = category;
    this.settingsClass = settingsClass;
    this.enabledField = null;
    this.enabledMethodClass = methodClass;
    this.enabledMethod = method;
  }

  @Override public String category() { return category; }

  private String visibleClass, visibleMethod;
  private boolean hidden;
  private Setting.Toggle hostToggle;

  /**
   * Moves this module's settings onto another card (under headings starting with {@code prefix}) and hides this module's own card;
   * the card's on/off for the other mod becomes a toggle inside the host card.
   */
  public BridgeModule embedInto(FeatureModule host, String prefix, String toggleLabel, boolean toggleDefault, String toggleTip) {
    hostToggle = new Setting.Toggle(toggleLabel, toggleDefault, toggleTip);
    hostToggle.setGroup(prefix);
    host.adopt(hostToggle);
    for (Setting<?> s : new ArrayList<Setting<?>>(settings())) {
      if (s == hostToggle) continue;
      s.setGroup(s.group().isEmpty() ? prefix : prefix + ": " + s.group());
      host.adopt(s);
    }
    hidden = true;
    return this;
  }
  private java.lang.reflect.Method visibleCall;
  private boolean visibleBroken;

  /** The card is only listed in the menu while {@code className.method()} (static, returns boolean) is true. */
  public BridgeModule visibleWhen(String className, String method) { visibleClass = className; visibleMethod = method; return this; }

  @Override
  public boolean visible() {
    if (hidden) return false;
    if (visibleClass == null) return true;
    if (visibleBroken) return false;
    try {
      if (visibleCall == null) visibleCall = Class.forName(visibleClass, true, getClass().getClassLoader()).getMethod(visibleMethod);
      return Boolean.TRUE.equals(visibleCall.invoke(null));
    } catch (Throwable error) {
      visibleBroken = true;
      return false;
    }
  }

  public BridgeModule group(String heading) { currentGroup = heading; return this; }

  /** Called (once per burst of changes, at most twice a second) after values were pushed: e.g. a save method. */
  public BridgeModule after(String className, String method) { after.add(new String[] {className, method}); return this; }

  public BridgeModule toggle(String label, String field, boolean def, String tip) {
    return bind(Kind.TOGGLE, new Setting.Toggle(label, def, tip), settingsClass, field, 0);
  }

  public BridgeModule slider(String label, String field, float def, float min, float max, float step, String tip) {
    return bind(Kind.SLIDER, new Setting.Slider(label, def, min, max, step, tip), settingsClass, field, 0);
  }

  public BridgeModule sliderIn(String owner, String label, String field, float def, float min, float max, float step, String tip) {
    return bind(Kind.SLIDER, new Setting.Slider(label, def, min, max, step, tip), owner, field, 0);
  }

  /** An int field (index) or an enum field (constant chosen by the upper-cased option text). */
  public BridgeModule choice(String label, String owner, String field, int defIndex, String tip, String... options) {
    return bind(Kind.CHOICE, Setting.tip(new Setting.Choice(label, options[defIndex], options), tip), owner == null ? settingsClass : owner, field, 0);
  }

  public BridgeModule color(String label, String field, int argb, String tip) {
    return bind(Kind.COLOR, new Setting.Color(label, argb, tip), settingsClass, field, 0);
  }

  /** A Controls key (category = the card name, so it also shows on the card) mirrored into an int key-code field. */
  public BridgeModule key(String label, String field, int defaultKey) {
    return bind(Kind.KEY, null, settingsClass, field, defaultKey).keyLabel(label);
  }

  private BridgeModule keyLabel(String label) { keyLabels.add(label); return this; }

  private final List<String> keyLabels = new ArrayList<>();

  private BridgeModule bind(Kind kind, Setting<?> setting, String owner, String field, int keyDefault) {
    if (setting != null) {
      add(setting);
      if (!currentGroup.isEmpty()) setting.setGroup(currentGroup);
    }
    binds.add(new Bind(kind, setting, owner, field, keyDefault));
    return this;
  }

  // ---- applying -----------------------------------------------------------------------------------------------------------

  @Override
  public void sync() {
    if (unavailable) return;
    try {
      if (!resolved) resolve();
      if (unavailable) return;
      boolean changed = false;
      boolean on = hostToggle != null ? hostToggle.get() : enabled();
      if (enabledApplied == null || enabledApplied != on) {
        if (enabledTarget != null) enabledTarget.setBoolean(null, on);
        else invoke(enabledMethodClass, enabledMethod, boolean.class, on);
        enabledApplied = on;
        changed = true;
      }
      for (Bind b : binds) {
        Object value = valueOf(b);
        if (value == null || value.equals(b.applied)) continue;
        write(b, value);
        b.applied = value;
        changed = true;
      }
      if (changed) afterPending = true;
      if (afterPending && System.currentTimeMillis() - lastAfter > 500) {
        afterPending = false;
        lastAfter = System.currentTimeMillis();
        for (String[] a : after) invoke(a[0], a[1], null, null);
      }
    } catch (Throwable error) {
      unavailable = true;
      dev.cbui.Cbui.reportOnce("bridge " + name(), error);
    }
  }

  private void resolve() throws Exception {
    resolved = true;
    Class<?> settings;
    try {
      settings = Class.forName(settingsClass, true, getClass().getClassLoader());
    } catch (ClassNotFoundException missing) {
      unavailable = true;
      return;
    }
    if (enabledField != null) enabledTarget = field(settings, enabledField);
    for (Bind b : binds) {
      Class<?> owner = b.owner.equals(settingsClass) ? settings : Class.forName(b.owner, true, getClass().getClassLoader());
      b.target = field(owner, b.field);
    }
  }

  private static Field field(Class<?> type, String name) throws Exception {
    Field f = type.getDeclaredField(name);
    f.setAccessible(true);
    return f;
  }

  private Object valueOf(Bind b) {
    switch (b.kind) {
      case TOGGLE: return ((Setting.Toggle) b.setting).get();
      case SLIDER: return ((Setting.Slider) b.setting).get();
      case CHOICE: return ((Setting.Choice) b.setting).get();
      case COLOR: return ((Setting.Color) b.setting).get();
      default: return keyCode(b);
    }
  }

  private Object keyCode(Bind b) {
    if (b.key == null) {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc.gameSettings == null) return null;
      int index = 0;
      for (Bind other : binds) {
        if (other == b) break;
        if (other.kind == Kind.KEY) index++;
      }
      String label = keyLabels.get(index);
      KeyBinding kb = new KeyBinding(label, b.keyDefault, name());
      File options = new File(mc.mcDataDir, "options.txt");
      try {
        if (options.isFile())
          for (String line : Files.readAllLines(options.toPath(), StandardCharsets.UTF_8)) {
            String prefix = "key_" + label + ":";
            if (line.startsWith(prefix)) kb.setKeyCode(Integer.parseInt(line.substring(prefix.length()).trim()));
          }
      } catch (Exception ignored) {
        // keep the default key
      }
      KeyBinding[] old = mc.gameSettings.keyBindings;
      KeyBinding[] all = java.util.Arrays.copyOf(old, old.length + 1);
      all[old.length] = kb;
      mc.gameSettings.keyBindings = all;
      KeyBinding.resetKeyBindingArrayAndHash();
      b.key = kb;
    }
    return b.key.getKeyCode();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void write(Bind b, Object value) throws Exception {
    Class<?> type = b.target.getType();
    if (b.kind == Kind.CHOICE && (type == int.class || type == float.class || type == double.class)) {
      // an int field holds the index of the chosen option
      value = Integer.valueOf(Math.max(0, ((Setting.Choice) b.setting).choices().indexOf((String) value)));
    }
    if (type == boolean.class) b.target.setBoolean(null, (Boolean) value);
    else if (type == int.class) b.target.setInt(null, value instanceof Float ? Math.round((Float) value) : ((Number) value).intValue());
    else if (type == float.class) b.target.setFloat(null, ((Number) value).floatValue());
    else if (type == double.class) b.target.setDouble(null, ((Number) value).doubleValue());
    else if (type.isEnum()) b.target.set(null, Enum.valueOf((Class<Enum>) type, ((String) value).toUpperCase().replace(' ', '_')));
    else if (type == String.class) b.target.set(null, String.valueOf(value));
  }

  private void invoke(String className, String method, Class<?> argType, Object arg) throws Exception {
    Class<?> c = Class.forName(className, true, getClass().getClassLoader());
    Method m = argType == null ? c.getDeclaredMethod(method) : c.getDeclaredMethod(method, argType);
    m.setAccessible(true);
    if (argType == null) m.invoke(null);
    else m.invoke(null, arg);
  }
}
