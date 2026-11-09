package dev.subframe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import java.io.File;

/**
 * Developer aid, enabled with -Dsubframe.selftest=true: opens a flat creative world, walks and turns
 * for a few seconds while recording, leaves, replays the recording to an mp4 and quits.
 */
final class SelfTest {
    private enum Stage { MENU, JOINING, RECORDING, LEAVING, REPLAYING, DONE, VIEWING }

    private static final int RECORD_TICKS = 200;
    private static final boolean VIEWER = Boolean.getBoolean("subframe.viewer");
    private static final boolean PVP = Boolean.getBoolean("subframe.pvp");
    private static final String VIEWREC = System.getProperty("subframe.viewrec");
    private static final String RENDERREC = System.getProperty("subframe.renderrec");
    private static final boolean VIEWMENU = Boolean.getBoolean("subframe.viewmenu");
    private static final boolean NOSTART = Boolean.getBoolean("subframe.nostart");
    /** Developer check: in the test world take an in-game screenshot (with the client's own crosshair), then quit. */
    private static final boolean SHOT = Boolean.getBoolean("subframe.shot");
    private static final boolean TWO_TAKES = Boolean.getBoolean("subframe.twotakes");
    private final PvpBot bot = new PvpBot();

    private final Minecraft mc = Minecraft.getMinecraft();
    private Stage stage = Stage.MENU;
    private int timer;
    private boolean replayStarted;

    static boolean enabled() {
        return Boolean.getBoolean("subframe.selftest");
    }

