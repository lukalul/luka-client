package dev.cbui.feature.tntcountdown;

import dev.cbui.feature.FeatureModule;
import dev.cbui.hud.Setting;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.entity.item.EntityTNTPrimed;

public final class TNTCountdownModule extends FeatureModule {
  public static final String[] FORMATS = {"%VALUE%", "%VALUE%s", "%VALUE%t", "%VALUE%ms", "[%VALUE%]", "TNT %VALUE%", "TNT: %VALUE%s", "Fuse: %VALUE%s", "%VALUE% sec"};
  public final Setting.Choice units = add(Setting.tip(new Setting.Choice("Time Display Units", "Seconds", "Milliseconds", "Ticks", "Seconds"), "Show the time left in milliseconds, ticks or seconds."));
  public final Setting.Choice format = add(Setting.tip(new Setting.Choice("Countdown Text Format", FORMATS[0], FORMATS), "How the countdown reads. %VALUE% is the remaining time."));
  public final Setting.Slider precision = add(new Setting.Slider("Decimal Precision", 2f, 0f, 3f, 1f, "How many decimal places the countdown shows."));
  public final Setting.Slider fuseOffset = add(new Setting.Slider("Fuse Offset", 0f, -80f, 80f, 1f, "Ticks to add or remove so the countdown matches the server's TNT fuse."));
  public final Setting.Color startColor = grouped("Color", add(new Setting.Color("Start Color", 0xFF00FF00, "The colour when the countdown begins.")));
  public final Setting.Color endColor = grouped("Color", add(new Setting.Color("End Color", 0xFFFF0000, "The colour the countdown fades into as it approaches zero.")));
  public final Setting.Slider scale = add(new Setting.Slider("Scale", 1f, .5f, 3f, .1f, "Size of the countdown text (luka addition: 1 is the normal name-tag size)."));

  public static int labelRgb = -1;
  private final Map<Float, Label> labels = new HashMap<>();
  /** Labels for whole-tick values from -256 to 255, found by index: a primed TNT asks every frame, so no boxing or hashing. */
  private final Label[] byTick = new Label[512];
  private SettingsSnapshot previousSettings;

  public TNTCountdownModule() { super("TNTCountdown", true); }
  @Override public String category() { return "VISUALS"; }

  public Label labelFor(EntityTNTPrimed entity) {
    float ticks = entity.fuse + fuseOffset.get();
    String u = units.get(), f = format.get();
    int decimals = (int) precision.get().floatValue();
    float off = fuseOffset.get();
    int c1 = startColor.get(), c2 = endColor.get();
    SettingsSnapshot current = previousSettings;
    if (current == null || !current.matches(u, f, decimals, off, c1, c2)) {
      current = new SettingsSnapshot(u, f, decimals, off, c1, c2);
      labels.clear();
      java.util.Arrays.fill(byTick, null);
      previousSettings = current;
    }
    int whole = (int) ticks;
    boolean indexed = whole == ticks && whole >= -256 && whole < 256;
    Label label = indexed ? byTick[whole + 256] : labels.get(ticks);
    if (label == null) {
      label = current.makeLabel(ticks);
      if (indexed) {
        byTick[whole + 256] = label;
      } else {
        if (labels.size() >= 256) labels.clear();
        labels.put(ticks, label);
      }
    }
    return label;
  }

  public static final class Label {
    public final float ticks;
    public final String text;
    public final int rgb;
    private Label(float ticks, String text, int rgb) { this.ticks = ticks; this.text = text; this.rgb = rgb; }
  }

  private static final class SettingsSnapshot {
    private final String units, pattern;
    private final int decimals, firstColor, lastColor;
    private final float offset;

    private SettingsSnapshot(String units, String pattern, int decimals, float offset, int firstColor, int lastColor) {
      this.units = units; this.pattern = pattern; this.decimals = decimals; this.offset = offset;
      this.firstColor = firstColor; this.lastColor = lastColor;
    }

    private boolean matches(String u, String p, int d, float o, int c1, int c2) {
      return decimals == d && Float.compare(offset, o) == 0 && firstColor == c1 && lastColor == c2 && units.equals(u) && pattern.equals(p);
    }

    private Label makeLabel(float ticks) {
      float displayed = "Milliseconds".equals(units) ? ticks * 50f : "Ticks".equals(units) ? ticks : ticks / 20f;
      String value = String.format(Locale.getDefault(), "%." + decimals + "f", displayed);
      float duration = 80f + offset;
      float progress = duration == 0f ? 1f : Math.max(0f, Math.min(1f, 1f - ticks / duration));
      return new Label(ticks, pattern.replace("%VALUE%", value), interpolate(firstColor, lastColor, progress));
    }

    private static int interpolate(int from, int to, float amount) {
      int red = Math.round((from >> 16 & 255) * (1f - amount) + (to >> 16 & 255) * amount);
      int green = Math.round((from >> 8 & 255) * (1f - amount) + (to >> 8 & 255) * amount);
      int blue = Math.round((from & 255) * (1f - amount) + (to & 255) * amount);
      return red << 16 | green << 8 | blue;
    }

    @Override public boolean equals(Object other) {
      if (!(other instanceof SettingsSnapshot)) return false;
      SettingsSnapshot that = (SettingsSnapshot) other;
      return decimals == that.decimals && Float.compare(offset, that.offset) == 0 && firstColor == that.firstColor
          && lastColor == that.lastColor && units.equals(that.units) && pattern.equals(that.pattern);
    }
    @Override public int hashCode() {
      int result = units.hashCode(); result = 31 * result + pattern.hashCode(); result = 31 * result + decimals;
      result = 31 * result + Float.floatToIntBits(offset); result = 31 * result + firstColor;
      return 31 * result + lastColor;
    }
  }
}
