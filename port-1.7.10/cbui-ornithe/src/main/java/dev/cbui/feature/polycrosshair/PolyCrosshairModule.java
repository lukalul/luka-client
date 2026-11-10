package dev.cbui.feature.polycrosshair;

import dev.cbui.Cbui;
import dev.cbui.feature.FeatureModule;
import dev.cbui.hud.Setting;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

/**
 * PolyCrosshair port: replaces the vanilla crosshair with your own (Normal / Friendly / Enemy profiles chosen by what you aim
 * at), or restyles the vanilla one. The original's pixel-grid editor does not exist here: a profile is either a built-in design
 * (plus, X, dot, ring, square with size, thickness, gap and colour) or the art imported from a PolyCrosshair config
 * ({@code <game dir>/luka/polycrosshair/<profile>.png} and {@code .shapes}).
 */
public final class PolyCrosshairModule extends FeatureModule {
  public static final String[] DESIGNS = {"Plus", "X", "Dot", "Ring", "Square", "Imported"};
  public static final String[] GRIDS = {"0.5x", "1x", "2x", "3x"};
  public static final int[] GRID_CELLS = {7, 15, 31, 63};

  public final Profile normal, friendly, enemy;

  // ---- "Customize Vanilla Crosshair" ----
  public final Setting.Toggle vanillaOn = grouped("Vanilla crosshair", add(new Setting.Toggle("Customize Vanilla Crosshair", false,
      "Applies these settings to the vanilla crosshair whenever it is showing instead of your custom one.")));
  public final Setting.Toggle vanillaCentered = grouped("Vanilla crosshair", add(new Setting.Toggle("Vanilla: Fix Centering", true,
      "Centers the crosshair on the screen's exact middle pixel. Vanilla can sit half a pixel off depending on window size and GUI scale.")));
  public final Setting.Toggle vanillaBlend = grouped("Vanilla crosshair", add(new Setting.Toggle("Vanilla: Vanilla Blending", true,
      "Inverts the colors behind the crosshair so it shows up on any background.")));
  public final Setting.Color vanillaBlendColor = grouped("Vanilla crosshair", add(new Setting.Color("Vanilla: Blending Color", 0xFFFFFFFF, "Tint of the inverted crosshair.")));
  public final Setting.Color vanillaColor = grouped("Vanilla crosshair", add(new Setting.Color("Vanilla: Color", 0xFFFFFFFF, "The crosshair's color while Vanilla Blending is off.")));
  public final Setting.Toggle vanillaScaleOn = grouped("Vanilla crosshair", add(new Setting.Toggle("Vanilla: Use Custom Scale", false,
      "Draws the crosshair at the scale you pick here, whatever the game's GUI scale is.")));
  public final Setting.Slider vanillaScale = grouped("Vanilla crosshair", add(new Setting.Slider("Vanilla: Scale", 2f, .5f, 6f, .5f,
      "The GUI scale to draw the crosshair at. 2 matches the Normal GUI scale.")));
  public final Setting.Toggle vanillaThird = grouped("Vanilla crosshair", add(new Setting.Toggle("Vanilla: Show in Third Person", true, "Shows the crosshair in third person too.")));
  public final Setting.Toggle vanillaSpectator = grouped("Vanilla crosshair", add(new Setting.Toggle("Vanilla: Always Visible in Spectator", false,
      "Shows the crosshair the whole time you are spectating. Vanilla only shows it when you look at something you can interact with.")));

  public final Setting.Toggle importNow = grouped("Import", add(new Setting.Toggle("Import from PolyCrosshair config", false,
      "Switch on to copy your drawn crosshairs (Normal/Friendly/Enemy art) from the PolyCrosshair config file into luka. It switches itself off again; "
          + "profiles with imported art then use the Imported design.")));

  private final File dir;
  private int ticks;
  private boolean autoImportTried;

  public PolyCrosshairModule() {
    super("PolyCrosshair", false);
    dir = new File(Minecraft.getMinecraft().mcDataDir, "luka/polycrosshair");
    normal = new Profile("normal", "Normal", true);
    friendly = new Profile("friendly", "Friendly", false);
    enemy = new Profile("enemy", "Enemy", false);
  }

  @Override public String category() { return "HUD"; }

  public File dir() { return dir; }

