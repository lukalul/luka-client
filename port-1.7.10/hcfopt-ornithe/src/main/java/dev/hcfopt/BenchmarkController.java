package dev.hcfopt;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** Automated singleplayer benchmark. Presence of hcfopt/bench.properties is the only enable switch. */
final class BenchmarkController {
    private enum Stage { MENU, JOINING, PREPARE, BUILDING, SETTLING, TRANSITION, MEASURING, WRITING, DONE }
    private static BenchmarkController INSTANCE;
    private static final String[] TOGGLES = {"entityTicks", "tileTicks", "tileRenderDistance", "itemRenderCap", "particleCaps", "cloudMeshCache"};

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Properties properties;
    private final List<String> commands;
    private final List<String> windows = new ArrayList<String>();
    private final BenchmarkStats stats = new BenchmarkStats();
    private Stage stage = Stage.MENU;
    private int ticks;
    private int commandIndex;
    private int windowIndex;
    private BenchmarkStats.Window current;
    private long latestTickNanos;
    private final int warmupTicks;
    private final int settleTicks;
    private final int measureTicks;
    private final int commandsPerTick;
    private final boolean originalMaster;
    private final boolean[] originalToggles = new boolean[TOGGLES.length];

    private BenchmarkController(Properties p) {
        properties = p;
        warmupTicks = integer("warmupSeconds", 8, 1, 600) * 20;
        settleTicks = integer("settleSeconds", 8, 1, 600) * 20;
        measureTicks = integer("measureSeconds", 20, 2, 3600) * 20;
        commandsPerTick = integer("commandsPerTick", 8, 1, 100);
        commands = ScenePlanner.plan(integer("hoppers", 3000, 0, 100000), integer("items", 300, 0, 10000),
                integer("mobs", 100, 0, 10000), integer("fallingBlocks", 200, 0, 10000), integer("primedTnt", 40, 0, 10000),
                integer("chests", 300, 0, 10000), integer("furnaces", 200, 0, 10000), integer("signs", 100, 0, 10000));
        windows.add("off");
        if (bool("ablation", false)) for (String toggle : TOGGLES) windows.add("only-" + toggle);
        windows.add("all-on");
        originalMaster = HcfOptApi.isMasterEnabled();
        for (int i = 0; i < TOGGLES.length; i++) originalToggles[i] = HcfOptApi.isEnabled(TOGGLES[i]);
    }

