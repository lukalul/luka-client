package dev.cbui.feature.bridge;

import dev.cbui.hud.AbstractHudModule;
import java.util.List;
import org.lwjgl.input.Keyboard;

/** The cards for Envoy's features and Motion Blur: every setting each of them has, edited from luka's own menu. */
public final class BridgeFeatures {
  private static final String A = "dev.envoy.EnvoySettings";
  private static final String AC = "dev.envoy.EnvoyConfig";
  private static final String SAVE_CLASS = AC;

  private BridgeFeatures() {}

  public static void addAll(List<AbstractHudModule> out) {
    out.add(nametags());
    out.add(staffTags());
    out.add(waypoints());
    out.add(simple("Team View", "teamView", "Shows your team's members through walls with the server's colours."));
    out.add(simple("Holograms", "holograms", "Floating text the server places in the world."));
    out.add(simple("Beams", "beams", "Light beams the server places in the world."));
    out.add(simple("World Border", "border", "Server-drawn borders and claim outlines."));
    out.add(simple("Notifications", "notifications", "Pop-up notifications sent by the server."));
    out.add(simple("Boss Bars", "bossBars", "Boss bars sent by the server."));
    out.add(simple("Titles", "titles", "Large on-screen titles sent by the server."));
    out.add(simple("Server TNT Timer", "tntCountdown", "The fuse timer the server sends for primed TNT."));
    out.add(cooldowns());
    out.add(combat());
    out.add(motionBlur());
  }

  /** One card for normal name tags and the server's (Apollo) tags: the server tags' settings move onto the Nametags card. */
  public static void merge(List<AbstractHudModule> all) {
    dev.cbui.feature.FeatureModule nametags = null, staffCard = null;
    BridgeModule server = null, staffTags = null;
    for (AbstractHudModule m : all) {
      if (m instanceof dev.cbui.feature.labels.LabelsModule) nametags = (dev.cbui.feature.FeatureModule) m;
      if (m instanceof dev.cbui.feature.staff.StaffModeModule) staffCard = (dev.cbui.feature.FeatureModule) m;
      if (m instanceof BridgeModule && m.name().equals("Nametags (server)")) server = (BridgeModule) m;
      if (m instanceof BridgeModule && m.name().equals("Staff nametags (server)")) staffTags = (BridgeModule) m;
    }
    if (nametags != null && server != null)
      server.embedInto(nametags, "Server tags (Apollo)", "Show the server's tags", true,
          "Draws the tags the server sends (CPS line, ranks, [Mod Mode]) in the style set on this card");
    if (staffCard != null && staffTags != null)
      staffTags.embedInto(staffCard, "Sticky nametags", "Sticky nametags", true,
          "Every other player gets a tag through walls and from far away (Apollo compatible). Needs the server to grant it");
  }

  private static BridgeModule simple(String name, String field, String tip) {
    BridgeModule m = new BridgeModule(name, "MODS", true, A, field).after(SAVE_CLASS, "save");
    return m;
  }

  private static BridgeModule nametags() {
    return new BridgeModule("Nametags (server)", "MODS", true, A, "nametags").after(SAVE_CLASS, "save")
        .slider("Maximum distance", "nametagMaxDistance", 64, 8, 128, 4, "How far away the server's tags are still drawn, in blocks")
        .toggle("Visible through walls", "nametagThroughWalls", true, "Server tags stay readable behind blocks");
  }

  /** Staff Mode's tag options: tags through walls and from far away, with Slinky-style details. Embedded in the Staff Mode card. */
  private static BridgeModule staffTags() {
    return new BridgeModule("Staff nametags (server)", "UTILITY", true, A, "staffSticky").after(SAVE_CLASS, "save")
        .slider("Sticky tag distance", "staffStickyDistance", 128, 16, 512, 16, "How far away other players still get a tag, in blocks")
        .toggle("Health", "staffTagHealth", true, "Health (and absorption) under the name, red when low")
        .toggle("Distance", "staffTagDistance", true, "How far the player is, in blocks")
        .toggle("Equipment", "staffTagEquipment", true, "The held item and armour as small icons above the tag")
        .toggle("Enchants", "staffTagEnchants", true, "Short enchant labels (P4, U3...) over each icon")
        .slider("Tag size", "staffTagScale", 1f, 0.25f, 3f, 0.05f, "How big the sticky tags are (the gear icons scale with them)")
        .toggle("Status", "staffTagStatus", true, "Shows Sneaking, Invisible, Sprinting, Blocking, Burning, Riding or Eating under the name")
        .toggle("Constant size", "staffTagConstantSize", false, "Keeps far tags readable by growing them with distance");
  }

