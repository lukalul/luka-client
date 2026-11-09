package dev.envoy.module.waypoint;

import dev.envoy.EnvoyConfig;
import dev.envoy.EnvoySettings;
import dev.envoy.gui.EnvoyGuiController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/** Owns waypoint key bindings and observes player death transitions. */
public final class WaypointController {
    private static final KeyBinding CREATE = new KeyBinding(
        "Create a Waypoint", EnvoySettings.waypointKeyCode, "Envoy");
    private static final KeyBinding MANAGE = new KeyBinding(
        "Manage Waypoints", EnvoySettings.waypointManageKeyCode, "Envoy");
    private static boolean installed;
    private static boolean previouslyDead;
    private static int knownCreateCode = Integer.MIN_VALUE;
    private static int knownManageCode = Integer.MIN_VALUE;

    private WaypointController() { }

    public static void tick() {
        Minecraft minecraft = Minecraft.getMinecraft();
        installBindings(minecraft);
        persistBindingChanges();
        dispatchKeyPresses(minecraft);
        observeDeath(minecraft);
    }

    private static void dispatchKeyPresses(Minecraft minecraft) {
        while (MANAGE.isPressed()) {
            if (canOpenScreen(minecraft)) EnvoyGuiController.requestOpenWaypointManager();
        }
        while (CREATE.isPressed()) {
            if (canOpenScreen(minecraft)) EnvoyGuiController.requestCreateWaypoint();
        }
    }

    private static boolean canOpenScreen(Minecraft minecraft) {
        return minecraft.theWorld != null && minecraft.thePlayer != null && minecraft.currentScreen == null;
    }

    private static void observeDeath(Minecraft minecraft) {
        if (minecraft.theWorld == null || minecraft.thePlayer == null) {
            previouslyDead = false;
            return;
        }
        boolean deadNow = minecraft.thePlayer.getHealth() <= 0.0F || minecraft.thePlayer.isDead;
        if (deadNow && !previouslyDead && EnvoySettings.deathWaypoints) recordDeath(minecraft);
        previouslyDead = deadNow;
    }

    private static void recordDeath(Minecraft minecraft) {
        String time = new SimpleDateFormat("HH:mm").format(new Date());
        LocalWaypoints.add("Death (" + time + ")",
            floor(minecraft.thePlayer.posX), floor(minecraft.thePlayer.posY),
            floor(minecraft.thePlayer.posZ), 0xFFFF3333);
        LocalWaypoints.trimDeaths(EnvoySettings.deathWaypointCount);
    }

    private static int floor(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    private static void persistBindingChanges() {
        int createCode = CREATE.getKeyCode();
        int manageCode = MANAGE.getKeyCode();
        if (knownCreateCode == Integer.MIN_VALUE) {
            knownCreateCode = createCode;
            knownManageCode = manageCode;
            EnvoySettings.waypointKeyCode = createCode;
            EnvoySettings.waypointManageKeyCode = manageCode;
            return;
        }
        if (createCode == knownCreateCode && manageCode == knownManageCode) return;
        knownCreateCode = createCode;
        knownManageCode = manageCode;
        EnvoySettings.waypointKeyCode = createCode;
        EnvoySettings.waypointManageKeyCode = manageCode;
        try { EnvoyConfig.save(); } catch (Throwable ignored) { }
    }

    private static void installBindings(Minecraft minecraft) {
        if (installed || minecraft.gameSettings == null) return;
        restoreOption(minecraft, CREATE);
        restoreOption(minecraft, MANAGE);
        KeyBinding[] existing = minecraft.gameSettings.keyBindings;
        KeyBinding[] expanded = Arrays.copyOf(existing, existing.length + 2);
        expanded[existing.length] = CREATE;
        expanded[existing.length + 1] = MANAGE;
        minecraft.gameSettings.keyBindings = expanded;
        KeyBinding.resetKeyBindingArrayAndHash();
        installed = true;
    }

    private static void restoreOption(Minecraft minecraft, KeyBinding binding) {
        File options = new File(minecraft.mcDataDir, "options.txt");
        if (!options.isFile()) return;
        String prefix = "key_" + binding.getKeyDescription() + ":";
        try {
            for (String line : Files.readAllLines(options.toPath(), StandardCharsets.UTF_8)) {
                if (line.startsWith(prefix)) {
                    binding.setKeyCode(Integer.parseInt(line.substring(prefix.length()).trim()));
                    return;
                }
            }
        } catch (Exception ignored) { }
    }
}
