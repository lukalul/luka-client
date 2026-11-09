package dev.cbui.hud;

import dev.cbui.Cbui;
import dev.cbui.hud.module.*;
import dev.cbui.oneconfig.ConfigSnapshot;
import dev.cbui.oneconfig.OneConfigFacade;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

/**
 * Owns the CBUI modules. Each module with a HUD box is registered as a real OneConfig HUD when
 * OneConfig's HUD API is available ({@link HudBackend}); OneConfig then positions, scales, shows and
 * draws it (calling back into the module's box {@link AbstractHudModule#renderBox}).
 * Modules OneConfig does not (or no longer) host are drawn here natively, so a missing or failing
 * OneConfig never loses a HUD.
 */
public final class HudManager {
  private static final String BACKEND = "dev.cbui.oneconfig.hud.OneConfigHudBackend";
  private static volatile float partialTicks;
  private final Minecraft mc = Minecraft.getMinecraft();
  private final List<AbstractHudModule> modules = new ArrayList<>();
  private final Set<String> oneConfigPlaced = new LinkedHashSet<>();
  private final Map<AbstractHudModule, HudElement> nativeElements = new IdentityHashMap<>();
  private HudBackend backend;
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

  /** Registers the modules with OneConfig's HUD system; leaves CBUI fully native if that fails. */
  public void attachOneConfig() {
    // CBUI's own modules are placed natively (anchor + offset): one source of truth that the editor can move exactly.
    // Hosting them in OneConfig's HUD system is opt-in (-Dcbui.oneconfig=true).
    if (!Boolean.getBoolean("cbui.oneconfig")) return;
    if (!OneConfigFacade.loaded()) return;
    try {
      HudBackend b = (HudBackend) Class.forName(BACKEND).getConstructor().newInstance();
      b.register(Collections.unmodifiableList(modules), oneConfigPlaced);
      backend = b;
      System.out.println("[CBUI] HUD modules registered with OneConfig's HUD system");
    } catch (Throwable error) {
      backend = null;
      Cbui.reportOnce("OneConfig HUD registration (using native HUD rendering)", error);
    }
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
    HudBackend b = backend;
    if (b != null)
      try { b.tick(); }
      catch (Throwable error) { Cbui.reportOnce("OneConfig HUD sync", error); }
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
      if ((editor || module.enabled()) && module.hasHud() && !managed(module))
        try {
          if (resolution == null) resolution = new ScaledResolution(mc);
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

  /** Latest frame's partial ticks (OneConfig's HUD renderer does not pass them). */
  public static float partialTicks() { return partialTicks; }

  /** True when OneConfig currently hosts (places, scales and draws) this module. */
  public boolean managed(AbstractHudModule module) {
    HudBackend b = backend;
    try { return b != null && b.healthy() && b.manages(module); }
    catch (Throwable error) { Cbui.reportOnce("OneConfig HUD state", error); return false; }
  }

  public boolean oneConfigActive() {
    HudBackend b = backend;
    return b != null && b.healthy();
  }

  /** Every box the CBUI HUD editor can move: natively placed modules plus every OneConfig HUD. */
  public List<HudElement> editorElements() {
    List<HudElement> out = new ArrayList<>();
    for (AbstractHudModule module : modules) {
      if (!module.hasHud() || managed(module) || !module.enabled()) continue;
      HudElement e = nativeElements.get(module);
      if (e == null) nativeElements.put(module, e = new NativeHudElement(module));
      out.add(e);
    }
    out.addAll(oneConfigElements());
    return out;
  }

  /** Live OneConfig HUD instances (CBUI's own and other mods'); empty without OneConfig. */
  public List<HudElement> oneConfigElements() {
    HudBackend b = backend;
    if (b == null || !b.healthy()) return Collections.emptyList();
    try { return b.elements(); }
    catch (Throwable error) { Cbui.reportOnce("OneConfig HUD list", error); return Collections.emptyList(); }
  }

  /** Another mod's OneConfig HUD settings as CBUI rows (null when unavailable). */
  public ConfigSnapshot.Mod oneConfigOptions(HudElement element) {
    HudBackend b = backend;
    if (b == null) return null;
    try { return b.options(element); }
    catch (Throwable error) { Cbui.reportOnce("OneConfig HUD options", error); return null; }
  }

  public Set<String> oneConfigTreeIds() {
    HudBackend b = backend;
    if (b == null) return Collections.emptySet();
    try { return b.treeIds(); }
    catch (Throwable error) { Cbui.reportOnce("OneConfig HUD trees", error); return Collections.emptySet(); }
  }

  /** The CBUI HUD editor opened or closed. */
  public void editorOpen(boolean open) {
    if (editorOpen == open) return;
    editorOpen = open;
    HudCache.dirty = true;
    HudBackend b = backend;
    if (b != null)
      try { b.editorOpen(open); }
      catch (Throwable error) { Cbui.reportOnce("OneConfig HUD editor state", error); }
  }

  /** Module names whose native position was already copied into OneConfig (persisted in cbui.json). */
  public Set<String> oneConfigPlaced() { return oneConfigPlaced; }

  public AbstractHudModule find(String name) { for (AbstractHudModule m : modules) if (m.name().equals(name)) return m; return null; }
  public List<AbstractHudModule> modules() { return Collections.unmodifiableList(modules); }
}
