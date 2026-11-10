package dev.cbui.hud.module;

import dev.cbui.hud.Anchor;
import dev.cbui.hud.CpsCounter;

public final class CpsModule extends LabelModule {
  private final CpsCounter counter = new CpsCounter(64);

  public CpsModule() { super("CPS", true, Anchor.TOP_RIGHT, -5, 5, "12 CPS"); }

  public void click() { counter.click(System.currentTimeMillis()); }

  @Override public void tick() { label(counter.count(System.currentTimeMillis()), " CPS"); }
}
