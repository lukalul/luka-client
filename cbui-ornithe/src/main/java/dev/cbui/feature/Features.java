package dev.cbui.feature;

import dev.cbui.hud.AbstractHudModule;
import dev.cbui.feature.shaketweaks.ShakeTweaksModule;
import dev.cbui.feature.damagetint.DamageTintModule;
import dev.cbui.feature.betterhurtcam.BetterHurtCamModule;
import dev.cbui.feature.scrolltweaks.ScrollTweaksModule;
import dev.cbui.feature.fovchanger.FOVChangerModule;
import dev.cbui.feature.freelook.FreelookModule;
import java.util.ArrayList;
import java.util.List;

/** Every ported feature module, created once and registered with the HUD manager. Mixins look their module up here. */
public final class Features {
  private static final List<AbstractHudModule> ALL = new ArrayList<>();

  /** A module that pushes its values somewhere else (another mod's settings) and needs to run every tick, even while switched off. */
  public interface Synced {
    void sync();
  }

  private Features() {}

  /** Called every client tick. */
  public static void syncAll() {
    for (int i = 0, n = ALL.size(); i < n; i++) {
      AbstractHudModule m = ALL.get(i);
      if (m instanceof Synced) {
        try { ((Synced) m).sync(); } catch (Throwable ignored) { /* a module's own guard reports */ }
      }
    }
  }

  /** Creates all feature modules (called once by the HUD manager). A feature that fails to construct is skipped, not fatal. */
  public static synchronized List<AbstractHudModule> create() {
    if (!ALL.isEmpty()) return ALL;
    add(new FreelookModule());
    add(new FOVChangerModule());
    add(new ScrollTweaksModule());
    add(new BetterHurtCamModule());
    add(new ShakeTweaksModule());
    add(new DamageTintModule());
    add(new dev.cbui.feature.smoothskies.SmoothSkiesModule());
    add(new dev.cbui.feature.rendertweaks.RenderTweaksModule());
    add(new dev.cbui.feature.overlaytweaks.OverlayTweaksModule());
    add(new dev.cbui.feature.droppeditemtweaks.DroppedItemTweaksModule());
    add(new dev.cbui.feature.numericalenchantments.NumericalEnchantmentsModule());
    add(new dev.cbui.feature.blockoverlay.BlockOverlayModule());
    add(new dev.cbui.feature.polytime.PolyTimeModule());
    add(new dev.cbui.feature.soundtweaks.SoundTweaksModule());
    add(new dev.cbui.feature.tntcountdown.TNTCountdownModule());
    add(new dev.cbui.feature.polynametag.PolyNametagModule());
    add(new dev.cbui.feature.polyhitbox.PolyHitboxModule());
    add(new dev.cbui.feature.polycrosshair.PolyCrosshairModule());
    add(new dev.cbui.feature.oldanimations.OldAnimationsModule());
    add(new dev.cbui.feature.screenshotmessageenhancer.ScreenshotMessageEnhancerModule());
    add(new dev.cbui.feature.crashpatch.CrashPatchModule());
    add(new dev.cbui.feature.staff.StaffModeModule());
    dev.cbui.feature.bridge.BridgeFeatures.addAll(ALL);
    add(new dev.cbui.feature.confirmdisconnect.ConfirmDisconnectModule());
    add(new dev.cbui.feature.polyzoom.PolyZoomModule());
    add(new dev.cbui.feature.autoreconnect.AutoReconnectModule());
    add(new dev.cbui.feature.profiler.FpsProfilerModule());
    add(new dev.cbui.feature.fullbright.FullbrightModule());
    add(new dev.cbui.feature.chat.ChatModule());
    add(new dev.cbui.feature.chatmacros.ChatMacrosModule());
    if (dev.cbui.Release.SOCIAL) add(new dev.cbui.feature.online.LukaOnlineModule());
    add(new dev.cbui.feature.hideblocks.HideBlocksModule());
    if (!dev.cbui.feature.discord.DiscordStatusModule.APP_ID.isEmpty()) add(new dev.cbui.feature.discord.DiscordStatusModule());
    // FEATURES-REGISTER (one line per feature, added during integration): add(new FreelookModule());
    try { dev.cbui.feature.bridge.BridgeFeatures.merge(ALL); } catch (Throwable error) { dev.cbui.Cbui.reportOnce("merge nametag cards", error); }
    LOOKUP.clear();
    created = true;
    return ALL;
  }

  @SuppressWarnings("unused")
  private static void add(AbstractHudModule module) {
    ALL.add(module);
  }

  /** Answers of {@link #get}: a lookup runs from render and world hooks many times a frame, so it is a lock-free map read, not a scan. */
  private static final java.util.concurrent.ConcurrentHashMap<Class<?>, Object> LOOKUP = new java.util.concurrent.ConcurrentHashMap<Class<?>, Object>();
  private static final Object MISSING = new Object();
  private static volatile boolean created;

  /** The module of this class, or null when it is not registered. */
  @SuppressWarnings("unchecked")
  public static <T extends AbstractHudModule> T get(Class<T> type) {
    Object hit = LOOKUP.get(type);
    if (hit != null) return hit == MISSING ? null : (T) hit;
    return scan(type);
  }

  @SuppressWarnings("unchecked")
  private static synchronized <T extends AbstractHudModule> T scan(Class<T> type) {
    for (AbstractHudModule m : ALL) {
      if (type.isInstance(m)) {
        LOOKUP.put(type, m);
        return (T) m;
      }
    }
    if (created) LOOKUP.put(type, MISSING); // nothing will be added any more, so "not there" is final
    return null;
  }

  /** True when the module exists and is switched on. Safe to call from any mixin. */
  public static boolean on(Class<? extends AbstractHudModule> type) {
    AbstractHudModule m = get(type);
    return m != null && m.enabled();
  }
}