    static void loadIfPresent() {
        File dir = new File(FabricLoader.getInstance().getGameDir().toFile(), "hcfopt");
        File file = new File(dir, "bench.properties");
        if (!file.isFile()) return;
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            p.load(in);
            INSTANCE = new BenchmarkController(p);
            HcfOptMod.LOG.info("HCFOpt benchmark enabled by {}", file);
        } catch (Throwable t) {
            HcfOptMod.problemOnce("benchmark-load", t);
        }
    }

    static void onTickStart() { if (INSTANCE != null) INSTANCE.tick(); }
    static void onTickEnd(long nanos) {
        if (INSTANCE != null) {
            INSTANCE.latestTickNanos = nanos;
            if (INSTANCE.stage == Stage.MEASURING && INSTANCE.current != null) INSTANCE.current.tick(nanos);
        }
    }
    static void onFrameStart() { }
    static void onFrameEnd(long nanos, int entities, int tiles) {
        if (INSTANCE != null && INSTANCE.stage == Stage.MEASURING && INSTANCE.current != null)
            INSTANCE.current.frame(nanos, INSTANCE.latestTickNanos, entities, tiles);
    }

    private void tick() {
        ticks++;
        mc.gameSettings.pauseOnLostFocus = false;
        switch (stage) {
            case MENU:
                if ((mc.currentScreen instanceof GuiMainMenu && ticks > 40) || (mc.currentScreen != null && mc.theWorld == null && ticks > 600)) {
                    String name = "hcfopt-bench-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
                    WorldSettings settings = new WorldSettings(8675309L, net.minecraft.world.WorldSettings$GameType.CREATIVE, false, false, WorldType.FLAT);
                    settings.enableCommands(); // the scene is built with /setblock and /summon, which need cheats (the player is then an operator, exempt from the chat spam kick)
                    mc.launchIntegratedServer(name, name, settings);
                    advance(Stage.JOINING);
                }
                break;
            case JOINING:
                if (mc.thePlayer != null && mc.theWorld != null && !(mc.currentScreen instanceof GuiDownloadTerrain) && ticks > 100) {
                    // the world spawn can be hundreds of blocks from the scene: go to the scene first so its chunks are loaded before anything is placed
                    send("/tp @p 0 30 0");
                    advance(Stage.PREPARE);
                }
                break;
            case PREPARE:
                if (ticks >= 120) {
                    HcfOptMod.LOG.info("[benchmark] building scene with {} commands", commands.size());
                    advance(Stage.BUILDING);
                }
                break;
            case BUILDING:
                for (int i = 0; i < commandsPerTick && commandIndex < commands.size(); i++) send(commands.get(commandIndex++));
                if (commandIndex >= commands.size()) advance(Stage.SETTLING);
                break;
            case SETTLING:
                if (ticks >= settleTicks) { windowIndex = 0; beginTransition(); }
                break;
            case TRANSITION:
                moveCamera();
                if (ticks >= warmupTicks) beginMeasurement();
                break;
            case MEASURING:
                moveCamera();
                if (ticks >= measureTicks) {
                    HcfOptMod.LOG.info("[benchmark] {}", current.summary());
                    windowIndex++;
                    if (windowIndex >= windows.size()) advance(Stage.WRITING); else beginTransition();
                }
                break;
            case WRITING:
                writeAndRestore();
                advance(Stage.DONE);
                break;
            case DONE:
                if (ticks > 40) mc.shutdown();
                break;
            default: break;
        }
    }

    private void beginTransition() {
        applyWindow(windows.get(windowIndex));
        send(ScenePlanner.cameraCommand(0));
        aim(0);
        HcfOptMod.LOG.info("[benchmark] warming window {}", windows.get(windowIndex));
        advance(Stage.TRANSITION);
    }

    private void beginMeasurement() {
        current = stats.addWindow(windows.get(windowIndex));
        send(ScenePlanner.cameraCommand(0));
        aim(0);
        HcfOptMod.LOG.info("[benchmark] measuring window {}", current.name);
        advance(Stage.MEASURING);
    }

    private void applyWindow(String name) {
        HcfOptApi.setMasterEnabled(true);
        HcfOptApi.setAllOptimizations(false);
        if ("all-on".equals(name)) HcfOptApi.setAllOptimizations(true);
        else if (name.startsWith("only-")) HcfOptApi.setEnabled(name.substring(5), true);
        else HcfOptApi.setMasterEnabled(false);
    }

    private void moveCamera() {
        aim(ticks);
        if (ticks % 20 == 1) send(ScenePlanner.cameraCommand(ticks));
    }

    /** Applied every tick because each /tp answer resets the client to the server's last known rotation. */
    private void aim(int tick) {
        if (mc.thePlayer == null) return;
        float[] angles = ScenePlanner.cameraAngles(tick);
        mc.thePlayer.rotationYaw = angles[0];
        mc.thePlayer.rotationPitch = angles[1];
    }

    private void writeAndRestore() {
        restore();
        try {
            File dir = new File(FabricLoader.getInstance().getGameDir().toFile(), "hcfopt");
            dir.mkdirs();
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
            File csv = new File(dir, "bench-" + stamp + ".csv");
            File summary = new File(dir, "bench-" + stamp + ".txt");
            stats.write(csv, summary);
            StringBuilder oneLine = new StringBuilder("[benchmark] COMPLETE ");
            for (BenchmarkStats.Window window : stats.windows()) oneLine.append(window.summary()).append(" | ");
            HcfOptMod.LOG.info(oneLine.toString());
        } catch (Throwable t) {
            HcfOptMod.problemOnce("benchmark-write", t);
        }
    }

    private void restore() {
        HcfOptApi.setMasterEnabled(originalMaster);
        for (int i = 0; i < TOGGLES.length; i++) HcfOptApi.setEnabled(TOGGLES[i], originalToggles[i]);
    }

    private void send(String command) {
        if (mc.thePlayer != null) mc.thePlayer.sendChatMessage(command);
    }

    private void advance(Stage next) { stage = next; ticks = 0; }
    private boolean bool(String key, boolean fallback) { return Boolean.parseBoolean(properties.getProperty(key, Boolean.toString(fallback))); }
    private int integer(String key, int fallback, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(properties.getProperty(key, Integer.toString(fallback))))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
