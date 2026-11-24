package dev.cbui.feature.hitboxes;

import dev.cbui.Cbui;
import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.entity.projectile.EntityWitherSkull;
import org.lwjgl.input.Keyboard;

/**
 * Configurable hitboxes that take the place of the game's F3+B boxes while the module is on. F3+B and a luka key
 * flip a "toggle state" that categories can follow.
 */
public final class HitboxesModule extends FeatureModule implements Features.Synced {
  // Category indices, in menu order.
  static final int DEFAULT = 0;
  static final int OTHER_PLAYERS = 1;
  static final int YOU = 2;
  static final int CREATURES = 3;
  static final int HOSTILE = 4;
  static final int THROWN = 5;
  static final int ARROWS = 6;
  static final int FIREBALLS = 7;
  static final int WITHER_SKULLS = 8;
  static final int HANGING = 9;
  static final int ARMOUR_STANDS = 10;
  static final int DROPPED_ITEMS = 11;
  static final int XP_ORBS = 12;

  private static final String ALL = "All entities";
  private static final String KEY_NAME = "Flip hitboxes";

  public final Setting.Toggle hideWithHud = grouped(ALL, add(new Setting.Toggle("Hide when HUD is hidden", true,
      "No hitboxes while F1 hides the HUD")));
  public final Setting.Toggle shrinkFar = grouped(ALL, add(new Setting.Toggle("Shrink lines far away", true,
      "Lines further than about 4 blocks get thinner with distance, down to 1 pixel; off keeps one width everywhere")));
  public final Setting.Toggle reachMargin = grouped(ALL, add(new Setting.Toggle("Include reach margin", true,
      "Grow boxes to the area attacks really hit")));
  public final Setting.Toggle rememberToggle = grouped(ALL, add(new Setting.Toggle("Remember toggle after restart", true,
      "Keep the toggle state between sessions")));
  public final Setting.Toggle toggled = grouped(ALL, add(new Setting.Toggle("Toggle state", false,
      "Flipped by F3+B or the hitbox key; drives \"When toggled\"")));

  final HitboxGroup[] groups = {
      group("Default", true, false),
      group("Other players", false, false),
      group("You", false, false),
      group("Creatures", false, false),
      group("Hostile mobs", false, false),
      group("Thrown items", false, false),
      group("Arrows", false, true),
      group("Fireballs", false, false),
      group("Wither skulls", false, false),
      group("Hanging entities", false, false),
      group("Armour stands", false, false),
      group("Dropped items", false, false),
      group("Experience orbs", false, false),
  };

  private KeyBinding flipKey;
  private boolean keyFailed;
  private boolean sessionStarted;

  public HitboxesModule() {
    super("Hitboxes", false);
  }

  @Override
  public String category() {
    return "VISUALS";
  }

  public static HitboxesModule active() {
    HitboxesModule module = Features.get(HitboxesModule.class);
    return module != null && module.enabled() ? module : null;
  }

  // ---------------------------------------------------------------- settings

