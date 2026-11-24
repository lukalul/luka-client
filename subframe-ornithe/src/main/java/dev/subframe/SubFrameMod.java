package dev.subframe;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.NetworkManager;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Properties;

/** Fabric/Ornithe entrypoint and guarded bridge used by the mixin platform hooks. */
public final class SubFrameMod implements ClientModInitializer {
    public static final String MOD_ID = "subframe";
    static final Logger LOG = LogManager.getLogger("SubFrame");

    static int gpuQp = 17;
    static boolean crosshairOverlay = true;
    /** High-fps (over H.264's limit) GPU exports use HEVC; true = lossless 4:4:4 (big files), false = quality-targeted 10-bit. */
    static boolean hevcLossless = true;
    static int clientMenuKey = Keyboard.KEY_RSHIFT;
    static volatile long lastBeat;
    static int outputFps = 60;
    static int subframes = 16;
    static double shutter = 0.5;
    static int warmupFrames = 60;
    static boolean exportVideo = true;
    static boolean blend = true;
    static boolean outputPng;
    static int maxSeconds;
    static String rawEncoderArgs = Encoders.CPU_RAW;
    static boolean useRecordedSettings;
    static boolean asyncReadback = true;
    static boolean disableVignette;
    static boolean autoRecord;
    static boolean showIndicator = true;
    static boolean captureLive = Boolean.getBoolean("subframe.diff");
    static int liveStride = 8;
    static String ffmpegPath = "ffmpeg";
    static String encoderArgs = Encoders.CPU_BLENDED;
    static String encoderMode = "auto";
    /** Linux VAAPI render node for AMD/Intel GPU encoding; empty = the first /dev/dri/renderD* there is. */
    static String vaapiDevice = "";

    static final KeyBinding startKey = new KeyBinding("Start recording", Keyboard.KEY_F6, "SubFrame");
    static final KeyBinding stopKey = new KeyBinding("Stop recording", Keyboard.KEY_F7, "SubFrame");
    static final KeyBinding managerKey = new KeyBinding("Open replay manager", Keyboard.KEY_F8, "SubFrame");

    private static final java.util.Map<String, Integer> PROBLEMS = new java.util.HashMap<String, Integer>();
    private static boolean keysRegistered;
    private static boolean effectRendererWarned;

    @Override
    public void onInitializeClient() {
        try {
            loadTestSettings();
            loadConfig();
            deleteStaleTemps();
            if (SelfTest.enabled()) {
                selfTest = new SelfTest();
            }
            LOG.info("SubFrame Ornithe initialized");
        } catch (Throwable t) {
            problem("initialize", t);
        }
    }

    static void problem(String where, Throwable t) {
        Integer n = PROBLEMS.get(where);
        n = n == null ? 1 : n + 1;
        PROBLEMS.put(where, n);
        if (n <= 3) {
            LOG.error("SubFrame error in " + where + " (occurrence " + n + "), carrying on", t);
        }
        if (n == 1 && where.startsWith("onRender") && Replay.active != null) {
            try {
                Replay.abort();
            } catch (Throwable ignored) {
            }
        }
    }

    public static void onLoginSuccess(NetworkManager manager) {
        try {
            Recorder.start(manager);
        } catch (Throwable t) {
            problem("onConnect", t);
        }
    }

    /**
     * While a replay is shown or rendered: the frame rate the player had when it was recorded, at the moment being shown
     * (luka's FPS counter shows this instead of the replay's own drawing speed). -1 otherwise.
     */
    public static int replayFps() {
        try {
            return Replay.recordedFps();
        } catch (Throwable notReady) {
            return -1;
        }
    }

    public static void onDisconnect() {
        try {
            Recorder.stop();
        } catch (Throwable t) {
            problem("onDisconnect", t);
        }
    }

    public static void onClientTickStart() {
        try {
            lastBeat = System.nanoTime();
            Recorder recorder = Recorder.active;
            if (recorder != null) {
                recorder.onTickStart();
            }
        } catch (Throwable t) {
            problem("onClientTickStart", t);
        }
    }

