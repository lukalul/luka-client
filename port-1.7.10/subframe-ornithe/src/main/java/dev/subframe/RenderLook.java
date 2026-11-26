package dev.subframe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * How a render from the replay editor looks: options the editor's render job sets (RenderJob) for that render only.
 * The render runs in its own copy of the game, so these change nothing the player keeps: game options are set in memory
 * (and that copy never writes options.txt, see mixin LookOptionsMixin), luka's HUD modules are switched without saving.
 *
 * <pre>
 * look.fov=90  look.renderDistance=12  look.brightness=0..100|full  look.particles=all|decreased|minimal
 * look.bobbing=on|off  look.time=0..23999  look.weather=clear  look.resourcePack=&lt;file name in resourcepacks&gt;
 * hide.hud  hide.hand  hide.crosshair  hide.chat  hide.scoreboard  hide.nametags   (=true)
 * hud.&lt;luka HUD module name&gt;=true|false
 * </pre>
 * Unset keys leave the game as it is (the recording's settings, the player's options).
 */
final class RenderLook {
    static int fov = -1, renderDistance = -1, particles = -1, bobbing = -1, time = -1;
    /** Brightness in percent (0..100), 1000 for fullbright, -1 unchanged. */
    static int brightness = -1;
    static boolean clearWeather, hideHud, hideHand, hideCrosshair, hideChat, hideScoreboard, hideNametags;
    static String resourcePack = "";
    static final Map<String, Boolean> hud = new LinkedHashMap<String, Boolean>();
    /** Render server: luka's HUD module switches as the game started, put back between jobs. */
    private static final Map<String, Boolean> hudAtStart = new LinkedHashMap<String, Boolean>();
    /** The pack applyResourcePack loaded ("" = the player's own packs), and the player's packs to go back to. */
    private static String appliedPack = "";
    private static java.util.List<net.minecraft.client.resources.ResourcePackRepository$Entry> originalEntries;
    private static java.util.List<String> originalNames;

    private RenderLook() {
    }

    static void load(Properties p) {
        fov = num(p, "look.fov", 30, 110);
        renderDistance = num(p, "look.renderDistance", 2, 32);
        time = num(p, "look.time", 0, 23999);
        String b = p.getProperty("look.brightness", "").trim();
        brightness = "full".equalsIgnoreCase(b) ? 1000 : num(p, "look.brightness", 0, 100);
        String part = p.getProperty("look.particles", "").trim().toLowerCase();
        particles = part.equals("all") ? 0 : part.equals("decreased") ? 1 : part.equals("minimal") ? 2 : -1;
        String bob = p.getProperty("look.bobbing", "").trim().toLowerCase();
        bobbing = bob.equals("on") ? 1 : bob.equals("off") ? 0 : -1;
        clearWeather = "clear".equalsIgnoreCase(p.getProperty("look.weather", "").trim());
        resourcePack = p.getProperty("look.resourcePack", "").trim();
        hideHud = flag(p, "hide.hud");
        hideHand = flag(p, "hide.hand");
        hideCrosshair = flag(p, "hide.crosshair");
        hideChat = flag(p, "hide.chat");
        hideScoreboard = flag(p, "hide.scoreboard");
        hideNametags = flag(p, "hide.nametags");
        hud.clear();
        for (String key : p.stringPropertyNames()) {
            if (key.startsWith("hud.")) {
                hud.put(key.substring(4), Boolean.parseBoolean(p.getProperty(key).trim()));
            }
        }
        SubFrameMod.LOG.info("[look] fov=" + fov + " renderDistance=" + renderDistance + " brightness=" + brightness + " particles=" + particles
                + " bobbing=" + bobbing + " time=" + time + " clearWeather=" + clearWeather + " pack='" + resourcePack + "' hide: hud=" + hideHud
                + " hand=" + hideHand + " crosshair=" + hideCrosshair + " chat=" + hideChat + " scoreboard=" + hideScoreboard
                + " nametags=" + hideNametags + " modules=" + hud);
    }