    /** Called once per client tick (end of Minecraft#runTick) from SubFrameMod. */
    public void onTick() {
        timer++;
        mc.gameSettings.pauseOnLostFocus = false;
        if (Boolean.getBoolean("subframe.noparticles")) {
            mc.gameSettings.particleSetting = 2;
        }
        if (stage == Stage.MENU && timer % 200 == 0) {
            SubFrameMod.LOG.info("[selftest] waiting for the main menu, screen=" + (mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getName()) + ", ticks=" + timer);
        }
        switch (stage) {
            case MENU:
                if (RENDERREC != null && menuReady()) {
                    SubFrameMod.outputFps = Integer.getInteger("subframe.render.fps", 480);
                    SubFrameMod.blend = Boolean.parseBoolean(System.getProperty("subframe.render.blend", "false"));
                    SubFrameMod.maxSeconds = Integer.getInteger("subframe.render.seconds", 20);
                    SubFrameMod.LOG.info("[render] rendering " + RENDERREC);
                    Replay.start(new File(RENDERREC));
                    replayStarted = Replay.active != null;
                    stage = replayStarted ? Stage.REPLAYING : Stage.DONE;
                    timer = 0;
                    break;
                }
                if (VIEWREC != null && menuReady()) {
                    SubFrameMod.LOG.info("[view] starting the viewer on " + VIEWREC);
                    Replay.startPreview(new File(VIEWREC));
                    stage = Stage.VIEWING;
                    timer = 0;
                    break;
                }
                if (VIEWREC == null && menuReady()) {
                    SubFrameMod.LOG.info("[selftest] opening world");
                    String worldName = System.getProperty("subframe.worldname", "sftest");
                    mc.launchIntegratedServer(worldName, worldName,
                            new WorldSettings(1234L, PVP ? WorldSettings.GameType.SURVIVAL : WorldSettings.GameType.CREATIVE, false, false,
                                    "default".equals(System.getProperty("subframe.worldtype")) ? WorldType.DEFAULT : WorldType.FLAT));
                    stage = Stage.JOINING;
                    timer = 0;
                }
                break;
            case JOINING:
                if (mc.thePlayer != null && mc.theWorld != null && timer > 80
                        && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiDownloadTerrain)) {
                    if (SHOT) {
                        mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = 15f;
                        if (timer > 160) {
                            try {
                                net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
                                SubFrameMod.LOG.info("[selftest] screenshot taken in the test world");
                            } catch (Throwable t) {
                                SubFrameMod.LOG.error("[selftest] screenshot failed", t);
                            }
                            stage = Stage.DONE;
                            mc.shutdown();
                        }
                        break;
                    }
                    SubFrameMod.LOG.info("[selftest] in world, recording");
                    mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = 15f; // other clients may start the player looking straight up
                    if (PVP) {
                        bot.setup();
                    } else {
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
                    }
                    if (!NOSTART) {
                        Recorder.startKey();
                    }
                    stage = Stage.RECORDING;
                    timer = 0;
                }
                break;
            case RECORDING:
                if (TWO_TAKES && timer == 100) {
                    Recorder.stopKey();
                }
                if (TWO_TAKES && timer == 140) {
                    Recorder.startKey();
                }
                if (PVP) {
                    bot.onTick();
                } else {
                    if (timer == 60 || timer == 120) {
                        mc.thePlayer.swingItem();
                    }
                    if (timer == 90) {
                        mc.thePlayer.jump();
                    }
                }
                if (timer >= (PVP ? 360 : RECORD_TICKS)) {
                    bot.stop();
                    if (!NOSTART) {
                        Recorder.stopKey();
                    }
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
                    SubFrameMod.LOG.info("[selftest] leaving world");
                    mc.theWorld.sendQuittingDisconnectingPacket();
                    mc.loadWorld(null);
                    mc.displayGuiScreen(new GuiMainMenu());
                    stage = Stage.LEAVING;
                    timer = 0;
                }
                break;
            case LEAVING:
                if (timer > 60) {
                    File dir = new File(mc.mcDataDir, "subframe");
                    File newest = null;
                    File[] files = dir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            if (f.getName().endsWith(".sfr") && (newest == null || f.getName().compareTo(newest.getName()) > 0)) {
                                newest = f;
                            }
                        }
                    }
                    if (newest == null) {
                        SubFrameMod.LOG.error("[selftest] no recording found");
                        mc.shutdown();
                        return;
                    }
                    if (Integer.getInteger("subframe.replay.particles") != null) {
                        // a settings change made after recording: the replay must follow it
                        mc.gameSettings.particleSetting = Integer.getInteger("subframe.replay.particles");
                        SubFrameMod.LOG.info("[selftest] particle setting changed to " + mc.gameSettings.particleSetting + " after recording");
                    }
                    SubFrameMod.LOG.info("[selftest] replaying " + newest);
                    if (VIEWER) {
                        Replay.startPreview(newest);
                    } else if (SubFrameMod.captureLive) {
                        Replay.startDiff(newest);
                    } else {
                        Replay.start(newest);
                    }
                    replayStarted = Replay.active != null;
                    stage = replayStarted ? Stage.REPLAYING : Stage.DONE;
                    timer = 0;
                }
                break;
            case REPLAYING:
                if (VIEWER && Replay.active != null && Replay.active.isPreview() && timer == 200) {
                    // what a user does in the viewer: change options, pick 240 fps, press render
                    SubFrameMod.LOG.info("[selftest] viewer: changing fov and render distance, rendering at 240 fps");
                    mc.gameSettings.fovSetting = 90f;
                    mc.gameSettings.renderDistanceChunks = 6;
                    mc.renderGlobal.loadRenderers();
                    SubFrameMod.outputFps = Integer.getInteger("subframe.viewer.fps", 240);
                    SubFrameMod.subframes = 4;
                    SubFrameMod.blend = Boolean.parseBoolean(System.getProperty("subframe.viewer.blend", "true"));
                    SubFrameMod.outputPng = Boolean.getBoolean("subframe.viewer.png");
                    SubFrameMod.maxSeconds = Integer.getInteger("subframe.viewer.seconds", 0);
                    Replay.active.startRender();
                }
                if (Replay.active == null && timer > 20) {
                    stage = Stage.DONE;
                }
                break;
            case VIEWING:
                break;
            case DONE:
                SubFrameMod.LOG.info("[selftest] done, replay started=" + replayStarted);
                if (timer > 40) {
                    mc.shutdown();
                }
                break;
            default:
                break;
        }
    }

    /** Turns the camera every frame while recording, so the frame-rate camera path has real content. */
    private long vmStart, vmNext;
    private int vmStep;
    private final java.util.ArrayList<String> vmQueue = new java.util.ArrayList<String>();
    private String vmCurrent;
    private boolean modConfigDone;
    private int cfgIndex;
    private final java.util.ArrayList<Integer> cfgQueue = new java.util.ArrayList<Integer>();

    

    

    /** The vanilla main menu, or after half a minute any screen (other clients draw their own menu). */
    private boolean menuReady() {
        return mc.currentScreen instanceof GuiMainMenu && timer > 40 || mc.currentScreen != null && mc.theWorld == null && timer > 600;
    }

    private String screenName() {
        return mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getName();
    }



    private long viewStart;
    private boolean pressedEsc, loggedAfter, finishedView;

    /** Viewer check on wall-clock time, since no client ticks run while the viewer is paused or a menu is open. */
    private void viewerCheck() {
        if (VIEWREC == null || stage != Stage.VIEWING || finishedView) {
            return;
        }
        if (VIEWMENU) {
            SubFrameMod.LOG.info("[selftest] the viewer menu check is not available in the Ornithe port");
            return;
        }
        if (viewStart == 0) {
            viewStart = System.nanoTime();
        }
        double sec = (System.nanoTime() - viewStart) / 1e9;
        Replay r = Replay.active;
        if (sec > 6 && !pressedEsc && mc.currentScreen != null) {
            pressedEsc = true;
            SubFrameMod.LOG.info("[view] before Esc: screen=" + mc.currentScreen.getClass().getName() + " pumps=" + (r == null ? -1 : r.inputPumps));
            mc.displayGuiScreen(new ReplayMenuScreen()); // what Esc does in the viewer
        }
        if (sec > 9 && pressedEsc && !loggedAfter) {
            loggedAfter = true;
            SubFrameMod.LOG.info("[view] 3 s after Esc: screen=" + (mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getName())
                    + " pumps=" + (r == null ? -1 : r.inputPumps) + " (grows every frame while the menu is open)");
        }
        if (sec > 11) {
            finishedView = true;
            Replay.abort();
            mc.shutdown();
        }
    }

    /** Called at the start of every rendered frame (the Forge version's render-tick START phase). */
    public void onRenderStart() {
        viewerCheck();
        if (stage == Stage.RECORDING && mc.thePlayer != null && mc.currentScreen == null) {
            if (PVP) {
                bot.aim();
            } else {
                mc.thePlayer.setAngles(6f, 0.4f);
            }
        }
    }
}
