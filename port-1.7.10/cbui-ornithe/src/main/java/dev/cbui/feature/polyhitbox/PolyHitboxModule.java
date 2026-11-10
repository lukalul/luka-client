package dev.cbui.feature.polyhitbox;

import dev.cbui.Cbui;
import dev.cbui.feature.FeatureModule;
import dev.cbui.hud.Setting;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.entity.projectile.EntityWitherSkull;
import org.lwjgl.input.Keyboard;

/**
 * Configurable replacement for the vanilla F3+B hitbox display. Entities are sorted into categories; the General category is the
 * card's own settings and every other category may optionally override General's show condition and/or its visuals.
 */
public final class PolyHitboxModule extends FeatureModule implements dev.cbui.feature.Features.Synced {
  /**
   * Runs every tick even while the module is off, so the toggle keybind is always registered. A bind that is only registered while
   * the module is enabled gets dropped from options.txt the next time Minecraft saves, and the player's key is lost.
   */
  @Override public void sync() { installToggleKey(); }

  public static final String[] CONDITIONS = {"Always", "Toggled", "Hovered", "Never"};
  public static final int ALWAYS = 0, TOGGLED = 1, HOVERED = 2, NEVER = 3;

  /** Entity categories, in menu order. DEFAULT is the General block and the fallback for anything unmatched. */
  public enum Cat {
    DEFAULT("General"), PLAYER("Player"), SELF("Self"), MOB("Mob"), MONSTER("Monster"), PROJECTILE("Projectile"),
    ARROW("Arrow"), FIREBALL("Fireball"), WITHER_SKULL("Wither Skull"), FRAMES("Frames"),
    ITEM("Item"), XP("XP");

    public final String label;

    Cat(String label) { this.label = label; }
  }

  /** A category together with the test that decides whether an entity belongs to it. */
  private static final class Rule {
    final Cat cat;
    final Predicate<Entity> test;

    Rule(Cat cat, Predicate<Entity> test) {
      this.cat = cat;
      this.test = test;
    }
  }

  /** Checked top to bottom; the first hit wins, so narrower categories sit above the broad ones they overlap with. */
  private static final Rule[] RULES = {
    new Rule(Cat.SELF, e -> e instanceof EntityPlayerSP),
    new Rule(Cat.MONSTER, e -> e instanceof IMob),
    new Rule(Cat.ARROW, e -> e instanceof EntityArrow),
    new Rule(Cat.FIREBALL, e -> e instanceof EntityLargeFireball || e instanceof EntitySmallFireball),
    new Rule(Cat.WITHER_SKULL, e -> e instanceof EntityWitherSkull),
    new Rule(Cat.PLAYER, e -> e instanceof EntityPlayer),
    new Rule(Cat.MOB, e -> e instanceof EntityLiving),
    new Rule(Cat.PROJECTILE, e -> e instanceof IProjectile),
    new Rule(Cat.FRAMES, e -> e instanceof EntityHanging),
    new Rule(Cat.ITEM, e -> e instanceof EntityItem),
    new Rule(Cat.XP, e -> e instanceof EntityXPOrb),
  };

  // Options that only exist once, under the General heading.
  public final Setting.Toggle hideInF1 =
      grouped("General", add(new Setting.Toggle("Hide with HUD (F1)", true, "Hide hitboxes while the HUD is hidden with F1.")));
  public final Setting.Toggle thinWithDistance = grouped("General",
      add(new Setting.Toggle("Thin Lines with Distance", true, "Keep the hitbox line width consistent across distances.")));
  public final Setting.Toggle accurateHitboxes = grouped("General",
      add(new Setting.Toggle("Accurate Hitboxes", true, "Draw the hitbox at the size attacks actually register against (grown by the entity's pick radius).")));
  public final Setting.Toggle retainToggle = grouped("General", add(new Setting.Toggle("Retain Toggle State", true,
      "Remember whether the toggle is on across game restarts, instead of starting every session with it off.")));
  /** Flipped by F3+B or the luka key; categories whose show condition is "Toggled" draw only while this is on. */
  public final Setting.Toggle toggled = grouped("General", add(new Setting.Toggle("Toggled", false,
      "Current state of the toggle: switched with F3+B (or the \"Toggle PolyHitbox\" key). Hitboxes using the Toggled show condition are drawn while this is on.")));

  /** One style per category, indexed by {@link Cat#ordinal()}. */
  public final Style[] styles = new Style[Cat.values().length];

  private final Map<Class<?>, Style> styleByType = new HashMap<>();
  private KeyBinding toggleKey;
  private boolean keySetupFailed, retainApplied;

  public PolyHitboxModule() {
    super("PolyHitbox", false);
    for (Cat c : Cat.values()) styles[c.ordinal()] = new Style(c);
  }

