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
    /** -Dsubframe.menushots=true: the viewer menu at each GUI scale, a screenshot each, layout lines, then quit. */
    private static final boolean MENUSHOTS = Boolean.getBoolean("subframe.menushots");
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
                    if (SubFrameMod.captureLive) {
                        // -Dsubframe.diff=true too: compare this recording's saved live frames with the replay (A/B of a change)
                        Replay.startDiff(new File(RENDERREC));
                    } else {
                        Replay.start(new File(RENDERREC));
                    }
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
    private boolean viewRendered;

    /** Viewer check on wall-clock time, since no client ticks run while the viewer is paused or a menu is open. */
    private void viewerCheck() {
        // -Dsubframe.viewrender=<s>: after that many seconds in the viewer, press Render there (the render rejoins the world,
        // unlike a render started from the main menu); the game quits once the render is over
        if (VIEWREC != null && stage == Stage.VIEWING && Integer.getInteger("subframe.viewrender") != null) {
            if (viewStart == 0) {
                viewStart = System.nanoTime();
            }
            Replay r = Replay.active;
            if (!viewRendered && r != null && (System.nanoTime() - viewStart) / 1e9 > Integer.getInteger("subframe.viewrender")) {
                viewRendered = true;
                SubFrameMod.LOG.info("[selftest] pressing Render in the viewer");
                r.startRender();
            } else if (viewRendered && r == null) {
                mc.shutdown();
            }
            return;
        }
        // -Dsubframe.viewstay: leave the viewer running for the test bot (screenshots of a player's replay)
        if (VIEWREC == null || stage != Stage.VIEWING || finishedView || Boolean.getBoolean("subframe.viewstay")) {
            return;
        }
        if (viewStart == 0) {
            viewStart = System.nanoTime();
        }
        double sec = (System.nanoTime() - viewStart) / 1e9;
        if (MENUSHOTS) {
            menuShots(sec);
            return;
        }
        if (VIEWMENU) {
            fpsBoxCheck(sec);
            return;
        }
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

    private boolean boxOpened;

    /** Viewer menu check (-Dsubframe.viewmenu): types into the custom video fps box the way a player would and logs each result. */
    private void fpsBoxCheck(double sec) {
        if (sec < 6) {
            return;
        }
        if (!boxOpened) {
            boxOpened = true;
            mc.displayGuiScreen(new ReplayMenuScreen());
            return;
        }
        finishedView = true;
        if (!(mc.currentScreen instanceof ReplayMenuScreen)) {
            SubFrameMod.LOG.info("[fpsbox] FAIL: the viewer menu did not open (" + mc.currentScreen + ")");
        } else {
            ReplayMenuScreen s = (ReplayMenuScreen) mc.currentScreen;
            int fx = s.fpsBoxPoint()[0], fy = s.fpsBoxPoint()[1];
            SubFrameMod.LOG.info("[fpsbox] start: fps=" + SubFrameMod.outputFps + " box='" + s.fpsText() + "'");
            s.mouseClicked(fx, fy, 0);
            boxType(s, "####144!");
            SubFrameMod.LOG.info("[fpsbox] typed 144 + Enter: fps=" + SubFrameMod.outputFps + " box='" + s.fpsText() + "'");
            s.mouseClicked(fx, fy, 0);
            boxType(s, "###25000ab");
            SubFrameMod.LOG.info("[fpsbox] while typing 25000ab: fps=" + SubFrameMod.outputFps + " box='" + s.fpsText() + "'");
            s.mouseClicked(5, 5, 0);
            SubFrameMod.LOG.info("[fpsbox] clicked away: fps=" + SubFrameMod.outputFps + " box='" + s.fpsText() + "'");
            s.mouseClicked(fx, fy, 0);
            boxType(s, "###75!");
            SubFrameMod.LOG.info("[fpsbox] typed 75 + Enter: fps=" + SubFrameMod.outputFps + " box='" + s.fpsText() + "'");
            s.mouseClicked(fx, fy, 0);
            boxType(s, "###333");
            s.mouseClicked(s.fpsPresetPoint()[0], s.fpsPresetPoint()[1], 0);
            SubFrameMod.LOG.info("[fpsbox] typed 333 then clicked the preset button: fps=" + SubFrameMod.outputFps + " box='" + s.fpsText() + "'");
            s.mouseClicked(fx, fy, 0);
            boxType(s, "#####5000!");
            SubFrameMod.LOG.info("[fpsbox] typed 5000 + Enter: fps=" + SubFrameMod.outputFps);
            Replay rp = Replay.active;
            String[] big = rp == null ? null : ReplayMenuScreen.bigRender(rp, 1920, 1080);
            SubFrameMod.LOG.info("[fpsbox] large-render check at 1920x1080: " + (big == null ? "none" : String.join(" | ", big)));
            s.mouseClicked(fx, fy, 0);
            boxType(s, "#####1000000!");
            SubFrameMod.LOG.info("[fpsbox] typed 1000000 + Enter: fps=" + SubFrameMod.outputFps + " box='" + s.fpsText() + "'");
            String[] huge = rp == null ? null : ReplayMenuScreen.bigRender(rp, 1920, 1080);
            SubFrameMod.LOG.info("[fpsbox] large-render check at 1,000,000 fps: " + (huge == null ? "none" : String.join(" | ", huge)));
        }
        Replay.abort();
        mc.shutdown();
    }

    /** GUI scales the menu pictures are taken at (0 = Auto). */
    private static final int[] SHOT_SCALES = {0, 1, 2, 3, 4};
    private int shotStep = -1, shotFrames, savedGuiScale;

    /** Menu pictures (-Dsubframe.menushots): one per GUI scale, saved as screenshots/subframe-menu-scale<N>.png. */
    private void menuShots(double sec) {
        if (sec < 6) {
            return;
        }
        if (shotStep < 0) {
            savedGuiScale = mc.gameSettings.guiScale;
            shotStep = 0;
            openMenuAt(SHOT_SCALES[0]);
            return;
        }
        if (++shotFrames < 4) {
            return;
        }
        int scale = SHOT_SCALES[shotStep];
        if (mc.currentScreen instanceof ReplayMenuScreen) {
            ReplayMenuScreen s = (ReplayMenuScreen) mc.currentScreen;
            SubFrameMod.LOG.info("[menushot] scale " + (scale == 0 ? "auto" : scale) + ": " + s.layoutReport());
            try {
                net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir, "subframe-menu-scale" + scale + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            } catch (Throwable t) {
                SubFrameMod.LOG.error("[menushot] screenshot failed", t);
            }
            if (scale == 0) {
                // the new buttons respond: two clicks on Intensity, three on Weighting, then the hints over them
                double i0 = SubFrameMod.shutter;
                String w0 = SubFrameMod.weighting;
                s.mouseClicked(s.intensityPoint()[0], s.intensityPoint()[1], 0);
                String i1 = ReplayMenuScreen.intensityText(SubFrameMod.shutter);
                s.mouseClicked(s.intensityPoint()[0], s.intensityPoint()[1], 0);
                String i2 = ReplayMenuScreen.intensityText(SubFrameMod.shutter);
                StringBuilder w = new StringBuilder();
                for (int k = 0; k < 3; k++) {
                    s.mouseClicked(s.weightPoint()[0], s.weightPoint()[1], 0);
                    w.append(k == 0 ? "" : " -> ").append(SubFrameMod.weighting);
                }
                SubFrameMod.LOG.info("[menushot] intensity " + ReplayMenuScreen.intensityText(i0) + " -> " + i1 + " -> " + i2 + "; weighting " + w0 + " -> " + w);
                SubFrameMod.LOG.info("[menushot] hint over Intensity: " + s.hintAt(s.intensityPoint()[0], s.intensityPoint()[1]));
                SubFrameMod.LOG.info("[menushot] hint over Weighting: " + s.hintAt(s.weightPoint()[0], s.weightPoint()[1]));
                SubFrameMod.shutter = i0;
                SubFrameMod.weighting = w0;
            }
        } else {
            SubFrameMod.LOG.info("[menushot] FAIL: the viewer menu is not open at scale " + scale + " (" + mc.currentScreen + ")");
        }
        shotStep++;
        if (shotStep < SHOT_SCALES.length) {
            openMenuAt(SHOT_SCALES[shotStep]);
            return;
        }
        mc.gameSettings.guiScale = savedGuiScale;
        finishedView = true;
        Replay.abort();
        mc.shutdown();
    }

    private void openMenuAt(int scale) {
        mc.gameSettings.guiScale = scale;
        shotFrames = 0;
        mc.displayGuiScreen(new ReplayMenuScreen());
    }

    /** \b is Backspace, \n is Enter, anything else is typed as a character. */
    private static void boxType(ReplayMenuScreen s, String keys) {
        for (char c : keys.toCharArray()) {
            if (c == '#') {
                s.keyTyped((char) 8, org.lwjgl.input.Keyboard.KEY_BACK);
            } else if (c == '!') {
                s.keyTyped((char) 13, org.lwjgl.input.Keyboard.KEY_RETURN);
            } else {
                s.keyTyped(c, 0);
            }
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
