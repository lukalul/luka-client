package dev.cbui.hud.module;

import dev.cbui.hud.Anchor;
import net.minecraft.client.Minecraft;

public final class FpsModule extends ValueModule {
  public FpsModule() { super("FPS", true, Anchor.TOP_LEFT, 5, 5, "FPS", "144", new Range(1000, 1, 60, RED, 144, GREEN)); }

  @Override public void tick() {
    // in a SubFrame replay or render: the frame rate the player had when it was recorded, not the replay's drawing speed
    int recorded = replayFps();
    value(recorded >= 0 ? recorded : Minecraft.getDebugFPS());
  }

  private static java.lang.reflect.Method replayFps;
  private static boolean looked;

  private static int replayFps() {
    if (!looked) {
      looked = true;
      try { replayFps = Class.forName("dev.subframe.SubFrameMod").getMethod("replayFps"); } catch (Throwable noSubFrame) { }
    }
    if (replayFps == null) return -1;
    try { return (Integer) replayFps.invoke(null); } catch (Throwable e) { return -1; }
  }
}