  private static BridgeModule waypoints() {
    return new BridgeModule("Waypoints", "MODS", true, A, "waypoints").after(SAVE_CLASS, "save")
        .group("General")
        .slider("Maximum render distance", "waypointMaxDistance", 0, 0, 5000, 50, "In blocks; 0 means no limit")
        .group("Label")
        .toggle("Show names", "waypointName", true, "The waypoint's name above it")
        .toggle("Show distance", "waypointDistance", true, "How far away it is")
        .slider("Text size", "waypointTextScale", 1f, 0.5f, 3f, 0.1f, "Size of the label text")
        .toggle("Text shadow", "waypointTextShadow", true, "A dark shadow behind the letters")
        .toggle("Label background", "waypointLabelBackground", true, "A dark box behind the label")
        .slider("Label background opacity", "waypointLabelBackgroundOpacity", 0.45f, 0f, 1f, 0.05f, "How solid the box is")
        .toggle("Marker diamond", "waypointMarker", true, "The small diamond under the label")
        .group("Beam")
        .toggle("Show beams", "waypointBeam", true, "A light beam at the waypoint")
        .slider("Beam opacity", "waypointBeamOpacity", 0.4f, 0f, 1f, 0.05f, "How see-through the beam is")
        .slider("Beam width", "waypointBeamWidth", 1f, 0.5f, 4f, 0.1f, "Multiplier on the normal width")
        .group("Server waypoints (Apollo)")
        .toggle("Show server waypoints", "waypointShowServer", true, "Waypoints the server sends (Apollo). Your own waypoints always show")
        .group("Death")
        .toggle("Create a waypoint on death", "deathWaypoints", true, "Marks where you died")
        .slider("Death waypoints to keep", "deathWaypointCount", 3, 0, 20, 1, "How many are kept")
        .group("Creation")
        .color("Default new colour", "defaultWaypointColor", 0xFFFF5555, "Colour a new waypoint starts with");
  }

  private static BridgeModule cooldowns() {
    return new BridgeModule("Server Cooldowns", "MODS", false, A, "cooldowns").after(SAVE_CLASS, "save")
        .sliderIn(AC, "Position X", "cooldownX", 0.5f, 0f, 1f, 0.01f, "Horizontal position (0 left, 1 right)")
        .sliderIn(AC, "Position Y", "cooldownY", 0.5f, 0f, 1f, 0.01f, "Vertical position (0 top, 1 bottom)")
        .choice("Direction", AC, "cooldownDirection", 0, "Which way the timers stack", "Right", "Left", "Down", "Up");
  }

  private static BridgeModule combat() {
    return new BridgeModule("Combat 1.7 Feel", "UTILITY", false, A, "disableMissPenalty")
        .after("dev.envoy.module.combat.CombatState", "localSettingsChanged").after(SAVE_CLASS, "save")
        .toggle("Disable block miss penalty", "disableBlockMissPenalty", false, "Removes the delay after hitting an air-like block. Servers may treat this as unfair")
        .toggle("Allow dig and use", "allowDigAndUse", false, "Use an item while digging, as in 1.7. May conflict with server anti-cheat");
  }

  private static BridgeModule motionBlur() {
    String s = "dev.motionblur.MotionBlurSettings";
    String mb = "dev.motionblur.MotionBlur";
    return new BridgeModule("Motion Blur", "VISUALS", false, s, mb, "setEnabled")
        .after(mb, "settingsChanged").after(mb, "save")
        .choice("Mode", null, "mode", 2, "V1: bright things leave fading ghosts. V2: even smear, gets long at high levels. V3: even smear with a limit, never mushy", "V1 Phosphor", "V2 Blend", "V3 Capped blend")
        .slider("Level", "level", 3, 1, 10, 1, "How long the trail lasts. 1 is faint, 10 is a long smear")
        .toggle("Hand only", "handOnly", false, "Only your hand and held item get the blur (swings, sway when turning); the world stays sharp")
        .toggle("Blur while in menus", "blurMenus", true, "Keeps the world-background blur running while a menu is open")
        .slider("Blend every Nth frame", "frameSkip", 1, 1, 10, 1, "1 blends every frame; higher trades smoothness for less GPU work")
        .group("Smoothing")
        .toggle("Smooth swings", "smoothSwings", true, "Eases the start and restarts of the arm swing")
        .toggle("Smooth FOV", "smoothFov", true, "Eases sprint, speed and bow FOV changes between ticks")
        .toggle("Smooth hand sway", "smoothHandSway", true, "Eases the held item sway when turning");
  }
}
