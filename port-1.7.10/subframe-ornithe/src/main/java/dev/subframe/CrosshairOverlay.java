package dev.subframe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import dev.luka.compat.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.FileReader;
import java.util.Locale;

/**
 * The crosshair in a render. Minecraft draws it with an inverting blend, so its colour depends on what is behind it
 * and it has to be drawn onto the finished picture, not into each of the subframes that get averaged. While a render
 * runs the game's own crosshair is cancelled and this one is drawn once per output frame, with the same texture
 * (the crosshair in the active resource pack's icons.png) and the same blend the game uses.
 */
public final class CrosshairOverlay {
    static boolean shouldCancelVanilla() {
        try {
            Replay r = Replay.active;
            return r != null && r.cancelsCrosshair();
        } catch (Throwable t) {
            SubFrameMod.problem("crosshairCancel", t);
            return false;
        }
    }

    // ---- Lunar Client's crosshair mod draws its own crosshair (and ignores the Forge event that cancels the game's), ----
    // ---- so while it is active the game's drawing is left alone: a second inverted draw would undo the first. ----

    private static boolean lunarModActive;
    private static long lunarRead;
    private static boolean lunarLogged;

    private static boolean isLunar() {
        String cmd = System.getProperty("sun.java.command", "").toLowerCase(Locale.ROOT);
        return cmd.contains("ichor") || cmd.contains("lunar");
    }

    /** True when Lunar Client is running and its crosshair mod is enabled in the active profile. Re-read every few seconds. */
    static boolean lunarCrosshairActive() {
        long now = System.currentTimeMillis();
        if (now - lunarRead < 5000) {
            return lunarModActive;
        }
        lunarRead = now;
        lunarModActive = false;
        if (!isLunar()) {
            return false;
        }
        try {
            File base = new File(System.getProperty("user.home"), ".lunarclient/settings");
            String profile = "Default";
            File launcher = new File(base, "launcher.json");
            String active = null;
            if (launcher.isFile()) {
                com.google.gson.JsonObject root = new com.google.gson.JsonParser().parse(new FileReader(launcher)).getAsJsonObject();
                com.google.gson.JsonObject st = root.has("settings") ? root.getAsJsonObject("settings") : root;
                if (st.has("gameProfile")) {
                    active = st.get("gameProfile").getAsString();
                }
            }
            File pm = new File(base, "game/profile_manager.json");
            if (active != null && pm.isFile()) {
                String text = new String(java.nio.file.Files.readAllBytes(pm.toPath()), "UTF-8");
                int at = text.indexOf(active);
                if (at >= 0) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(text.substring(at, Math.min(text.length(), at + 400)));
                    if (m.find() && new File(base, "game/" + m.group(1)).isDirectory()) {
                        profile = m.group(1);
                    }
                }
            }
            File mods = new File(base, "game/" + profile + "/mods.json");
            if (mods.isFile()) {
                com.google.gson.JsonObject c = new com.google.gson.JsonParser().parse(new FileReader(mods)).getAsJsonObject().getAsJsonObject("CROSSHAIR");
                lunarModActive = c != null && c.has("enabled") && c.get("enabled").getAsBoolean();
            }
            if (!lunarLogged) {
                lunarLogged = true;
                SubFrameMod.LOG.info("[crosshair] Lunar Client, profile " + profile + ", crosshair mod " + (lunarModActive ? "on: its own crosshair is left as the game draws it" : "off: the resource pack's crosshair is drawn on the finished frame"));
            }
        } catch (Throwable t) {
            lunarModActive = false;
        }
        return lunarModActive;
    }

    /** luka's own crosshair (custom shapes and colours), when that client is installed and its crosshair module is on. */
    private static boolean lukaCrosshair() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            mc.entityRenderer.setupOverlayRendering();
            Class<?> c = Class.forName("dev.cbui.feature.crosshair.CrosshairRenderer");
            return (Boolean) c.getMethod("render", boolean.class).invoke(null, true);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Draws the crosshair on top of the picture that is bound right now. */
    static void draw() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.thirdPersonView != 0 || mc.gameSettings.hideGUI) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            if (lukaCrosshair()) {
                return;
            }
            ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            mc.entityRenderer.setupOverlayRendering();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.disableLighting();
            GlStateManager.disableFog();
            GlStateManager.enableTexture2D();
            mc.getTextureManager().bindTexture(Gui.icons);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(775, 769, 1, 0);
            GlStateManager.enableAlpha();
            GlStateManager.color(1f, 1f, 1f, 1f);
            new Gui().drawTexturedModalRect(sr.getScaledWidth() / 2 - 7, sr.getScaledHeight() / 2 - 7, 0, 0, 16, 16);
        } finally {
            GL11.glPopAttrib();
            // the attribute stack puts the real GL state back; GlStateManager's cached copy must agree with it
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
    }
}
