package dev.envoy.module.staff;

import dev.envoy.Envoy;
import dev.envoy.EnvoySettings;
import dev.envoy.module.nametag.AdventureText;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

public final class StaffState {
    private static final String[] DETECT_TEXTS = {"mod mode", "staff", "vanish"};
    private static final Set<String> MODS = ConcurrentHashMap.newKeySet();
    private static volatile boolean apollo;
    private static volatile boolean tag;
    private static volatile boolean announced;
    private StaffState() { }

    public static boolean active() { return apollo || tag; }
    public static Set<String> mods() { return MODS; }
    public static boolean allowsNametags() { return apollo ? hasPermission("NAMETAGS") : tag; }
    public static boolean allowsXray() { return apollo && hasPermission("XRAY"); }

    public static void apolloEnable(List<String> granted) {
        MODS.addAll(granted);
        apollo = true;
        publishChange();
    }

    public static void apolloDisable(List<String> revoked) {
        MODS.removeAll(revoked);
        apollo = !MODS.isEmpty();
        publishChange();
    }

    public static void ownTag(List<List<AdventureText.Run>> lines) {
        boolean detected = EnvoySettings.staffDetectTag && containsMarker(lines, selectedMarker());
        if (tag != detected) {
            tag = detected;
            publishChange();
        }
    }

    public static void clear() {
        boolean wasActive = active();
        MODS.clear();
        apollo = false;
        tag = false;
        announced = false;
        if (wasActive) Envoy.LOGGER.info("[Staff] cleared on disconnect");
    }

    private static boolean hasPermission(String expected) {
        for (String permission : MODS) {
            if (expected.equals(permission) || "STAFF_MODS".equals(permission) || permission.startsWith("MOD_")) return true;
        }
        return false;
    }

    private static String selectedMarker() {
        int index = Math.max(0, Math.min(DETECT_TEXTS.length - 1, EnvoySettings.staffDetectText));
        return DETECT_TEXTS[index];
    }

    private static boolean containsMarker(List<List<AdventureText.Run>> lines, String marker) {
        if (lines == null) return false;
        for (List<AdventureText.Run> line : lines) {
            StringBuilder plain = new StringBuilder();
            for (AdventureText.Run run : line) plain.append(run.display);
            String normalized = plain.toString().replaceAll("\u00a7.", "").toLowerCase(Locale.ROOT);
            if (normalized.contains(marker)) return true;
        }
        return false;
    }

    private static void publishChange() {
        final boolean enabled = active();
        Envoy.LOGGER.info("[Staff] staff mode {} (apollo={}, tag={}, mods={})", enabled ? "on" : "off", apollo, tag, MODS);
        if (announced == enabled) return;
        announced = enabled;
        final String notice = enabled
            ? "Staff mode detected - the Staff card is now in the mods menu (server tags are shown while it lasts)."
            : "Staff mode ended - normal name tags are back.";
        final Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.addScheduledTask(new Runnable() {
            public void run() {
                if (minecraft.thePlayer != null) minecraft.thePlayer.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.DARK_PURPLE + "[luka] " + EnumChatFormatting.WHITE + notice));
            }
        });
    }
}
