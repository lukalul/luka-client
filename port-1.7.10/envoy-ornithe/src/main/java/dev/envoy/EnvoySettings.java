package dev.envoy;

/**
 * Mutable runtime switches. Fields remain public for the optional menu bridge.
 * The staffNametag* fields were removed on 2026-11-22: nothing read them and they had no card. Old envoy.properties
 * files never stored them (EnvoyConfig only reads its own keys), so nothing needs migrating.
 */
public final class EnvoySettings {
    public static boolean renderSelf;
    public static boolean blockFreelookHandshake;
    public static boolean waypoints;
    public static boolean nametags;
    public static boolean teamView;
    public static boolean cooldowns;
    public static boolean holograms;
    public static boolean beams;
    public static boolean border;
    public static boolean notifications;
    public static boolean coloredFire;
    public static boolean bossBars;
    public static boolean titles;
    public static boolean tntCountdown;

    public static boolean disableMissPenalty;
    public static boolean disableBlockMissPenalty;
    public static boolean allowDigAndUse;

    public static boolean waypointBeam;
    public static boolean waypointName;
    public static boolean waypointDistance;
    public static float waypointTextScale;
    public static int waypointMaxDistance;
    public static float waypointBeamOpacity;
    public static int waypointKeyCode;
    public static int waypointManageKeyCode;
    public static boolean deathWaypoints;
    public static int deathWaypointCount;
    public static int defaultWaypointColor;

    public static float nametagScale;
    public static float nametagHeight;
    public static boolean nametagBackground;
    public static float nametagBackgroundOpacity;
    public static int nametagBackgroundColor;
    public static boolean nametagShadow;
    public static boolean nametagThroughWalls;
    public static int nametagMaxDistance;

    public static boolean nametagsOnlyStaff;
    public static boolean nametagsHidden;
    public static boolean staffSticky;
    public static boolean staffTagHealth;
    public static boolean staffTagDistance;
    public static boolean staffTagEquipment;
    public static boolean staffTagEnchants;
    public static boolean staffTagConstantSize;
    public static boolean staffTagStatus;
    public static float staffTagScale;
    public static int staffStickyDistance;
    public static boolean staffDetectTag;
    public static int staffDetectText;

    public static boolean waypointShowServer;
    public static boolean waypointLabelBackground;
    public static float waypointLabelBackgroundOpacity;
    public static boolean waypointMarker;
    public static float waypointBeamWidth;
    public static boolean waypointTextShadow;

    static {
        installDefaults();
    }

    private EnvoySettings() { }

    private static void installDefaults() {
        renderSelf = blockFreelookHandshake = true;
        waypoints = nametags = teamView = cooldowns = true;
        holograms = beams = border = notifications = coloredFire = true;
        bossBars = titles = tntCountdown = true;

        waypointBeam = waypointName = waypointDistance = deathWaypoints = true;
        waypointTextScale = 1.0F;
        waypointBeamOpacity = 0.4F;
        waypointKeyCode = org.lwjgl.input.Keyboard.KEY_N;
        waypointManageKeyCode = org.lwjgl.input.Keyboard.KEY_M;
        deathWaypointCount = 3;
        defaultWaypointColor = 0xFFFF5555;

        nametagScale = staffTagScale = 1.0F;
        nametagBackground = nametagThroughWalls = true;
        nametagBackgroundOpacity = 0.25F;
        nametagMaxDistance = 64;

        staffSticky = staffTagHealth = staffTagDistance = staffTagEquipment = true;
        staffTagEnchants = staffTagStatus = staffDetectTag = true;
        staffStickyDistance = 128;

        waypointShowServer = waypointLabelBackground = waypointMarker = waypointTextShadow = true;
        waypointLabelBackgroundOpacity = 0.45F;
        waypointBeamWidth = 1.0F;
    }
}
