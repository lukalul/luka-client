package dev.cbui.feature.crosshair;

import dev.cbui.Cbui;
import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.ChatComponentText;

/**
 * Replaces the crosshair with one of three profiles picked by what the player aims at, or restyles the game's own
 * crosshair. Profiles draw a built-in shape or a picture from luka/crosshair (drawn by the player or imported from another crosshair mod's config).
 */
public final class CrosshairModule extends FeatureModule {
  private static final String PLAIN = "Plain crosshair";
  private static final String DRAWINGS = "Imported drawings";
  private static final int NEUTRAL = 0;
  private static final int ALLY = 1;
  private static final int FOE = 2;
  /** Spectator mode exists on this version. */
  private static final boolean HAS_SPECTATOR = true;
  private static final int RESCAN_TICKS = 40;

  public final Setting.Toggle restyle = plain(new Setting.Toggle("Restyle the plain crosshair", false,
      "When no profile applies, draw the game's own crosshair with the options below"));
  public final Setting.Toggle plainSnap = plain(new Setting.Toggle("Plain - snap to centre", true,
      "Line the crosshair up with the window's middle pixel"));
  public final Setting.Toggle plainInvert = plain(new Setting.Toggle("Plain - invert background", true,
      "Show the crosshair as the inverse of what is behind it"));
  public final Setting.Color plainTint = plain(new Setting.Color("Plain - invert tint", 0xFFFFFFFF,
      "Tint applied to the inverted crosshair"));
  public final Setting.Color plainColour = plain(new Setting.Color("Plain - colour", 0xFFFFFFFF,
      "Colour used when inversion is off"));
  public final Setting.Toggle plainOwnSize = plain(new Setting.Toggle("Plain - own size", false,
      "Ignore the GUI scale and use the size below"));
  public final Setting.Slider plainSize = plain(new Setting.Slider("Plain - size", 2F, .5F, 6F, .5F,
      "GUI scale to draw at; 2 is the Normal GUI scale"));
  public final Setting.Toggle plainThird = plain(new Setting.Toggle("Plain - third person", true,
      "Also show it in third person"));
  public final Setting.Toggle plainSpectator = HAS_SPECTATOR ? plain(new Setting.Toggle("Plain - always in spectator",
      false, "Show it the whole time while spectating, not only over entities and containers")) : null;
  public final Setting.Toggle importNow = grouped(DRAWINGS, add(new Setting.Toggle("Import drawn crosshairs", false,
      "Copy drawn crosshairs from another crosshair mod's config into luka/crosshair, then switch back off")));

  final CrosshairSet normal = profile(0, "Default", true);
  final CrosshairSet ally = profile(1, "Ally", false);
  final CrosshairSet foe = profile(2, "Foe", false);

  private final CrosshairArt art = new CrosshairArt(mc.mcDataDir);
  private final CrosshairPainter painter = new CrosshairPainter(art);
  private boolean firstRunDone;
  private int rescanIn;
  /** Set when drawing failed once; luka leaves the crosshair to the game until the next launch. */
  private static volatile boolean broken;

  public CrosshairModule() {
    super("Crosshair", false);
  }

  @Override
  public String category() {
    return "HUD";
  }

  public static CrosshairModule active() {
    CrosshairModule module = Features.get(CrosshairModule.class);
    return module != null && module.enabled() ? module : null;
  }

  // ---------------------------------------------------------------- settings

  private <S extends dev.cbui.hud.Setting<?>> S plain(S setting) {
    return grouped(PLAIN, add(setting));
  }