  private HitboxGroup group(String title, boolean base, boolean arrows) {
    HitboxGroup g = new HitboxGroup(title);
    String x = base ? "" : title + " - ";
    if (!base) {
      g.ownVisibility = main(title, new Setting.Toggle(x + "Use own visibility", false,
          "Ignore Default's visibility for this category"));
    }
    g.visibility = main(title, Setting.tip(new Setting.Choice(x + "Visibility", HitboxGroup.ALWAYS, HitboxGroup.VISIBILITY),
        "When boxes of this kind are drawn"));
    if (arrows) {
      g.stuckArrows = main(title, new Setting.Toggle(x + "Arrows stuck in blocks", true, "Also draw arrows that have landed"));
    }
    if (!base) {
      g.ownLook = main(title, new Setting.Toggle(x + "Use own look", false,
          "Ignore Default's colours and lines for this category"));
    }
    g.pattern = more(title, Setting.tip(new Setting.Choice(x + "Line pattern", HitboxGroup.SOLID, HitboxGroup.PATTERNS),
        "Solid or dashed lines"));
    g.dash = more(title, new Setting.Slider(x + "Dash length", 10F, 1F, 20F, 1F, "Length of dashes and gaps"));
    g.throughModel = more(title, new Setting.Toggle(x + "Show through own model", false,
        "Draw in front of the entity's model; blocks still cover it"));
    g.aimedColours = more(title, new Setting.Toggle(x + "Aimed-at colours", false,
        "Use the aimed-at colours for the entity under the crosshair"));
    g.hitColours = more(title, new Setting.Toggle(x + "Hit-cooldown colours", false,
        "Use the hit-cooldown colours while it flashes after a hit"));
    g.fill = more(title, new Setting.Toggle(x + "Fill faces", false, "Fill the box sides"));
    g.fillColour = more(title, new Setting.Color(x + "Fill colour", 0x3FFFFFFF, "Colour of the box sides"));
    g.fillAimed = more(title, new Setting.Color(x + "Fill colour, aimed at", 0x3FFFFFFF,
        "Side colour while it is under the crosshair"));
    g.fillHit = more(title, new Setting.Color(x + "Fill colour, hit cooldown", 0x3FFF5555,
        "Side colour while it recovers from a hit"));
    g.edges = more(title, new Setting.Toggle(x + "Box edges", true, "Draw the box edges"));
    g.edgeColour = more(title, new Setting.Color(x + "Edge colour", 0xFFFFFFFF, "Colour of the box edges"));
    g.edgeAimed = more(title, new Setting.Color(x + "Edge colour, aimed at", 0xFFFFFFFF,
        "Edge colour while it is under the crosshair"));
    g.edgeHit = more(title, new Setting.Color(x + "Edge colour, hit cooldown", 0xFFFF5555,
        "Edge colour while it recovers from a hit"));
    g.edgeWidth = more(title, new Setting.Slider(x + "Edge width", 1F, .5F, 3F, .25F, "1 is about vanilla's line width"));
    g.eye = more(title, new Setting.Toggle(x + "Eye line", true, "Mark the eye height"));
    g.eyeColour = more(title, new Setting.Color(x + "Eye line colour", 0xFFFF0000, "Colour of the eye-height mark"));
    g.eyeAimed = more(title, new Setting.Color(x + "Eye line colour, aimed at", 0xFFFF0000,
        "Eye line colour while it is under the crosshair"));
    g.eyeHit = more(title, new Setting.Color(x + "Eye line colour, hit cooldown", 0xFFFF5555,
        "Eye line colour while it recovers from a hit"));
    g.eyeWidth = more(title, new Setting.Slider(x + "Eye line width", 1F, .5F, 3F, .25F, "Thickness of the eye-height mark"));
    g.look = more(title, new Setting.Toggle(x + "Look direction", true, "Line showing where it faces"));
    g.lookColour = more(title, new Setting.Color(x + "Look line colour", 0xFF0000FF, "Colour of the facing line"));
    g.lookAimed = more(title, new Setting.Color(x + "Look line colour, aimed at", 0xFF0000FF,
        "Facing line colour while it is under the crosshair"));
    g.lookHit = more(title, new Setting.Color(x + "Look line colour, hit cooldown", 0xFFFF5555,
        "Facing line colour while it recovers from a hit"));
    g.lookWidth = more(title, new Setting.Slider(x + "Look line width", 1F, .5F, 3F, .25F, "Thickness of the facing line"));
    return g;
  }

  private <S extends Setting<?>> S main(String group, S setting) {
    return grouped(group, add(setting));
  }

  private <S extends Setting<?>> S more(String group, S setting) {
    return grouped(group, adv(setting));
  }

  /** The group whose visibility a category follows. */
  HitboxGroup visibilityOf(int category) {
    HitboxGroup g = groups[category];
    return g.ownVisibility != null && g.ownVisibility.get() ? g : groups[DEFAULT];
  }