  @Override public String category() { return "VISUALS"; }

  public Style style(Cat c) { return styles[c.ordinal()]; }

  private Style general() { return styles[Cat.DEFAULT.ordinal()]; }

  /** The category style an entity falls under (General fallbacks are not applied here). */
  public Style match(Entity entity) {
    Class<?> type = entity.getClass();
    Style cached = styleByType.get(type);
    if (cached != null) return cached;
    Cat chosen = Cat.DEFAULT;
    for (Rule rule : RULES) {
      if (rule.test.test(entity)) {
        chosen = rule.cat;
        break;
      }
    }
    Style resolved = style(chosen);
    styleByType.put(type, resolved);
    return resolved;
  }

  private static boolean ownsSetting(Setting.Toggle override) { return override == null || override.get(); }

  /** Whichever style's show condition governs: the category's own when it overrides General, otherwise General. */
  public Style logicOf(Style matched) { return ownsSetting(matched.overrideLogic) ? matched : general(); }

  /** Whichever style's look governs: the category's own when it overrides General, otherwise General. */
  public Style visualsOf(Style matched) { return ownsSetting(matched.overrideVisuals) ? matched : general(); }

  public boolean anyToggled() {
    for (Style s : styles) {
      if (logicOf(s).condition() == TOGGLED) return true;
    }
    return false;
  }

  /** True when at least one category can currently draw something. */
  public boolean anythingVisible() {
    for (Style s : styles) {
      if (logicOf(s).condition() == NEVER) continue;
      Style look = visualsOf(s);
      if (look.side.get() || look.outline.get() || look.eye.get() || look.viewRay.get()) return true;
    }
    return false;
  }

  /** What F3+B does while the module is on, in place of vanilla's own hitbox flag. */
  public void flipToggle() { toggled.set(!toggled.get()); }

  @Override
  public void tick() {
    if (!retainApplied) {
      retainApplied = true;
      if (!retainToggle.get()) toggled.set(false);
    }
    installToggleKey();
    pollToggleKey();
  }

  private void pollToggleKey() {
    if (toggleKey == null) return;
    try {
      if (toggleKey.isPressed()) flipToggle();
    } catch (Throwable error) {
      Cbui.reportOnce("hitbox toggle key", error);
    }
  }

