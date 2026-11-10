package dev.cbui.hud;

import dev.cbui.Cbui;
import dev.cbui.hud.module.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

/** Owns the CBUI modules and draws them (each module is placed by its own anchor + offset). */
public final class HudManager {
  private static volatile float partialTicks;
  private final Minecraft mc = Minecraft.getMinecraft();
  private final List<AbstractHudModule> modules = new ArrayList<>();
  private final Map<AbstractHudModule, HudElement> nativeElements = new IdentityHashMap<>();
  private boolean leftDown, editorOpen;
  /** Timing of the HUD pass (CPU time spent issuing it), logged every 600 frames when config/cbui-hudprofile exists. */
  private final boolean profile = new java.io.File(Minecraft.getMinecraft().mcDataDir, "config/cbui-hudprofile").exists();
  private long guiStart, guiNanos;
  private long profileNanos;
  private int profileFrames, profileDrawn;

  public HudManager() {
    modules.add(new KeystrokesModule()); modules.add(new CpsModule()); modules.add(new FpsModule()); modules.add(new CoordinatesModule());
    modules.add(new ArmourModule()); modules.add(new PotionModule()); modules.add(new DirectionModule()); modules.add(new AlwaysSprintModule()); modules.add(new ToggleSneakModule()); modules.add(new PotionCounterModule()); modules.add(new ItemCounterModule()); modules.add(new PearlCooldownModule()); modules.add(new ResourcePacksModule()); modules.add(new PingModule()); modules.add(new MemoryModule()); modules.add(new ClockModule()); modules.add(new ComboModule()); modules.add(new ReachModule()); modules.add(new CooldownsModule()); modules.add(new ServerAddressModule()); modules.add(new ScoreboardModule()); modules.add(new GuiBlurModule()); modules.add(new TeamHudModule()); modules.add(new AlertsModule());
    try { modules.addAll(dev.cbui.feature.Features.create()); } catch (Throwable error) { Cbui.reportOnce("feature modules", error); }
  }

  public void leftClick() {
    AbstractHudModule cps = find("CPS");
    if (cps instanceof CpsModule) ((CpsModule) cps).click();
  }

  public void tick() {
    for (int i = 0, n = modules.size(); i < n; i++) {
      AbstractHudModule module = modules.get(i);
      if (module.enabled())
        try { module.tick(); }
        catch (Throwable error) { module.enabled(false); Cbui.reportOnce("module tick: " + module.name(), error); }
    }
  }

  /** Start of vanilla's in-game GUI pass (only timed when profiling). */
  public void guiBegin() { if (profile) guiStart = System.nanoTime(); }

  /** End of vanilla's GUI pass, just before CBUI draws its own HUD. */
  public void guiEnd() { if (profile && guiStart != 0L) { guiNanos += System.nanoTime() - guiStart; guiStart = 0L; } }

  public void render(float partialTicks) { render(partialTicks, false); }

  public void render(float partialTicks, boolean editor) {
    HudManager.partialTicks = partialTicks;
    // the HUD editor draws its own preview of every box: the live HUD underneath would ghost through it
    if (editorOpen && !editor) return;
    if (mc.gameSettings.showDebugInfo && !editor) return;
    if (!editor && HudCache.active()) {
      HudCache.draw(new Runnable() {
        @Override public void run() { paintModules(HudManager.partialTicks, false); }
      });
      return;
    }
    paintModules(partialTicks, editor);
  }

  private void paintModules(float partialTicks, boolean editor) {
    long started = profile ? System.nanoTime() : 0L;
    int drawn = 0;
    ScaledResolution resolution = null;
    for (int i = 0, n = modules.size(); i < n; i++) {
      AbstractHudModule module = modules.get(i);
      if ((editor || module.enabled()) && module.hasHud())
        try {
          if (resolution == null) resolution = dev.luka.compat.Compat.scaled(mc);
          module.render(resolution, partialTicks, editor);
          if (!editor) HudCache.mark(module.bounds(resolution), resolution);
          drawn++;
        }
        catch (Throwable error) { module.enabled(false); Cbui.reportOnce("module render: " + module.name(), error); }
    }
    if (profile && !editor) {
      profileNanos += System.nanoTime() - started;
      profileDrawn += drawn;
      if (++profileFrames >= 600) {
        try {
          String line = String.format(java.util.Locale.ROOT, "HUD pass: %.1f us/frame CPU, %.1f modules drawn/frame; vanilla GUI pass: %.1f us/frame (600 frames)%n",
              profileNanos / 600.0 / 1000.0, profileDrawn / 600.0, guiNanos / 600.0 / 1000.0);
          guiNanos = 0L;
          java.nio.file.Files.write(new java.io.File(mc.mcDataDir, "config/cbui-hudprofile.log").toPath(), line.getBytes("UTF-8"),
              java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Throwable ignored) { }
        profileNanos = 0L; profileDrawn = 0; profileFrames = 0;
      }
    }
  }

  /** Latest frame's partial ticks. */
  public static float partialTicks() { return partialTicks; }

  /** Every box the CBUI HUD editor can move. */
  public List<HudElement> editorElements() {
    List<HudElement> out = new ArrayList<>();
    for (AbstractHudModule module : modules) {
      if (!module.hasHud() || !module.enabled()) continue;
      HudElement e = nativeElements.get(module);
      if (e == null) nativeElements.put(module, e = new NativeHudElement(module));
      out.add(e);
    }
    return out;
  }

  /** The CBUI HUD editor opened or closed. */
  public void editorOpen(boolean open) {
    if (editorOpen == open) return;
    editorOpen = open;
    HudCache.dirty = true;
  }

  public AbstractHudModule find(String name) { for (AbstractHudModule m : modules) if (m.name().equals(name)) return m; return null; }
  public List<AbstractHudModule> modules() { return Collections.unmodifiableList(modules); }
}