    private static int num(Properties p, String key, int min, int max) {
        try {
            String v = p.getProperty(key);
            return v == null || v.trim().isEmpty() ? -1 : Math.max(min, Math.min(max, Integer.parseInt(v.trim())));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean flag(Properties p, String key) {
        return Boolean.parseBoolean(p.getProperty(key, "false").trim());
    }

    /** Game options, after the replay set its own (Replay.begin). */
    static void applySettings(GameSettings gs) {
        if (fov > 0) {
            gs.fovSetting = fov;
        }
        if (renderDistance > 0) {
            gs.renderDistanceChunks = renderDistance;
        }
        if (bobbing >= 0) {
            gs.viewBobbing = bobbing == 1;
        }
        if (particles >= 0) {
            gs.particleSetting = particles;
        }
        if (brightness >= 0) {
            gs.gammaSetting = brightness == 1000 ? 10f : brightness / 100f;
        }
    }

    /** The world just before a frame is drawn: a fixed time of day, no rain or thunder. */
    static void applyWorld(Minecraft mc) {
        if (mc.theWorld == null) {
            return;
        }
        if (time >= 0) {
            long day = mc.theWorld.getWorldInfo().getWorldTime();
            // keep the day count (the moon phase), set the time within the day
            mc.theWorld.getWorldInfo().setWorldTime(day - Math.floorMod(day, 24000L) + time);
        }
        if (clearWeather) {
            mc.theWorld.setRainStrength(0f);
            mc.theWorld.setThunderStrength(0f);
        }
    }

    /**
     * luka's HUD modules for this render: the editor's choices, and the ones the other options need off (all of them for
     * hide.hud; Scoreboard, Crosshair, Time Changer, Fullbright when those are taken over). Switched in memory only:
     * nothing calls luka's save. Without luka (cbui) there is nothing to switch.
     */
    static void applyHud() {
        Map<String, Boolean> want = new LinkedHashMap<String, Boolean>(hud);
        if (hideScoreboard) {
            want.put("Scoreboard", false);
        }
        if (hideCrosshair) {
            want.put("Crosshair", false);
        }
        if (time >= 0) {
            want.put("Time Changer", false);
        }
        if (brightness >= 0) {
            want.put("Fullbright", false);
        }
        try {
            Class<?> cbui = Class.forName("dev.cbui.Cbui");
            Object manager = cbui.getMethod("hud").invoke(null);
            if (manager == null) {
                return;
            }
            if (hideHud) {
                for (Object m : (Iterable<?>) manager.getClass().getMethod("modules").invoke(manager)) {
                    Method hasHud = m.getClass().getMethod("hasHud");
                    if (Boolean.TRUE.equals(hasHud.invoke(m))) {
                        want.put((String) m.getClass().getMethod("name").invoke(m), false);
                    }
                }
            }
            Method find = manager.getClass().getMethod("find", String.class);
            int switched = 0;
            for (Map.Entry<String, Boolean> e : want.entrySet()) {
                Object m = find.invoke(manager, e.getKey());
                if (m != null) {
                    m.getClass().getMethod("enabled", boolean.class).invoke(m, e.getValue());
                    switched++;
                }
            }
            try {
                Class.forName("dev.cbui.hud.HudCache").getField("dirty").setBoolean(null, true);
            } catch (Throwable ignored) {
                // no HUD cache: it draws every frame anyway
            }
            SubFrameMod.LOG.info("[look] luka modules switched for this render: " + switched + " of " + want.size());
        } catch (ClassNotFoundException noLuka) {
            // SubFrame without luka's client mod
        } catch (Throwable t) {
            SubFrameMod.LOG.warn("[look] could not switch luka's HUD modules: " + t);
        }
    }

    /** Back to "nothing changed" (render server, between jobs). The loaded resource pack stays until a job wants another. */
    static void reset() {
        fov = renderDistance = particles = bobbing = time = brightness = -1;
        clearWeather = hideHud = hideHand = hideCrosshair = hideChat = hideScoreboard = hideNametags = false;
        resourcePack = "";
        hud.clear();
    }

    /** Render server start: remembers every luka HUD module switch (jobs turn some off). */
    static void snapshotHud() {
        hudAtStart.clear();
        try {
            Object manager = Class.forName("dev.cbui.Cbui").getMethod("hud").invoke(null);
            if (manager == null) {
                return;
            }
            for (Object m : (Iterable<?>) manager.getClass().getMethod("modules").invoke(manager)) {
                hudAtStart.put((String) m.getClass().getMethod("name").invoke(m), (Boolean) m.getClass().getMethod("enabled").invoke(m));
            }
        } catch (ClassNotFoundException noLuka) {
            // SubFrame without luka's client mod
        } catch (Throwable t) {
            SubFrameMod.LOG.warn("[look] could not read luka's HUD modules: " + t);
        }
    }

    /** Render server, between jobs: every luka HUD module switched as when the server started. */
    static void restoreHud() {
        if (hudAtStart.isEmpty()) {
            return;
        }
        Map<String, Boolean> saved = new LinkedHashMap<String, Boolean>(hud);
        boolean savedHide = hideHud, savedScoreboard = hideScoreboard, savedCrosshair = hideCrosshair;
        int savedTime = time, savedBrightness = brightness;
        hud.clear();
        hud.putAll(hudAtStart);
        hideHud = hideScoreboard = hideCrosshair = false;
        time = brightness = -1;
        applyHud();
        hud.clear();
        hud.putAll(saved);
        hideHud = savedHide;
        hideScoreboard = savedScoreboard;
        hideCrosshair = savedCrosshair;
        time = savedTime;
        brightness = savedBrightness;
    }

    /**
     * The resource pack for this render (from the game's resourcepacks folder), loaded at the start of a job. A render
     * server keeps the last job's pack loaded while the next job wants the same one, and goes back to the player's own
     * packs for a job that names none.
     */
    @SuppressWarnings("unchecked")
    static void applyResourcePack(Minecraft mc) {
        if (resourcePack.equals(appliedPack)) {
            return;
        }
        try {
            // 1.7.10 names: the entry class is ResourcePackRepository$Entry, setRepositories is m_59621098 (as fastpacks uses them)
            net.minecraft.client.resources.ResourcePackRepository repo = mc.getResourcePackRepository();
            if (originalEntries == null) {
                originalEntries = new java.util.ArrayList<net.minecraft.client.resources.ResourcePackRepository$Entry>(repo.getRepositoryEntries());
                originalNames = new java.util.ArrayList<String>(mc.gameSettings.resourcePacks);
            }
            if (resourcePack.isEmpty()) {
                repo.m_59621098(originalEntries);
                mc.gameSettings.resourcePacks.clear();
                mc.gameSettings.resourcePacks.addAll(originalNames);
                mc.refreshResources();
                appliedPack = "";
                SubFrameMod.LOG.info("[look] back to the player's resource packs");
                return;
            }
            repo.updateRepositoryEntriesAll();
            for (Object o : repo.getRepositoryEntriesAll()) {
                net.minecraft.client.resources.ResourcePackRepository$Entry e = (net.minecraft.client.resources.ResourcePackRepository$Entry) o;
                if (e.getResourcePackName().equals(resourcePack)) {
                    java.util.List<net.minecraft.client.resources.ResourcePackRepository$Entry> one =
                            new java.util.ArrayList<net.minecraft.client.resources.ResourcePackRepository$Entry>();
                    one.add(e);
                    repo.m_59621098(one);
                    mc.gameSettings.resourcePacks.clear();
                    mc.gameSettings.resourcePacks.add(resourcePack);
                    mc.refreshResources();
                    appliedPack = resourcePack;
                    SubFrameMod.LOG.info("[look] resource pack for this render: " + resourcePack);
                    return;
                }
            }
            SubFrameMod.LOG.warn("[look] resource pack not found: " + resourcePack);
        } catch (Throwable t) {
            SubFrameMod.LOG.warn("[look] could not load resource pack " + resourcePack + ": " + t);
        }
    }
}
