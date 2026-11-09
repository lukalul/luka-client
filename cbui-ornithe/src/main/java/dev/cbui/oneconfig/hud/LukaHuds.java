package dev.cbui.oneconfig.hud;

import org.polyfrost.oneconfig.api.hud.v1.Hud;

/**
 * One OneConfig HUD class per CBUI module. OneConfig keys HUD providers by class and restores saved
 * instances by class name with a no-argument constructor, so each module needs its own small class.
 * Do not rename these: the names are stored in OneConfig's HUD profile data.
 */
public final class LukaHuds {
  /** The OneConfig HUD for a CBUI module name, or null for modules without a HUD box. */
  static LukaHud create(String moduleName) {
    switch (moduleName) {
      case "Keystrokes": return new Keystrokes();
      case "CPS": return new Cps();
      case "FPS": return new Fps();
      case "Coordinates": return new Coordinates();
      case "Armour status": return new ArmourStatus();
      case "Potion status": return new PotionStatus();
      case "Direction": return new Direction();
      case "Always sprint": return new AlwaysSprint();
      case "Toggle sneak": return new ToggleSneak();
      case "Potion counter": return new PotionCounter();
      case "Resource packs": return new ResourcePacks();
      case "Ping": return new Ping();
      case "Memory": return new Memory();
      case "Clock": return new Clock();
      case "Combo": return new Combo();
      case "Reach": return new Reach();
      case "Cooldowns": return new Cooldowns();
      case "Server address": return new ServerAddress();
      case "Scoreboard": return new Scoreboard();
      default: return null;
    }
  }

  private static Hud.Category combat() { return Hud.Category.getCOMBAT(); }
  private static Hud.Category info() { return Hud.Category.getINFO(); }
  private static Hud.Category player() { return Hud.Category.getPLAYER(); }

  public static final class Keystrokes extends LukaHud { public Keystrokes() { super("Keystrokes", "cbui_keystrokes", combat()); } }
  public static final class Cps extends LukaHud { public Cps() { super("CPS", "cbui_cps", combat()); } }
  public static final class Fps extends LukaHud { public Fps() { super("FPS", "cbui_fps", info()); } }
  public static final class Coordinates extends LukaHud { public Coordinates() { super("Coordinates", "cbui_coordinates", info()); } }
  public static final class ArmourStatus extends LukaHud { public ArmourStatus() { super("Armour status", "cbui_armour_status", player()); } }
  public static final class PotionStatus extends LukaHud { public PotionStatus() { super("Potion status", "cbui_potion_status", player()); } }
  public static final class Direction extends LukaHud { public Direction() { super("Direction", "cbui_direction", info()); } }
  public static final class ToggleSneak extends LukaHud { public ToggleSneak() { super("Toggle sneak", "cbui_toggle_sneak", player()); } }
  public static final class AlwaysSprint extends LukaHud { public AlwaysSprint() { super("Always sprint", "cbui_always_sprint", player()); } }
  public static final class PotionCounter extends LukaHud { public PotionCounter() { super("Potion counter", "cbui_potion_counter", combat()); } }
  public static final class ResourcePacks extends LukaHud { public ResourcePacks() { super("Resource packs", "cbui_resource_packs", info()); } }
  public static final class Ping extends LukaHud { public Ping() { super("Ping", "cbui_ping", info()); } }
  public static final class Memory extends LukaHud { public Memory() { super("Memory", "cbui_memory", info()); } }
  public static final class Clock extends LukaHud { public Clock() { super("Clock", "cbui_clock", info()); } }
  public static final class Combo extends LukaHud { public Combo() { super("Combo", "cbui_combo", combat()); } }
  public static final class Reach extends LukaHud { public Reach() { super("Reach", "cbui_reach", combat()); } }
  public static final class Cooldowns extends LukaHud { public Cooldowns() { super("Cooldowns", "cbui_cooldowns", combat()); } }
  public static final class ServerAddress extends LukaHud { public ServerAddress() { super("Server address", "cbui_server_address", info()); } }
  public static final class Scoreboard extends LukaHud { public Scoreboard() { super("Scoreboard", "cbui_scoreboard", info()); } }

  private LukaHuds() {}
}
