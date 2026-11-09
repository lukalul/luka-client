package dev.cbui.oneconfig.hud;

import dev.cbui.Cbui;
import dev.cbui.hud.AbstractHudModule;
import dev.cbui.hud.HudBackend;
import dev.cbui.hud.HudElement;
import dev.cbui.hud.TwoWaySync;
import dev.cbui.oneconfig.ConfigBridge;
import dev.cbui.oneconfig.ConfigSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;

/**
 * Links CBUI to OneConfig 1.6.1's HUD API ({@code org.polyfrost.oneconfig.api.hud.v1}). Loaded by
 * name from {@link dev.cbui.hud.HudManager#attachOneConfig}; any linkage or runtime failure turns
 * it unhealthy and CBUI falls back to native HUD rendering.
 *
 * <p>Per module, once its OneConfig instance exists: the first time ever, CBUI's native position
 * and scale are copied into the HUD (remembered in cbui.json); afterwards OneConfig's stored values
 * win at start-up. Enabled (= not hidden) and scale are then kept in sync both ways, last writer
 * wins, so CBUI's cards/settings and OneConfig's own HUD editor can both be used.
 */
public final class OneConfigHudBackend implements HudBackend {
  private final Map<String, Link> links = new HashMap<>();
  private final Map<Hud, OneConfigHudElement> elements = new WeakHashMap<>();
  private volatile Set<AbstractHudModule> managed = Collections.emptySet();
  private volatile boolean healthy = true;
  private Set<AbstractHudModule> spare = Collections.newSetFromMap(new IdentityHashMap<AbstractHudModule, Boolean>());
  private Set<String> placed = new HashSet<>();
  private Boolean savedOverride;

  private static final class Link {
    final TwoWaySync enabled = new TwoWaySync(), scale = new TwoWaySync();
  }

  public OneConfigHudBackend() {}

  @Override
  public void register(List<AbstractHudModule> modules, Set<String> placed) {
    this.placed = placed;
    int count = 0;
    for (AbstractHudModule m : modules) {
      if (!m.hasHud()) continue;
      LukaHud hud = LukaHuds.create(m.name());
      if (hud == null) continue;
      HudManager.register(hud);
      count++;
    }
    if (count == 0) throw new IllegalStateException("no CBUI HUD classes registered");
  }

  @Override
  public void tick() {
    if (!healthy) return;
    try {
      Set<AbstractHudModule> now = spare;
      now.clear();
      for (Hud hud : HudManager.instances()) {
        if (!(hud instanceof LukaHud)) continue;
        LukaHud cb = (LukaHud) hud;
        AbstractHudModule m = cb.module();
        if (m == null || cb.failed() || now.contains(m)) continue;
        if (sync(cb, m)) now.add(m);
      }
      Set<AbstractHudModule> old = managed;
      managed = now;
      spare = old.isEmpty() && old.getClass() != now.getClass()
          ? Collections.newSetFromMap(new IdentityHashMap<AbstractHudModule, Boolean>()) : old;
    } catch (Throwable error) {
      healthy = false;
      managed = Collections.emptySet();
      Cbui.reportOnce("OneConfig HUD backend (switched to native HUD rendering)", error);
    }
  }

  /** Pairs one instance with its module; false while OneConfig's screen size is not known yet. */
  private boolean sync(LukaHud hud, AbstractHudModule m) {
    String name = m.name();
    boolean fresh = !placed.contains(name);
    if (!hud.active()) {
      // OneConfig converts positions with the GUI size it saw on its last HUD frame; wait for one.
      if (HudManager.guiScreenWidth <= 0 || HudManager.guiScreenHeight <= 0) return false;
      if (Minecraft.getMinecraft().theWorld == null && fresh) return false;
      if (fresh) {
        hud.setCustomScale(m.scale());
        try { m.tick(); } catch (Throwable ignored) { } // measures the real text so right-hand boxes are placed flush with the edge
        float[] b = m.bounds(new ScaledResolution(Minecraft.getMinecraft()));
        hud.setX(b[0]);
        hud.setY(b[1]);
        hud.setHidden(!m.enabled());
        placed.add(name);
        save(hud);
        try { if (Cbui.config() != null) Cbui.config().save(Cbui.hud()); }
        catch (Throwable error) { Cbui.reportOnce("cbui.json save after OneConfig HUD migration", error); }
      }
      hud.activate();
    }
    Link link = links.get(name);
    if (link == null) links.put(name, link = new Link());
    int e = link.enabled.step(hud.getHidden() ? 0 : 1, m.userEnabled() ? 1 : 0, fresh);
    if (e == TwoWaySync.TO_LOCAL) m.enabled(!hud.getHidden());
    else if (e == TwoWaySync.TO_EXTERNAL) { hud.setHidden(!m.userEnabled()); save(hud); }
    int s = link.scale.step(hud.getCustomScale(), m.scale(), fresh);
    if (s == TwoWaySync.TO_LOCAL) m.scale(hud.getCustomScale());
    else if (s == TwoWaySync.TO_EXTERNAL) { hud.setCustomScale(m.scale()); save(hud); }
    if (s != TwoWaySync.NONE) link.scale.settle(hud.getCustomScale(), m.scale());
    return true;
  }

  @Override
  public boolean manages(AbstractHudModule module) {
    return healthy && managed.contains(module);
  }

  @Override
  public List<HudElement> elements() {
    if (!healthy) return Collections.emptyList();
    List<HudElement> out = new ArrayList<>();
    for (Hud hud : HudManager.instances()) {
      if (hud instanceof LukaHud && !((LukaHud) hud).active()) continue;
      OneConfigHudElement e = elements.get(hud);
      if (e == null) elements.put(hud, e = new OneConfigHudElement(hud));
      out.add(e);
    }
    return out;
  }

  @Override
  public ConfigSnapshot.Mod options(HudElement element) {
    if (!(element instanceof OneConfigHudElement) || element.module() != null) return null;
    Tree tree = ((OneConfigHudElement) element).hud.getTree();
    if (tree == null) return null;
    ConfigSnapshot snapshot = ConfigBridge.fromTrees(Collections.singletonList(tree), true);
    return snapshot.mods.isEmpty() ? null : snapshot.mods.get(0);
  }

  @Override
  public Set<String> treeIds() {
    Set<String> out = new HashSet<>();
    if (!healthy) return out;
    for (Hud hud : HudManager.instances()) {
      Tree tree = hud.getTree();
      if (tree != null) out.add(tree.getID());
    }
    return out;
  }

  @Override
  public void editorOpen(boolean open) {
    LukaHud.cbuiEditorOpen = open;
    if (open) {
      if (savedOverride == null) savedOverride = HudManager.overrideShowInScreens;
      HudManager.overrideShowInScreens = true; // other mods' HUDs stay visible under the editor
    } else if (savedOverride != null) {
      HudManager.overrideShowInScreens = savedOverride;
      savedOverride = null;
    }
  }

  @Override
  public boolean healthy() {
    return healthy;
  }

  private static void save(Hud hud) {
    try { hud.save(); }
    catch (Throwable error) { Cbui.reportOnce("OneConfig HUD save: " + hud.title, error); }
  }
}
