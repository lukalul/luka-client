package dev.cbui.hud.module;

import dev.cbui.hud.Anchor;
import dev.cbui.hud.CpsCounter;

public final class CpsModule extends ValueModule {
  private final CpsCounter counter = new CpsCounter(64);

  public CpsModule() { super("CPS", true, Anchor.TOP_RIGHT, -5, 5, "CPS", "12", new Range(50, 1, 0, RED, 15, ORANGE)); }

  public void click() { counter.click(System.currentTimeMillis()); }

  @Override public void tick() { value(counter.count(System.currentTimeMillis())); }
}