  private CrosshairSet profile(int slot, String title, boolean on) {
    CrosshairSet p = new CrosshairSet(slot, title);
    String x = title + " - ";
    String file = CrosshairArt.FILES[slot];
    p.on = main(title, new Setting.Toggle(x + "on", on, "Use this profile for its targets"));
    p.shape = main(title, Setting.tip(new Setting.Choice(x + "shape", "Cross", CrosshairSet.SHAPES),
        "Built-in shape, or the art in luka/crosshair/" + file + ".png"));
    p.colour = main(title, new Setting.Color(x + "colour", 0xFFFFFFFF, "Colour of the built-in shape"));
    p.reach = more(title, new Setting.Slider(x + "reach", 4F, 1F, 31F, 1F, "Distance from the centre, in cells"));
    p.lineWidth = more(title, new Setting.Slider(x + "line width", 1F, 1F, 7F, 1F,
        "Thickness of the lines, in cells; even values draw like the next odd one"));
    p.gap = more(title, new Setting.Slider(x + "middle gap", 0F, 0F, 15F, 1F,
        "Empty cells around the centre (crosses only)"));
    p.keepMiddle = more(title, new Setting.Toggle(x + "keep middle pixel", false,
        "Fill the centre even with a gap (crosses only)"));
    p.canvas = more(title, Setting.tip(new Setting.Choice(x + "canvas", "Medium", CrosshairSet.CANVASES),
        "Size of the drawing area for built-in shapes: 7, 15, 31 or 63 cells"));
    p.turn = more(title, new Setting.Slider(x + "turn", 0F, 0F, 360F, 1F, "Clockwise rotation in degrees"));
    p.border = more(title, new Setting.Toggle(x + "border", false, "Outline around the shape"));
    p.borderWidth = more(title, new Setting.Slider(x + "border width", .5F, 0F, 1F, .01F, "Outline width in cells"));
    p.borderColour = more(title, new Setting.Color(x + "border colour", 0xFF000000, "Outline colour"));
    p.invert = more(title, new Setting.Toggle(x + "invert background", true,
        "Show the shape as the inverse of what is behind it"));
    p.invertTint = more(title, new Setting.Color(x + "invert tint", 0xFFFFFFFF, "Tint applied to the inverted shape"));
    p.lowHealth = more(title, new Setting.Toggle(x + "low health tint", false, "Recolour the shape when health is low"));
    p.lowHealthAt = more(title, new Setting.Slider(x + "low health at", 6F, 1F, 20F, 1F,
        "Health points at or below which the tint applies"));
    p.lowHealthColour = more(title, new Setting.Color(x + "low health colour", 0xFFFF3C3C, "Colour used at low health"));
    p.bowSpread = more(title, new Setting.Toggle(x + "bow spread", false, "Split the crosshair apart while drawing a bow"));
    p.spread = more(title, new Setting.Slider(x + "spread distance", 3F, 1F, 10F, .01F,
        "How far the pieces start apart, in cells"));
    p.ownSize = more(title, new Setting.Toggle(x + "own size", false, "Ignore the GUI scale and use the size below"));
    p.size = more(title, new Setting.Slider(x + "size", 2F, .5F, 6F, .5F, "GUI scale to draw at; 2 is the Normal GUI scale"));
    p.exact = more(title, new Setting.Toggle(x + "exact sizing", false,
        "Use the exact size instead of rounding to whole pixels"));
    p.thirdPerson = more(title, new Setting.Toggle(x + "third person", true, "Also show it in third person"));
    if (HAS_SPECTATOR) {
      p.spectator = more(title, new Setting.Toggle(x + "always in spectator", false,
          "Show it the whole time while spectating, not only over entities and containers"));
    }
    return p;
  }

  private <S extends dev.cbui.hud.Setting<?>> S main(String group, S setting) {
    return grouped(group, add(setting));
  }

  private <S extends dev.cbui.hud.Setting<?>> S more(String group, S setting) {
    return grouped(group, adv(setting));
  }

  // ---------------------------------------------------------------- ticking: import switch and file watching

  @Override
  public void tick() {
    try {
      if (!firstRunDone) {
        firstRunDone = true;
        firstImport();
      }
      if (importNow.get()) {
        importNow.set(false);
        importByHand();
      }
      if (--rescanIn <= 0) {
        rescanIn = RESCAN_TICKS;
        art.rescan();
      }
    } catch (Throwable error) {
      Cbui.reportOnce("crosshair files", error);
    }
  }

  /** The very first time, with no art folder yet, quietly pick up drawings another crosshair mod left. */
  private void firstImport() {
    if (art.folder().exists()) return;
    CrosshairArt.Found found = art.importDrawings();
    if (found == null) return;
    CrosshairSet[] profiles = {normal, ally, foe};
    for (int i = 0; i < profiles.length; i++) {
      if (found.copied[i]) profiles[i].shape.set(CrosshairSet.IMPORTED);
    }
  }