  /** The group whose colours and lines a category follows. */
  HitboxGroup lookOf(int category) {
    HitboxGroup g = groups[category];
    return g.ownLook != null && g.ownLook.get() ? g : groups[DEFAULT];
  }

  /** False when no category could draw anything right now, so the whole pass can be skipped. */
  boolean anythingToDraw() {
    for (int i = 0; i < groups.length; i++) {
      String mode = visibilityOf(i).visibility.get();
      if (HitboxGroup.NEVER.equals(mode)) continue;
      if (HitboxGroup.WHEN_TOGGLED.equals(mode) && !toggled.get()) continue;
      if (lookOf(i).anyPart()) return true;
    }
    return false;
  }

  /** Which category an entity belongs to; the first matching rule wins. */
  static int categoryOf(Entity entity, EntityPlayer you) {
    if (entity == you) return YOU;
    if (entity instanceof IMob) return HOSTILE;
    if (entity instanceof EntityArrow) return ARROWS;
    if (entity instanceof EntityLargeFireball || entity instanceof EntitySmallFireball) return FIREBALLS;
    if (entity instanceof EntityWitherSkull) return WITHER_SKULLS;
    if (entity instanceof EntityPlayer) return OTHER_PLAYERS;
    if (entity instanceof EntityLiving) return CREATURES;
    if (entity instanceof IProjectile || entity instanceof EntityThrowable || entity instanceof EntityFishHook) return THROWN;
    if (entity instanceof EntityHanging) return HANGING;
    if (entity instanceof EntityArmorStand) return ARMOUR_STANDS;
    if (entity instanceof EntityItem) return DROPPED_ITEMS;
    if (entity instanceof EntityXPOrb) return XP_ORBS;
    return DEFAULT;
  }

  // ---------------------------------------------------------------- toggle state and key

  void flip() {
    toggled.set(!toggled.get());
  }

  /** F3+B: true when the module took the key press (and flipped its toggle), false to let the game have it. */
  public static boolean takeDebugKey() {
    try {
      HitboxesModule module = active();
      if (module == null) return false;
      module.flip();
      return true;
    } catch (Throwable error) {
      Cbui.reportOnce("hitbox F3+B", error);
      return false;
    }
  }

  /** True while the game's own debug boxes must stay hidden. */
  public static boolean hidesGameBoxes() {
    try {
      return active() != null;
    } catch (Throwable error) {
      return false;
    }
  }

  @Override
  public void sync() {
    ensureKey();
    if (flipKey != null && !enabled()) {
      while (flipKey.isPressed()) {
        // presses made while the module is off are dropped, not saved up
      }
    }
  }

  @Override
  public void tick() {
    if (!sessionStarted) {
      sessionStarted = true;
      if (!rememberToggle.get()) toggled.set(false);
    }
    ensureKey();
    if (flipKey != null) {
      while (flipKey.isPressed()) flip();
    }
  }

  private void ensureKey() {
    if (flipKey != null || keyFailed || mc.gameSettings == null) return;
    try {
      KeyBinding key = new KeyBinding(KEY_NAME, Keyboard.KEY_NONE, "luka");
      File options = new File(mc.mcDataDir, "options.txt");
      if (options.isFile()) {
        // also finds the key saved under its name before the card was renamed
        Integer code = dev.cbui.config.CardRenames.savedKeyCode(
            Files.readAllLines(options.toPath(), StandardCharsets.UTF_8), key.getKeyDescription());
        if (code != null) key.setKeyCode(code); // a missing or damaged line keeps it unbound
      }
      KeyBinding[] old = mc.gameSettings.keyBindings;
      KeyBinding[] all = Arrays.copyOf(old, old.length + 1);
      all[old.length] = key;
      mc.gameSettings.keyBindings = all;
      KeyBinding.resetKeyBindingArrayAndHash();
      flipKey = key;
    } catch (Throwable error) {
      keyFailed = true;
      Cbui.reportOnce("hitbox key", error);
    }
  }
}
