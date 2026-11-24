package dev.cbui.feature;

import dev.cbui.hud.AbstractHudModule;
import dev.cbui.feature.betterhurtcam.BetterHurtCamModule;
import dev.cbui.feature.viewangle.ViewAngleModule;
import dev.cbui.feature.lookaround.LookAroundModule;
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
    add(new dev.cbui.feature.saturation.SaturationModule());
    add(new LookAroundModule());
    add(new ViewAngleModule());
    add(new dev.cbui.feature.hotbarwheel.HotbarWheelModule());
    add(new BetterHurtCamModule());
    add(new dev.cbui.feature.steadycam.SteadyCamModule());
    add(new dev.cbui.feature.hurttint.HurtTintModule());
    add(new dev.cbui.feature.skyhorizon.SkyHorizonModule());
    add(new dev.cbui.feature.worldfx.WorldEffectsModule());
    add(new dev.cbui.feature.viewoverlays.ViewOverlaysModule());
    add(new dev.cbui.feature.grounditems.GroundItemsModule());
    add(new dev.cbui.feature.leveldigits.LevelDigitsModule());
    add(new dev.cbui.feature.blockoverlay.BlockOverlayModule());
    add(new dev.cbui.feature.skyclock.SkyClockModule());
    add(new dev.cbui.feature.soundmixer.SoundMixerModule());
    add(new dev.cbui.feature.fusetimer.FuseTimerModule());
    add(new dev.cbui.feature.labels.LabelsModule());
    add(new dev.cbui.feature.hitboxes.HitboxesModule());
    add(new dev.cbui.feature.crosshair.CrosshairModule());
    add(new dev.cbui.feature.oldanimations.OldAnimationsModule());
    add(new dev.cbui.feature.noglint.NoGlintModule());
    add(new dev.cbui.feature.rawinput.RawInputModule());
    add(new dev.cbui.feature.shotchat.ShotChatModule());
    add(new dev.cbui.feature.crashguard.CrashGuardModule());
    add(new dev.cbui.feature.staff.StaffModeModule());
    dev.cbui.feature.bridge.BridgeFeatures.addAll(ALL);
    add(new dev.cbui.feature.leaveguard.LeaveGuardModule());
    add(new dev.cbui.feature.centerinventory.CenterInventoryModule());
    add(new dev.cbui.feature.zoom.ZoomModule());
    add(new dev.cbui.feature.autoreconnect.AutoReconnectModule());
    add(new dev.cbui.feature.profiler.FpsProfilerModule());
    add(new dev.cbui.feature.fullbright.FullbrightModule());
    add(new dev.cbui.feature.chat.ChatModule());
    add(new dev.cbui.feature.chatmacros.ChatMacrosModule());
    add(new dev.cbui.feature.nickhider.NickHiderModule());
    add(new dev.cbui.feature.tablist.TabListModule());
    if (dev.cbui.Release.SOCIAL) add(new dev.cbui.feature.online.LukaOnlineModule());
    add(new dev.cbui.feature.hideblocks.HideBlocksModule());
    if (!dev.cbui.feature.discord.DiscordStatusModule.APP_ID.isEmpty()) add(new dev.cbui.feature.discord.DiscordStatusModule());
    // FEATURES-REGISTER (one line per feature, added during integration): add(new LookAroundModule());
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