    public static void onClientTickEnd() {
        try {
            lastBeat = System.nanoTime();
            if (selfTest != null) {
                selfTest.onTick();
            }
            Minecraft mc = Minecraft.getMinecraft();
            registerKeys(mc);
            if (mc.effectRenderer != null && !(mc.effectRenderer instanceof DeterministicEffectRenderer)) {
                if (mc.effectRenderer.getClass() == EffectRenderer.class) {
                    mc.effectRenderer = new DeterministicEffectRenderer(mc.theWorld, mc.getTextureManager());
                } else if (!effectRendererWarned) {
                    effectRendererWarned = true;
                    LOG.warn("Another mod replaced the particle renderer; particle matching is disabled");
                }
            }
            Recorder recorder = Recorder.active;
            if (recorder != null) {
                recorder.onTick();
            }
            while (startKey.isPressed()) {
                Recorder.startKey();
            }
            while (stopKey.isPressed()) {
                Recorder.stopKey();
            }
            // a menu screen is open on the title screen, and the game only feeds key bindings while none is: poll the key
            boolean managerDown = org.lwjgl.input.Keyboard.isKeyDown(managerKey.getKeyCode());
            if (managerDown && !managerWasDown && mc.theWorld == null && mc.currentScreen instanceof GuiMainMenu) {
                ReplayManagerScreen.open();
            }
            managerWasDown = managerDown;
            while (managerKey.isPressed()) {
                if (mc.theWorld == null && (mc.currentScreen instanceof GuiMainMenu || mc.currentScreen == null)) {
                    ReplayManagerScreen.open();
                }
            }
        } catch (Throwable t) {
            problem("onClientTickEnd", t);
        }
    }

    public static void onRenderStart() {
        try {
            if (selfTest != null) {
                selfTest.onRenderStart();
            }
            lastBeat = System.nanoTime();
            Recorder recorder = Recorder.active;
            if (recorder != null) {
                recorder.onRenderStart();
            }
        } catch (Throwable t) {
            problem("onRenderStart", t);
        }
    }

    public static void onRenderEnd(float partialTick) {
        try {
            lastBeat = System.nanoTime();
            Recorder recorder = Recorder.active;
            if (recorder != null) {
                recorder.onFrame(partialTick);
            }
            Replay replay = Replay.active;
            if (replay != null) {
                replay.onRenderEnd();
            }
        } catch (Throwable t) {
            problem("onRenderEnd", t);
        }
    }

    public static void renderIndicator() {
        RecIndicator.render();
    }

    private static long lastCrosshairProbe;

    /** Replay diagnostics: once a second, whether the game asked to draw the crosshair and what it answered. */
    public static void probeCrosshair(boolean visible) {
        try {
            if (Replay.active == null) return;
            long now = System.currentTimeMillis();
            if (now - lastCrosshairProbe < 1000) return;
            lastCrosshairProbe = now;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            LOG.info("[crosshair-probe] showCrosshair=" + visible + " screen=" + (mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getName())
                    + " thirdPerson=" + mc.gameSettings.thirdPersonView + " hideGUI=" + mc.gameSettings.hideGUI
                    + " spectator=" + (mc.playerController != null && mc.playerController.isSpectator()));
        } catch (Throwable ignored) {
        }
    }

    public static boolean shouldCancelCrosshair() {
        return CrosshairOverlay.shouldCancelVanilla();
    }

    public static void onEntityJoined(Entity entity, World world) {
        Determinism.onEntityJoin(entity, world);
    }

    public static void onWorldLoaded(World world) {
        Determinism.onWorldLoad(world);
    }

