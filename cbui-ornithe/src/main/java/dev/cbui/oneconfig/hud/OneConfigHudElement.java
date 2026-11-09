package dev.cbui.oneconfig.hud;

import dev.cbui.Cbui;
import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.HudElement;
import dev.cbui.hud.LayoutMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import org.polyfrost.oneconfig.api.hud.v1.LegacyHudMarker;

/**
 * Any live OneConfig HUD as a CBUI editor box. Position, scale and visibility are read from and
 * written to the HUD itself (OneConfig converts positions to its screen-section form and stores
 * them in the active OneConfig profile); nothing about another mod's HUD is stored by CBUI.
 */
final class OneConfigHudElement implements HudElement {
  final Hud hud;

  OneConfigHudElement(Hud hud) {
    this.hud = hud;
  }

  public String name() {
    String title = hud.title;
    return title == null || title.isEmpty() ? hud.getClass().getSimpleName() : title;
  }

  public boolean enabled() { return !hud.getHidden(); }

  public void enabled(boolean on) {
    hud.setHidden(!on);
    AbstractHudModule m = module();
    if (m != null) m.enabled(on);
    save();
  }

  public float x() { return hud.getX(); }
  public float y() { return hud.getY(); }
  /*
   * For CBUI's own HUDs the box is computed from the module, never from OneConfig's measured size:
   * OneConfig only learns a LegacyHud's size when its renderer draws it, which it never does for a
   * hidden HUD. getScaledWidth() then falls back to the Hud's static size (unrelated to our box), so
   * hidden modules were previewed 6-8x too large (preview scale = reported width / module width).
   */
  public float width() {
    AbstractHudModule m = module();
    return m != null ? LayoutMath.hostedSize(m.width(), hud.getEffectiveScale()) : hud.getScaledWidth();
  }

  public float height() {
    AbstractHudModule m = module();
    return m != null ? LayoutMath.hostedSize(m.height(), hud.getEffectiveScale()) : hud.getScaledHeight();
  }

  /** Scale the box is drawn at (OneConfig's effective scale, GUI-scale aware). */
  public float drawScale() { return hud.getEffectiveScale(); }

  public void moveTo(float x, float y) {
    hud.setX(x);
    hud.setY(y);
  }

  public float scale() { return hud.getCustomScale(); }

  public void scale(float value) {
    if (hud instanceof LegacyHudMarker && !((LegacyHudMarker) hud).getSupportsScale()) return;
    float s = Math.max(AbstractHudModule.MIN_SCALE, Math.min(AbstractHudModule.MAX_SCALE, value));
    // keep the box's centre where it is while it grows or shrinks
    float cx = x() + width() / 2, cy = y() + height() / 2;
    hud.setCustomScale(s);
    moveTo(cx - width() / 2, cy - height() / 2);
  }

  public boolean locked() { return hud.getLocked(); }

  public void dragStart() { hud.onEditorDragStart(); }

  public void dragEnd() {
    hud.onEditorDragEnd();
    save();
  }

  public void resetLayout() {
    AbstractHudModule m = module();
    if (m == null) return; // another mod's HUD: its defaults are OneConfig's business
    m.resetLayout();
    hud.setCustomScale(1);
    float[] b = m.bounds(new ScaledResolution(Minecraft.getMinecraft()));
    moveTo(b[0], b[1]);
    save();
  }

  public AbstractHudModule module() { return hud instanceof LukaHud ? ((LukaHud) hud).module() : null; }

  public boolean oneConfig() { return true; }

  void save() {
    try { hud.save(); }
    catch (Throwable error) { Cbui.reportOnce("OneConfig HUD save: " + name(), error); }
  }
}