  @Override
  public void tick() {
    try {
      if (!autoImportTried) {
        autoImportTried = true;
        if (!dir.exists()) {
          int n = CrosshairImport.run(dir);
          if (n > 0) for (Profile p : new Profile[] {normal, friendly, enemy}) if (p.hasImported()) p.design.set("Imported");
        }
      }
      if (importNow.get()) {
        importNow.set(false);
        int n = CrosshairImport.run(dir);
        Minecraft mc = Minecraft.getMinecraft();
        if (n > 0) for (Profile p : new Profile[] {normal, friendly, enemy}) if (p.hasImported()) p.design.set("Imported");
        if (mc.thePlayer != null)
          mc.thePlayer.addChatMessage(new ChatComponentText(n > 0
              ? "§a[luka] Imported " + n + " crosshair(s) from PolyCrosshair into " + dir.getPath()
              : "§c[luka] No PolyCrosshair config with drawn crosshairs was found."));
      }
      if ((++ticks & 31) == 0) {
        normal.refreshStamp();
        friendly.refreshStamp();
        enemy.refreshStamp();
      }
    } catch (Throwable error) {
      Cbui.reportOnce("crosshair tick", error);
    }
  }

  /** The profile that applies to a target, or null when neither it nor Normal is enabled (then vanilla/customised vanilla shows). */
  public Profile profile(int kind) {
    Profile requested;
    switch (kind) {
      case 1: requested = friendly; break;
      case 2: requested = enemy; break;
      default: requested = normal;
    }
    return requested.use.get() ? requested : normal.use.get() ? normal : null;
  }

  /** One crosshair profile. */
  public final class Profile {
    public final String key, label;
    public final Setting.Toggle use;
    public final Setting.Choice design, grid;
    public final Setting.Slider arm, thickness, gap, rotation;
    public final Setting.Toggle centerDot;
    public final Setting.Color color;
    public final Setting.Toggle outlineOn, blendOn, healthOn, bowOn, scaleOn, smooth, third, spectator;
    public final Setting.Slider outlineThickness, healthThreshold, bowScale, scale;
    public final Setting.Color outlineColor, blendColor, healthColor;
    private long stamp;

    Profile(String key, String label, boolean defaultOn) {
      this.key = key;
      this.label = label;
      String g = label + " crosshair", p = label + ": ";
      use = grouped(g, add(new Setting.Toggle(p + "Use " + label + " Crosshair", defaultOn,
          defaultOn ? "Shows this crosshair when you are not looking at a friendly or enemy target. Turn this off to use the vanilla crosshair instead."
              : "Shows this crosshair when you look at " + label.toLowerCase() + " targets. Turn this off to use the Normal crosshair instead.")));
      design = grouped(g, add(Setting.tip(new Setting.Choice(p + "Design", "Plus", DESIGNS),
          "Built-in shapes use the settings below; Imported uses the art copied from PolyCrosshair (luka/polycrosshair/" + key + ".png).")));
      color = grouped(g, add(new Setting.Color(p + "Shape Color", 0xFFFFFFFF, "Colour of the built-in designs (imported art keeps its own colours).")));
      arm = grouped(g, adv(new Setting.Slider(p + "Arm Length / Size", 4f, 1f, 31f, 1f, "How far the shape reaches from the centre, in crosshair pixels.")));
      thickness = grouped(g, adv(new Setting.Slider(p + "Line Thickness", 1f, 1f, 7f, 1f, "Thickness of the lines, in crosshair pixels.")));
      gap = grouped(g, adv(new Setting.Slider(p + "Center Gap", 0f, 0f, 15f, 1f, "Empty space around the centre (plus and X designs).")));
      centerDot = grouped(g, adv(new Setting.Toggle(p + "Center Dot", false, "Keeps a dot in the middle even when there is a gap (plus and X designs).")));
      grid = grouped(g, adv(Setting.tip(new Setting.Choice(p + "Grid Size", "1x", GRIDS),
          "The size of the drawing canvas, and how large the crosshair is on screen (built-in designs).")));
      rotation = grouped(g, adv(new Setting.Slider(p + "Rotation", 0f, 0f, 360f, 1f, "Rotates the crosshair clockwise around its centre.")));
      outlineOn = grouped(g, adv(new Setting.Toggle(p + "Outline", false, "Draws a border around the crosshair.")));
      outlineThickness = grouped(g, adv(new Setting.Slider(p + "Outline Thickness", .5f, 0f, 1f, .01f, "How far the outline extends, in crosshair pixels.")));
      outlineColor = grouped(g, adv(new Setting.Color(p + "Outline Color", 0xFF000000, "Colour of the outline.")));
      blendOn = grouped(g, adv(new Setting.Toggle(p + "Vanilla Blending", true, "Inverts the colours behind the crosshair, like the vanilla one, so it shows up on any background.")));
      blendColor = grouped(g, adv(new Setting.Color(p + "Blending Color", 0xFFFFFFFF, "Tint of the inverted crosshair.")));
      healthOn = grouped(g, adv(new Setting.Toggle(p + "Health Color Shift", false, "Recolours the crosshair when your health is low.")));
      healthThreshold = grouped(g, adv(new Setting.Slider(p + "Health Threshold", 6f, 1f, 20f, 1f, "The health at or below which the crosshair changes colour. 20 is full health.")));
      healthColor = grouped(g, adv(new Setting.Color(p + "Health Color", 0xFFFF3C3C, "Colour used at low health.")));
      bowOn = grouped(g, adv(new Setting.Toggle(p + "Dynamic Bow", false, "Spreads the crosshair apart when you start drawing a bow and closes it as the shot charges.")));
      bowScale = grouped(g, adv(new Setting.Slider(p + "Bow Animation Scale", 3f, 1f, 10f, .01f, "How far the crosshair spreads apart.")));
      scaleOn = grouped(g, adv(new Setting.Toggle(p + "Use Custom Scale", false, "Draws the crosshair at the scale you pick here, whatever the game's GUI scale is.")));
      scale = grouped(g, adv(new Setting.Slider(p + "Scale", 2f, .5f, 6f, .5f, "The GUI scale to draw the crosshair at. 2 matches the Normal GUI scale.")));
      smooth = grouped(g, adv(new Setting.Toggle(p + "Smooth Scaling", false, "Draws the crosshair at exactly the scale you pick. Turn this off to round its size to whole GUI pixels.")));
      third = grouped(g, adv(new Setting.Toggle(p + "Show in Third Person", true, "Shows the crosshair in third person too.")));
      spectator = grouped(g, adv(new Setting.Toggle(p + "Always Visible in Spectator", false,
          "Shows the crosshair the whole time you are spectating. Vanilla only shows it when you look at something you can interact with.")));
    }

