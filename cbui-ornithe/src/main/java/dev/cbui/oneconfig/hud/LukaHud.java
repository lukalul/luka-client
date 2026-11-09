package dev.cbui.oneconfig.hud;

import dev.cbui.Cbui;
import dev.cbui.hud.AbstractHudModule;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;
import org.polyfrost.oneconfig.api.hud.v1.LegacyHud;

/**
 * A CBUI module as a real OneConfig HUD. It is a {@link LegacyHud}: OneConfig's HUD manager owns its
 * instance, saved position, scale, visibility and profile, and its legacy renderer translates and
 * scales to the HUD's frame before calling {@link #render()} every frame; this class then draws the
 * module's box at the origin. OneConfig re-creates instances from saved trees
 * with the public no-argument constructor of each subclass in {@link LukaHuds}, so those class names
 * are part of the saved data and must not be renamed.
 */
public abstract class LukaHud extends LegacyHud {
  /** Set while CBUI's own HUD editor is open: it draws CBUI's boxes itself. */
  static volatile boolean cbuiEditorOpen;

  private final String moduleName;
  private volatile boolean active, failed;
  private volatile AbstractHudModule module;

  protected LukaHud(String moduleName, String id, Hud.Category category) {
    super(id, moduleName, category);
    this.moduleName = moduleName;
  }

  public final AbstractHudModule module() {
    AbstractHudModule cached = module;
    if (cached != null) return cached;
    dev.cbui.hud.HudManager manager = Cbui.hud();
    return module = manager == null ? null : manager.find(moduleName);
  }

  /** True once CBUI paired this instance with its module (position migrated, state synced). */
  final boolean active() { return active && !failed; }

  final void activate() { active = true; }

  final boolean failed() { return failed; }

  @Override
  public float getWidth() {
    AbstractHudModule m = module();
    return m == null ? 1 : Math.max(1, m.width());
  }

  @Override
  public float getHeight() {
    AbstractHudModule m = module();
    return m == null ? 1 : Math.max(1, m.height());
  }

  /*
   * OneConfig measures a LegacyHud only while drawing it, so a hidden HUD has renderedW/H = 0 and
   * getScaledWidth/Height() fall back to the static size. Reporting the module's box as the static
   * size keeps OneConfig's own geometry (keep-visible clamping, section/anchor maths, its editor)
   * correct for hidden CBUI HUDs too.
   */
  @Override
  public float getStaticW() { return getWidth(); }

  @Override
  public float getStaticH() { return getHeight(); }

  @Override
  public void render() {
    if (cbuiEditorOpen || !active || failed) return;
    AbstractHudModule m = module();
    if (m == null) return;
    try {
      boolean editing = HudManager.INSTANCE.isEditing();
      if (!m.enabled() && !editing) return;
      // boxes whose text grows (memory, scoreboard, pack list) are slid back inside the screen instead of running off its edge
      float scale = getEffectiveScale() > 0 ? getEffectiveScale() : 1f, sw = HudManager.guiScreenWidth, sh = HudManager.guiScreenHeight;
      float dx = 0, dy = 0;
      if (sw > 0 && sh > 0 && !editing) {
        float over = getX() + m.width() * scale - (sw - 2), underY = getY() + m.height() * scale - (sh - 2);
        if (over > 0) dx = -Math.min(over, Math.max(0, getX() - 2)) / scale;
        if (underY > 0) dy = -Math.min(underY, Math.max(0, getY() - 2)) / scale;
      }
      net.minecraft.client.renderer.GlStateManager.pushMatrix();
      try {
        if (dx != 0 || dy != 0) net.minecraft.client.renderer.GlStateManager.translate(dx, dy, 0);
        m.renderBox(dev.cbui.hud.HudManager.partialTicks(), editing);
      } finally {
        net.minecraft.client.renderer.GlStateManager.popMatrix();
      }
    } catch (Throwable error) {
      failed = true; // CBUI's native renderer takes this module over from the next frame
      Cbui.reportOnce("OneConfig HUD render: " + moduleName, error);
    }
  }

  /** Values are computed on CBUI's client tick; nothing for OneConfig's update pass to do. */
  @Override
  public boolean update() { return false; }

  @Override
  public boolean showByDefault() { return true; }

  /** One instance per module; hiding (not deleting) is how it is switched off. */
  @Override
  public boolean deletable() { return false; }

  @Override
  public boolean multipleInstancesAllowed() { return false; }

  /** CBUI draws its own background (with per-module colour, rounding and border). */
  @Override
  public boolean hasBackground() { return false; }

  @Override
  public boolean canMergeBackground() { return false; }
}