  /** Registers the "Toggle PolyHitbox" keybinding once, restoring any binding already saved in options.txt. */
  private void installToggleKey() {
    if (toggleKey != null || keySetupFailed) return;
    try {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc.gameSettings == null) return;
      KeyBinding key = new KeyBinding("Toggle PolyHitbox", Keyboard.KEY_NONE, "luka");
      Integer saved = savedKeyCode(new File(mc.mcDataDir, "options.txt"), "key_" + key.getKeyDescription() + ":");
      if (saved != null) key.setKeyCode(saved);
      KeyBinding[] before = mc.gameSettings.keyBindings;
      KeyBinding[] after = Arrays.copyOf(before, before.length + 1);
      after[before.length] = key;
      mc.gameSettings.keyBindings = after;
      KeyBinding.resetKeyBindingArrayAndHash();
      toggleKey = key;
    } catch (Throwable error) {
      keySetupFailed = true;
      Cbui.reportOnce("hitbox key registration", error);
    }
  }

  /** The last parseable value saved for the given options.txt prefix, or null when none was saved. */
  private static Integer savedKeyCode(File options, String prefix) throws IOException {
    if (!options.isFile()) return null;
    Integer found = null;
    List<String> lines = Files.readAllLines(options.toPath(), StandardCharsets.UTF_8);
    for (String line : lines) {
      if (!line.startsWith(prefix)) continue;
      try {
        found = Integer.valueOf(line.substring(prefix.length()).trim());
      } catch (NumberFormatException ignored) {
        // keep whatever was parsed before this malformed line
      }
    }
    return found;
  }

  /** The settings of one category. General has no override switches; Arrow adds "Show Grounded Arrows". */
  public final class Style {
    public final Cat cat;
    public final Setting.Toggle overrideLogic, overrideVisuals, showGrounded;
    public final Setting.Choice showCondition;
    public final Setting.Choice lineMode;
    public final Setting.Slider dashFactor;
    public final Setting.Toggle drawOver, hoverColor, iframeColor;
    public final Setting.Toggle side, outline, eye, viewRay;
    public final Setting.Color sideColor, sideHover, sideIframe, outlineColor, outlineHover, outlineIframe;
    public final Setting.Color eyeColor, eyeHover, eyeIframe, rayColor, rayHover, rayIframe;
    public final Setting.Slider outlineThickness, eyeThickness, rayThickness;

    private final String heading;
    private final String prefix;

    Style(Cat cat) {
      this.cat = cat;
      boolean isGeneral = cat == Cat.DEFAULT;
      heading = cat.label;
      prefix = isGeneral ? "" : cat.label + ": ";

      overrideLogic = isGeneral ? null
          : basicFlag("Override General Logic", false, "Override the General show condition for " + cat.label + ".");
      showCondition = grouped(heading, add(Setting.tip(new Setting.Choice(prefix + "Show Condition", "Always", CONDITIONS),
          "When to draw this hitbox. \"Toggled\" is switched on and off with F3+B or the luka key.")));
      showGrounded = cat == Cat.ARROW ? basicFlag("Show Grounded Arrows", true, "Draw hitboxes for arrows stuck in blocks.") : null;
      overrideVisuals = isGeneral ? null
          : basicFlag("Override General Visuals", false, "Override the General styling for " + cat.label + ".");

      lineMode = grouped(heading, adv(Setting.tip(new Setting.Choice(prefix + "Line Style", "Normal", "Normal", "Dashed"), "Solid or dashed lines.")));
      dashFactor = fineSlider("Dash Factor", 10f, 1f, 20f, 1f, "Length of each dash and gap (dashed lines only).");
      drawOver = fineFlag("Draw Over Entity", false,
          "Draw the hitbox on top of the entity it belongs to instead of letting the entity cover it. Blocks in the way still cover it.");
      hoverColor = fineFlag("Different Color on Hover", false, "Use the hovered colours while this entity is under the crosshair.");
      iframeColor = fineFlag("Different Color in I-Frames", false,
          "Use separate colours while the entity is in its invulnerability frames after a hit. Takes priority over the hover colours.");

      side = fineFlag("Sides", false, "Fill the faces of the box.");
      sideColor = fineColor("Side Color", 0x3FFFFFFF, "Colour of the filled faces.");
      sideHover = fineColor("Hovered Side Color", 0x3FFFFFFF, "Face colour while hovered.");
      sideIframe = fineColor("I-Frame Side Color", 0x3FFF5555, "Face colour in i-frames.");

      outline = fineFlag("Outline", true, "Draw the edges of the box.");
      outlineColor = fineColor("Outline Color", 0xFFFFFFFF, "Colour of the box edges.");
      outlineHover = fineColor("Hovered Outline Color", 0xFFFFFFFF, "Edge colour while hovered.");
      outlineIframe = fineColor("I-Frame Outline Color", 0xFFFF5555, "Edge colour in i-frames.");
      outlineThickness = lineWidth("Outline Thickness");

      eye = fineFlag("Eye Height", true, "Draw a line at the entity's eye height.");
      eyeColor = fineColor("Eye Height Color", 0xFFFF0000, "Colour of the eye height line.");
      eyeHover = fineColor("Hovered Eye Height Color", 0xFFFF0000, "Eye line colour while hovered.");
      eyeIframe = fineColor("I-Frame Eye Height Color", 0xFFFF5555, "Eye line colour in i-frames.");
      eyeThickness = lineWidth("Eye Height Thickness");

      viewRay = fineFlag("View Ray", true, "Draw a line showing where the entity is looking.");
      rayColor = fineColor("View Ray Color", 0xFF0000FF, "Colour of the view ray.");
      rayHover = fineColor("Hovered View Ray Color", 0xFF0000FF, "View ray colour while hovered.");
      rayIframe = fineColor("I-Frame View Ray Color", 0xFFFF5555, "View ray colour in i-frames.");
      rayThickness = lineWidth("View Ray Thickness");
    }

    private Setting.Toggle basicFlag(String label, boolean initial, String tip) {
      return grouped(heading, add(new Setting.Toggle(prefix + label, initial, tip)));
    }

    private Setting.Toggle fineFlag(String label, boolean initial, String tip) {
      return grouped(heading, adv(new Setting.Toggle(prefix + label, initial, tip)));
    }

    private Setting.Color fineColor(String label, int argb, String tip) {
      return grouped(heading, adv(new Setting.Color(prefix + label, argb, tip)));
    }

    private Setting.Slider fineSlider(String label, float initial, float min, float max, float step, String tip) {
      return grouped(heading, adv(new Setting.Slider(prefix + label, initial, min, max, step, tip)));
    }

    private Setting.Slider lineWidth(String label) {
      return fineSlider(label, 1f, .5f, 3f, .25f, "Line width, as a multiple of vanilla's 2.5 pixel lines.");
    }

    /** Index into {@link #CONDITIONS} for the chosen show condition; unknown text counts as "Always". */
    public int condition() {
      int index = Arrays.asList(CONDITIONS).indexOf(showCondition.get());
      return index < 0 ? ALWAYS : index;
    }

    public boolean dashed() { return "Dashed".equals(lineMode.get()); }
  }
}
