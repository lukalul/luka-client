package dev.cbui.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

/** A CBUI module placed by CBUI itself (anchor + offset). */
final class NativeHudElement implements HudElement {
  private final AbstractHudModule module;

  NativeHudElement(AbstractHudModule module) {
    this.module = module;
  }

  private float[] b() {
    return module.bounds(dev.luka.compat.Compat.scaled(Minecraft.getMinecraft()));
  }

  public String name() { return module.name(); }
  public boolean enabled() { return module.enabled(); }
  public void enabled(boolean on) { module.enabled(on); }
  public float x() { return b()[0]; }
  public float y() { return b()[1]; }
  public float width() { return b()[2]; }
  public float height() { return b()[3]; }

  public void moveTo(float x, float y) {
    float[] b = b();
    module.offset(module.offsetX() + x - b[0], module.offsetY() + y - b[1]);
  }

  public float scale() { return module.scale(); }
  public void scale(float value) { module.scale(value); }
  public float drawScale() { return module.scale(); }
  public boolean locked() { return false; }
  public void dragStart() { }
  public void dragEnd() { module.reanchor(dev.luka.compat.Compat.scaled(Minecraft.getMinecraft())); }
  public boolean hudVisible() { return module.hudVisible(); }
  public void hudVisible(boolean visible) { module.hudVisible(visible); }
  public void resetLayout() { module.resetLayout(); }
  public AbstractHudModule module() { return module; }
}