    private static void registerKeys(Minecraft mc) {
        if (keysRegistered || mc.gameSettings == null) {
            return;
        }
        // options.txt was read before these keys existed: take their saved codes from it, or they come back as the defaults every start
        for (KeyBinding key : new KeyBinding[] {startKey, stopKey, managerKey}) {
            try {
                File options = new File(mc.mcDataDir, "options.txt");
                if (!options.isFile()) break;
                String prefix = "key_" + key.getKeyDescription() + ":";
                for (String line : java.nio.file.Files.readAllLines(options.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                    if (line.startsWith(prefix)) {
                        key.setKeyCode(Integer.parseInt(line.substring(prefix.length()).trim()));
                        break;
                    }
                }
            } catch (Exception ignored) {
                // keep the default
            }
        }
        KeyBinding[] old = mc.gameSettings.keyBindings;
        KeyBinding[] all = Arrays.copyOf(old, old.length + 3);
        all[old.length] = startKey;
        all[old.length + 1] = stopKey;
        all[old.length + 2] = managerKey;
        mc.gameSettings.keyBindings = all;
        KeyBinding.resetKeyBindingArrayAndHash();
        keysRegistered = true;
    }

    private static void deleteStaleTemps() {
        File[] files = new File(Minecraft.getMinecraft().mcDataDir, "subframe").listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().startsWith(".") && file.getName().endsWith(".tmp") && !file.delete()) {
                    file.deleteOnExit();
                }
            }
        }
    }

    static SelfTest selfTest;
    private static boolean managerWasDown;

    /** Developer hook: <gameDir>/subframe/selftest.properties becomes system properties for the automated checks. */
    private static void loadTestSettings() {
        File f = new File(FabricLoader.getInstance().getGameDir().toFile(), "subframe/selftest.properties");
        if (!f.isFile()) {
            return;
        }
        try (FileInputStream in = new FileInputStream(f)) {
            Properties p = new Properties();
            p.load(in);
            for (String k : p.stringPropertyNames()) {
                System.setProperty(k, p.getProperty(k));
            }
            LOG.info("Loaded " + p.size() + " test settings from " + f.getName());
        } catch (Exception e) {
            LOG.warn("Could not read " + f, e);
        }
    }

    private static void loadConfig() {
        File file = FabricLoader.getInstance().getConfigDir().resolve("subframe.properties").toFile();
        Properties p = new Properties();
        if (file.isFile()) {
            try (FileInputStream in = new FileInputStream(file)) {
                p.load(in);
            } catch (Exception e) {
                LOG.warn("Could not read " + file, e);
            }
        }
        outputFps = integer(p, "outputFps", outputFps, 1, 20000);
        subframes = integer(p, "subframes", subframes, 1, 4096);
        shutter = decimal(p, "shutter", shutter);
        warmupFrames = integer(p, "warmupFrames", warmupFrames, 0, 1000);
        maxSeconds = integer(p, "maxSeconds", maxSeconds, 0, 100000);
        gpuQp = integer(p, "gpuQuality", gpuQp, 1, 51);
        liveStride = integer(p, "liveStride", liveStride, 1, 1000);
        exportVideo = bool(p, "exportVideo", exportVideo);
        blend = bool(p, "blend", blend);
        outputPng = bool(p, "outputPng", outputPng);
        useRecordedSettings = bool(p, "useRecordedSettings", useRecordedSettings);
        asyncReadback = bool(p, "asyncReadback", asyncReadback);
        autoRecord = bool(p, "autoRecord", autoRecord);
        crosshairOverlay = bool(p, "crosshairOverlay", crosshairOverlay);
        hevcLossless = bool(p, "hevcLossless", hevcLossless);
        showIndicator = bool(p, "showIndicator", showIndicator);
        disableVignette = bool(p, "disableVignette", disableVignette);
        captureLive = bool(p, "captureLive", captureLive) || Boolean.getBoolean("subframe.diff");
        ffmpegPath = p.getProperty("ffmpegPath", ffmpegPath);
        encoderArgs = System.getProperty("subframe.encoderArgs", p.getProperty("encoderArgs", encoderArgs)).replace('~', ' ');
        rawEncoderArgs = System.getProperty("subframe.rawEncoderArgs", p.getProperty("rawEncoderArgs", rawEncoderArgs)).replace('~', ' ');
        encoderMode = p.getProperty("encoder", encoderMode);
        vaapiDevice = p.getProperty("vaapiDevice", vaapiDevice);

        p.setProperty("outputFps", Integer.toString(outputFps));
        p.setProperty("subframes", Integer.toString(subframes));
        p.setProperty("shutter", Double.toString(shutter));
        p.setProperty("warmupFrames", Integer.toString(warmupFrames));
        p.setProperty("maxSeconds", Integer.toString(maxSeconds));
        p.setProperty("gpuQuality", Integer.toString(gpuQp));
        p.setProperty("liveStride", Integer.toString(liveStride));
        p.setProperty("exportVideo", Boolean.toString(exportVideo));
        p.setProperty("blend", Boolean.toString(blend));
        p.setProperty("outputPng", Boolean.toString(outputPng));
        p.setProperty("useRecordedSettings", Boolean.toString(useRecordedSettings));
        p.setProperty("asyncReadback", Boolean.toString(asyncReadback));
        p.setProperty("autoRecord", Boolean.toString(autoRecord));
        p.setProperty("crosshairOverlay", Boolean.toString(crosshairOverlay));
        p.setProperty("hevcLossless", Boolean.toString(hevcLossless));
        p.setProperty("showIndicator", Boolean.toString(showIndicator));
        p.setProperty("disableVignette", Boolean.toString(disableVignette));
        p.setProperty("captureLive", Boolean.toString(captureLive));
        p.setProperty("ffmpegPath", ffmpegPath);
        p.setProperty("encoder", encoderMode);
        p.setProperty("vaapiDevice", vaapiDevice);
        p.setProperty("encoderArgs", encoderArgs);
        p.setProperty("rawEncoderArgs", rawEncoderArgs);
        file.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            p.store(out, "SubFrame Ornithe settings");
        } catch (Exception e) {
            LOG.warn("Could not write " + file, e);
        }
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        return Boolean.parseBoolean(p.getProperty(key, Boolean.toString(fallback)));
    }

    private static int integer(Properties p, String key, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(p.getProperty(key, Integer.toString(fallback)))));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double decimal(Properties p, String key, double fallback) {
        try {
            return Double.parseDouble(p.getProperty(key, Double.toString(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