    public int cells() {
      String v = grid.get();
      for (int i = 0; i < GRIDS.length; i++) if (GRIDS[i].equals(v)) return GRID_CELLS[i];
      return 15;
    }

    public File png() { return new File(dir, key + ".png"); }

    public File shapesFile() { return new File(dir, key + ".shapes"); }

    public boolean hasImported() { return png().isFile(); }

    /** Changes when the imported files change on disk, so the texture is rebuilt. */
    public long stamp() { return stamp; }

    void refreshStamp() {
      File a = png(), b = shapesFile();
      stamp = (a.isFile() ? a.lastModified() ^ a.length() : 0) * 31 + (b.isFile() ? b.lastModified() ^ b.length() : 0);
    }

    /** The art for this profile: imported files when chosen and present, else a built-in design. */
    public Pixels pixels() {
      if ("Imported".equals(design.get()) && hasImported()) {
        try {
          Pixels px = Pixels.fromImage(ImageIO.read(png()));
          if (px != null) return px;
        } catch (Exception ignored) {
          // fall through to the built-in plus
        }
      }
      return builtIn();
    }

    public Shapes shapes(int cells) {
      if ("Imported".equals(design.get()) && hasImported() && shapesFile().isFile()) {
        try {
          return Shapes.decode(new String(Files.readAllBytes(shapesFile().toPath()), StandardCharsets.UTF_8).trim(), cells, false);
        } catch (Exception ignored) {
          // no shapes
        }
      }
      return new Shapes(cells);
    }

    private Pixels builtIn() {
      int width = cells(), middle = width / 2, reach = Math.min(Math.round(arm.get()), middle);
      int line = Math.max(1, Math.round(thickness.get())), empty = Math.round(gap.get()), ink = color.get();
      Pixels canvas = new Pixels(width);
      String selected = design.get();
      for (int y = -middle; y <= middle; y++) for (int x = -middle; x <= middle; x++) {
        int ax = Math.abs(x), ay = Math.abs(y);
        boolean fill;
        if ("Dot".equals(selected)) fill = x * x + y * y <= (line - .5f) * (line - .5f) + .25f;
        else if ("Ring".equals(selected)) { double radius = Math.hypot(x, y); fill = radius <= reach + .5f && radius > reach + .5f - line; }
        else if ("Square".equals(selected)) fill = Math.max(ax, ay) <= reach && Math.max(ax, ay) > reach - line;
        else if ("X".equals(selected)) fill = Math.min(ax, ay) >= empty && Math.max(ax, ay) <= reach && Math.abs(ax - ay) <= line / 2;
        else fill = ax >= empty && ax <= reach && ay <= line / 2 || ay >= empty && ay <= reach && ax <= line / 2;
        if (fill || centerDot.get() && ax <= line / 2 && ay <= line / 2) canvas.set(middle + x, middle + y, ink);
      }
      return canvas;
    }
  }
}
