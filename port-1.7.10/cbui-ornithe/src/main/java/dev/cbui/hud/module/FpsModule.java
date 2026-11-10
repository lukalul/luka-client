package dev.cbui.hud.module;

import dev.cbui.hud.Anchor;
import net.minecraft.client.Minecraft;

public final class FpsModule extends LabelModule {
  public FpsModule() { super("FPS", true, Anchor.TOP_LEFT, 5, 5, "144 FPS"); }

  @Override public void tick() { label(dev.luka.compat.Compat.fps(mc), " FPS"); }
}