  /** The card's switch: copies drawings but never changes a profile's shape; the chat line says what to do. */
  private void importByHand() {
    CrosshairArt.Found found = art.importDrawings();
    String text;
    if (found == null) {
      text = "No drawn crosshairs were found in the config folders or the game folder.";
    } else {
      StringBuilder which = new StringBuilder();
      String[] names = {"Default", "Ally", "Foe"};
      for (int i = 0; i < names.length; i++) {
        if (!found.copied[i]) continue;
        if (which.length() > 0) which.append(", ");
        which.append(names[i]);
      }
      text = "Copied " + found.count() + " crosshair drawing" + (found.count() == 1 ? "" : "s") + " (" + which
          + ") into luka/crosshair. Set a profile's shape to \"Imported art\" to use it.";
    }
    if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText("§5[luka] §f" + text));
  }

  // ---------------------------------------------------------------- drawing

  /**
   * Draws luka's crosshair if one applies. True means luka took charge (drew it, or deliberately hid it) and the
   * caller must not draw its own; false means the caller should draw the usual crosshair. Never throws.
   */
  public static boolean takeOver(boolean vanillaWouldShow, float partialTicks) {
    if (broken) return false;
    CrosshairModule module = active();
    if (module == null) return false;
    try {
      return module.draw(vanillaWouldShow, partialTicks);
    } catch (Throwable error) {
      broken = true;
      Cbui.reportOnce("crosshair", error);
      try {
        module.painter.restore();
      } catch (Throwable ignored) {
        // already reported
      }
      return false;
    }
  }

  private boolean draw(boolean vanillaWouldShow, float partialTicks) {
    EntityPlayerSP player = mc.thePlayer;
    if (player == null || mc.gameSettings == null) return false;
    CrosshairSet profile = pick(player);
    if (profile == null && !restyle.get()) return false;
    if (!vanillaWouldShow && !spectatorWantsIt(player, profile)) return true;
    if (mc.gameSettings.thirdPersonView != 0 && !(profile != null ? profile.thirdPerson.get() : plainThird.get())) {
      return true;
    }
    if (profile != null) {
      boolean low = profile.lowHealth.get() && player.getHealth() <= profile.lowHealthAt.get();
      painter.drawProfile(profile, low, partialTicks);
    } else {
      painter.drawPlain(plainSnap.get(), plainInvert.get(), plainTint.get(), plainColour.get(), plainOwnSize.get(),
          plainSize.get());
    }
    return true;
  }

  /** The game hides the crosshair in spectator mode unless over an entity or container; the switch overrides that. */
  private boolean spectatorWantsIt(EntityPlayerSP player, CrosshairSet profile) {
    Setting.Toggle always = profile != null ? profile.spectator : plainSpectator;
    if (always == null || !always.get() || !player.isSpectator()) return false;
    boolean debugScreen = mc.gameSettings.showDebugInfo && !player.hasReducedDebug() && !mc.gameSettings.reducedDebugInfo;
    return !debugScreen;
  }

  /** The profile for the aimed-at entity, falling back to Default; null when Default is off too. */
  private CrosshairSet pick(EntityPlayerSP player) {
    Entity target = mc.objectMouseOver != null ? mc.objectMouseOver.entityHit : null;
    int side = sideOf(player, target);
    CrosshairSet chosen = side == ALLY ? ally : side == FOE ? foe : normal;
    if (!chosen.on.get()) chosen = normal;
    return chosen.on.get() ? chosen : null;
  }

  private static int sideOf(EntityPlayer player, Entity target) {
    if (target == null || target == player || target.isInvisible() || !(target instanceof EntityLivingBase)) return NEUTRAL;
    Team mine = player.getTeam();
    Team theirs = ((EntityLivingBase) target).getTeam();
    if (mine != null && theirs != null) return mine.isSameTeam(theirs) ? ALLY : FOE;
    if (target instanceof EntityAnimal || target instanceof EntityAmbientCreature || target instanceof EntityWaterMob) {
      return ALLY;
    }
    if (target instanceof IMob || target instanceof EntityPlayer) return FOE;
    return NEUTRAL;
  }
}
