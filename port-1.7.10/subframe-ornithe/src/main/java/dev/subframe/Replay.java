package dev.subframe;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.Session;
import net.minecraft.util.Timer;
import dev.subframe.mixin.MinecraftAccessor;
import org.lwjgl.opengl.Display;

import java.io.File;
import java.util.Locale;
import java.util.UUID;

/**
 * Plays a recording back on a virtual clock and renders it offline. Each output frame is the average
 * (in linear light) of N subframes spread across the shutter window; the game's real-time loop is
 * frozen, so ticks, packets and the camera are all driven from here.
 */
final class Replay {
    static volatile Replay active;
    /** Set when a replay was started from the manager screen, so closing it goes back there. */
    static boolean returnToManager;

    /** Ticks before the first recorded frame that are replayed one by one. */
    private static final int PREROLL_TICKS = 100;

    private static final String[] TIMER_FIELD = {"timer", "field_71428_T"};

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Recording rec;
    private final File outFile;
    private int outFps, subframes;
    private double shutter;
    private double frameDur; // nanoseconds of recorded time per output frame

    // viewer: plays in real time with one subframe per displayed frame, so settings can be changed while watching
    private boolean preview;
    private boolean paused;
    private double tCur;
    private long lastReal;

    private EmbeddedChannel channel;
    private NetworkManager net;
    private NetHandlerPlayClient handler;
    private Timer savedTimer;
    private ReplayScreen screen;
    private ChunkWait chunks;
    /** The real login, kept while the session names the recorded player. */
    private Session savedSession;

    // render log (render mode only)
    private FileLog rlog;
    private int fTicks, fSettle, fPackets, identicalRun, tickFailures;
    private long prevHash = Long.MIN_VALUE, nsTicks, nsRender, nsWrite, lastProgressNs;
    private long lastProgressFrame;
    private long sumFrameNs, sumRenderNs, sumWriteNs, sumTicksNs, sumWaitNs;
    private int outW, outH;
    private boolean windowWarned;
    private int entryFbo;
    private int pendingErrors;
    private String encoderUsed = "(none)";
    private boolean waitingForWindow;
    private int windowStableFrames;
    private long windowHiddenSince;
    private Accumulator acc;
    private FrameSink sink;

    private boolean overrodeSettings;
    private float savedFov, savedBob;
    private int savedRenderDistance, savedFramerate;
    /** The window title before the replay ("luka"), put back when it ends. */
    private String savedTitle;
    /** The render has its own size (not the window's): the game's size is set back to the window's when it ends. */
    private boolean customSize;
    private int savedGuiScale;
    private boolean savedHideGui, savedVsync;

    private int packetIdx;
    private int ticksRun;
    private int limbTick = -1;
    private float limbPhase, limbAmount, limbPrevAmount, bodyYaw, prevBodyYaw;
    private volatile long frameIndex;
    private Boolean savedShowGlErrors;
    /** Private off-screen target, used when the game itself draws into the window (no off-screen game framebuffer). */
    private boolean useRt;
    private int rtFbo, rtTex, rtDepth;
    private boolean hiddenLogged;
    /** The game redraws into the window whatever target is bound on some clients, so a minimised window cannot be rendered. */
    private boolean windowDependent;
    private int drawChecks;
    private Boolean savedFboEnable;
    private boolean diagNext = true;
    private Integer savedChunkUpdates;
    private boolean probed;
    private boolean vulkanNoted;
    private int vulkanCompiledAfter = -1;
    private int crosshairFailures;
    private int pinnedResizes;
    private int warmup;
    private boolean running;
    /** Recording time of the frame being drawn, for the FPS counter (-1 before the first). */
    private volatile long shownT = -1;

    /**
     * The frame rate the player had live at the moment of the recording being shown, or -1 when no replay is shown.
     * Like the game's own counter it changes once a second: it is the count of the last whole recorded second (the
     * first second of the recording shows its own count), not a window that slides with every drawn frame.
     */
    static int recordedFps() {
        Replay r = active;
        if (r == null || !r.running || r.finished || r.shownT < 0) {
            return -1;
        }
        long second = 1_000_000_000L;
        long start = r.rec.startTime();
        long boundary = start + Math.max(1, Math.floorDiv(r.shownT - start, second)) * second;
        return r.rec.fpsAt(boundary);
    }
    private boolean finished;

    private final File recFile;
    private final RenderRange range;
    /** Absolute recording times (ns) of the selected render range; the whole recording when nothing is selected. */
    private long rangeStartT, rangeEndT;
    private double pendingSeekT = -1;
    private boolean loopRange;
    // handing the screen to the client's own menu (Right Shift and similar)
    private boolean menuReleased, clientScreenSeen, menuDrove, menuWasPaused, pendingPaused;
    private long menuReleaseT, menuSavedPos, menuLastTick;
    private final boolean diffMode;
    private FrameDiff diff;
    private java.io.DataInputStream liveIn;
    private byte[][] diffLive;
    private int diffW, diffH;
    private float lastPartial;
    private int particlesMatched, particlesUnmatched, emittersInjected, particlesInjected, particlesInjectFailed;
    private final java.util.TreeMap<String, int[]> particleStats = new java.util.TreeMap<String, int[]>();

    private Replay(Recording rec, File recFile, File outFile, boolean diffMode, boolean preview) {
        this.rec = rec;
        this.recFile = recFile;
        this.range = RenderRange.load(recFile);
        this.diffMode = diffMode;
        this.preview = preview;
        this.outFile = outFile;
        readRenderSettings();
        applyRange();
        this.warmup = SubFrameMod.warmupFrames;
    }

    /** Output fps, subframes and shutter are read when a render starts, so the viewer menu can change them. */
    private static boolean lukaMotionBlurOn() {
        try {
            return Class.forName("dev.motionblur.MotionBlurSettings").getField("enabled").getBoolean(null);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean forcedBlend;
    /** Raw high-fps renders: the seconds each rendered frame counts as in luka's own Motion Blur (0 = leave the blur alone). */
    private double blurDt;

    /** Runs the game's own Motion Blur on every frame of an offline render, as if the game ran at the output frame rate. */
    private static void setBlurClock(double dt) {
        try {
            Class<?> r = Class.forName("dev.motionblur.BlurRenderer");
            r.getField("fixedDt").setDouble(null, dt);
            r.getMethod("reset").invoke(null);
        } catch (Throwable t) {
            SubFrameMod.LOG.warn("[render] could not set the Motion Blur clock: " + t);
        }
    }

    private void readRenderSettings() {
        forcedBlend = false;
        blurDt = 0;
        outFps = SubFrameMod.outputFps;
        subframes = SubFrameMod.blend ? Math.max(1, SubFrameMod.subframes) : 1;
        if (!SubFrameMod.blend && lukaMotionBlurOn() && outFps <= 120) {
            // the live motion blur is a screen effect that cannot run while frames are rendered offline: the same look comes from
            // blending subframes, so a render with luka's Motion Blur on is blended even when "raw high-fps frames" was picked
            subframes = Math.max(8, SubFrameMod.subframes);
            forcedBlend = true;
            SubFrameMod.LOG.info("[render] Motion Blur is on: blending " + subframes + " subframes per frame instead of writing raw frames");
        }
        shutter = Math.max(0.01, Math.min(1.0, SubFrameMod.shutter));
        frameDur = 1e9 / outFps;
        blurDt = (outFps > 120 && subframes == 1 && lukaMotionBlurOn()) ? 1.0 / outFps : 0;
    }

    /** Renders the whole recording to an mp4 straight away. */
    static void start(File file) {
        start(file, false, false);
    }

    /** Opens the viewer: real-time playback with a menu (Esc) for options and for starting the render. */
    static void startPreview(File file) {
        start(file, false, true);
    }

    /** Re-renders the frames LiveCapture saved, one unblended subframe each, and reports how far they are from the live ones. */
    static void startDiff(File file) {
        start(file, true, false);
    }

    /** An output name that does not exist yet (video or PNG folder), so a render never overwrites an earlier one. */
    private static File freeOutput(File dir, String base) {
        File f = new File(dir, base + ".mp4");
        for (int n = 2; f.exists() || new File(dir, base + (n == 2 ? "" : "-" + (n - 1)) + "-frames").exists(); n++) {
            f = new File(dir, base + "-" + n + ".mp4");
        }
        return f;
    }

    private static void start(File file, boolean diffMode, boolean preview) {
        Minecraft mc = Minecraft.getMinecraft();
        if (active != null) {
            return;
        }
        if (mc.theWorld != null) {
            SubFrameMod.LOG.warn("Leave the world first, then start a replay from the main menu.");
            return;
        }
        Replay r = null;
        try {
            Recording rec = Recording.load(file);
            String base = file.getName().replaceAll("\\.sfr$", "");
            r = new Replay(rec, file, freeOutput(file.getParentFile(), base), diffMode, preview);
            r.begin();
            active = r;
        } catch (Throwable t) {
            SubFrameMod.LOG.error("Could not start replay", t);
            active = null;
            if (r != null) {
                r.restoreSession();
            }
        }
    }

    static void abort() {
        Replay r = active;
        if (r != null) {
            r.finish("aborted");
        }
    }

    private void begin() throws Exception {
        GameSettings gs = mc.gameSettings;
        savedFov = gs.fovSetting;
        savedRenderDistance = gs.renderDistanceChunks;
        savedBob = gs.viewBobbing ? 1 : 0;
        savedHideGui = gs.hideGUI;
        savedFramerate = gs.limitFramerate;
        savedVsync = gs.enableVsync;
        if (savedTitle == null) {
            savedTitle = Display.getTitle();
        }
        muteGlErrorChat(true);
        if (!preview) {
            speedUpChunkBuilding(true);
        }
        // by default the replay uses your CURRENT settings, so changing FOV, render distance, resource packs or
        // OptiFine options after recording applies to the replay; the diff test needs the recorded ones
        overrodeSettings = diffMode || SubFrameMod.useRecordedSettings;
        if (overrodeSettings) {
            gs.fovSetting = rec.fov;
            gs.renderDistanceChunks = rec.renderDistance;
            gs.viewBobbing = rec.viewBobbing;
        }
        gs.hideGUI = false; // true would also hide the first-person hand
        gs.limitFramerate = 260;
        gs.enableVsync = false;
        Display.setVSyncEnabled(false);

        savedTimer = ((MinecraftAccessor) mc).subframe$getTimer();
        ((MinecraftAccessor) mc).subframe$setTimer(new FrozenTimer());

        chunks = new ChunkWait();
        Determinism.seed = rec.seed;
        Determinism.active = true;
        connect();
    }

    /** Builds the fake connection and joins the recorded world. Also used to restart from the beginning. */
    private void connect() {
        packetIdx = 0;
        net = new NetworkManager(true);
        channel = new EmbeddedChannel(net);
        net.setConnectionState(EnumConnectionState.PLAY);
        // 1.7.10 processReceivedPackets compares this field with the channel attribute and calls onConnectionStateTransition,
        // which NetHandlerPlayClient rejects; the fake connection never goes through a real protocol change.
        ((dev.subframe.mixin.NetworkManagerAccessor) (Object) net).subframe$setConnectionState(EnumConnectionState.PLAY);
        // 1.7.10 makes the local player from the session, not from the connection: the replay's player has to be the recorded one
        UUID id = rec.playerId != null ? rec.playerId : UUID.randomUUID();
        if (savedSession == null) {
            savedSession = ((MinecraftAccessor) mc).subframe$getSession();
        }
        ((MinecraftAccessor) mc).subframe$setSession(new Session(rec.playerName, id.toString().replace("-", ""), "", "legacy"));
        handler = new NetHandlerPlayClient(mc, null, net);
        net.setNetHandler(handler);

        // a take can start long after joining: the packets before it are applied at once, and only the last few
        // ticks before the first frame are run (enough for entities to finish gliding to their positions)
        long startRef = diffMode ? rec.startTime() : preview ? (pendingSeekT >= 0 ? (long) pendingSeekT : rec.startTime()) : rangeStartT;
        int firstTick = (int) Math.floor(rec.gameTimeAt(startRef));
        // packets older than this are applied at once; the last stretch is replayed tick by tick so that effects
        // from just before the take (hurt animations, knockback, particles) play out and fade as they did live
        int cut = Math.max(rec.joinTick, firstTick - PREROLL_TICKS);
        ticksRun = cut;
        screen = new ReplayScreen();
        mc.displayGuiScreen(screen);
        // the world as it was: login, chunks, spawns
        processPacketsByTick(cut);
        SubFrameMod.LOG.info("Replay started: " + rec.frameCount + " frames, " + rec.packets.size()
                + " packets, output " + outFile);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void processPackets(long upToT) {
        while (packetIdx < rec.packets.size() && rec.packets.get(packetIdx).t <= upToT) {
            Recording.Pkt p = rec.packets.get(packetIdx++);
            fPackets++;
            try {
                Packet packet = PacketIds.create(p.id);
                if (packet == null) {
                    continue;
                }
                packet.readPacketData(new PacketBuffer(Unpooled.wrappedBuffer(p.data)));
                packet.processPacket(handler);
            } catch (Throwable t) {
                SubFrameMod.LOG.error("Packet " + p.id + " failed in replay", t);
            }
        }
        channel.outboundMessages().clear();
    }

    int currentTick() {
        return ticksRun;
    }

    /** The replayed game made an emitter itself (from a packet): mark the matching live record as covered. */
    void emitterSeen(int tick, int entityId, int typeId) {
        for (int dt : new int[]{0, -1, 1}) {
            java.util.List<int[]> l = rec.emitters.get(tick + dt);
            if (l == null) {
                continue;
            }
            for (int[] e : l) {
                if (e[2] == 0 && e[0] == entityId && e[1] == typeId) {
                    e[2] = 1;
                    return;
                }
            }
        }
    }

    /** Recreates emitters the live game made from local input (own hits), which a replay has no input for. */
    private void injectEmitters() {
        java.util.List<int[]> l = rec.emitters.get(ticksRun);
        if (l == null || mc.theWorld == null || !(mc.effectRenderer instanceof DeterministicEffectRenderer)) {
            return;
        }
        for (int[] e : l) {
            if (e[2] != 0) {
                continue;
            }
            Entity target = mc.theWorld.getEntityByID(e[0]);
            e[2] = 1;
            if (target != null) {
                ((DeterministicEffectRenderer) mc.effectRenderer).inject(target, e[1]);
                emittersInjected++;
            }
        }
    }

    /** Rebuilds recorded particles that no replayed spawn claimed (made live from the player's own input). */
    private void injectParticles(int upToTick) {
        if (!(mc.effectRenderer instanceof DeterministicEffectRenderer) || mc.theWorld == null) {
            return;
        }
        for (ParticleState.Rec r : rec.dueParticles(upToTick)) {
            if (!r.injectable) {
                continue;
            }
            try {
                net.minecraft.client.particle.EntityFX fx = ParticleState.rebuild(r);
                // it would have been updated once in the tick it was created, and once in each tick since
                for (int i = ticksRun - r.tick; i > 0 && !fx.isDead; i--) {
                    fx.onUpdate();
                }
                if (!fx.isDead) {
                    ((DeterministicEffectRenderer) mc.effectRenderer).injectEffect(fx);
                    particlesInjected++;
                }
            } catch (Throwable t) {
                if (particlesInjectFailed++ < 3) {
                    SubFrameMod.LOG.error("Could not rebuild particle " + r.cls, t);
                }
            }
        }
    }

    /** Applies recorded packets that arrived while the live game had completed at most this many ticks. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void processPacketsByTick(int maxTick) {
        while (packetIdx < rec.packets.size() && rec.packets.get(packetIdx).tick <= maxTick) {
            Recording.Pkt p = rec.packets.get(packetIdx++);
            try {
                Packet packet = PacketIds.create(p.id);
                if (packet == null) {
                    continue;
                }
                packet.readPacketData(new PacketBuffer(Unpooled.wrappedBuffer(p.data)));
                packet.processPacket(handler);
            } catch (Throwable t) {
                SubFrameMod.LOG.error("Packet " + p.id + " failed in replay", t);
            }
        }
        channel.outboundMessages().clear();
    }

    private void tickOnce() {
        // packets that arrived before this tick started were applied before it in the live game; ticks from before
        // the take have no recorded start time, so those use the tick number the packet arrived in
        long start = rec.tickStartTime(ticksRun + 1);
        if (start >= 0) {
            processPackets(start);
        } else {
            processPacketsByTick(ticksRun);
        }
        injectEmitters();
        // entities update after the player in the live tick, so they must see the player where the tick leaves it
        PlayerState next = rec.stateAt(ticksRun + 1);
        if (mc.thePlayer != null && next != null) {
            mc.thePlayer.setPosition(next.x, next.y, next.z);
        }
        long tk0 = System.nanoTime();
        try {
            mc.runTick();
        } catch (Throwable t) {
            SubFrameMod.LOG.error("Tick failed in replay", t);
            if (rlog != null && tickFailures++ < 20) {
                java.io.StringWriter sw = new java.io.StringWriter();
                t.printStackTrace(new java.io.PrintWriter(sw));
                rlog.important("TICK FAILED at tick " + ticksRun + ": " + sw.toString().replace("\r", "").replace("\n", " | "));
            }
        }
        nsTicks += System.nanoTime() - tk0;
        fTicks++;
        ticksRun++;
        applyPlayerState();
        injectParticles(ticksRun - 2);
        channel.outboundMessages().clear();
        reclaimScreen();
    }

    /** The join packet opens the download-terrain screen; take it over, but leave the viewer's own menus alone. */
    private void reclaimScreen() {
        // the loading-terrain screen closes itself when the position packet is processed, as in the live game
        if (mc.theWorld != null && mc.currentScreen == null) {
            if (menuReleased) {
                long waited = System.nanoTime() - menuReleaseT;
                if (!clientScreenSeen && waited < 3_000_000_000L) {
                    driveClientTicks();
                    return;
                }
                finishMenuRelease();
            }
            mc.displayGuiScreen(screen);
        } else if (menuReleased && mc.currentScreen != null && mc.currentScreen != screen) {
            if (!clientScreenSeen) {
                SubFrameMod.LOG.info("[viewer] the client opened " + mc.currentScreen.getClass().getName());
            }
            clientScreenSeen = true;
        }
    }

    /**
     * Gives the screen back to the game for a moment so the client can react to a key it only handles with no screen
     * open (the mod menu key). Closing the client's menu returns to the viewer, at the same place and pause state.
     */
    void releaseForClientMenu() {
        if (!running || !preview || menuReleased || mc.currentScreen != screen) {
            return;
        }
        menuReleased = true;
        clientScreenSeen = false;
        menuDrove = false;
        menuWasPaused = paused;
        menuSavedPos = posNs();
        menuReleaseT = System.nanoTime();
        menuLastTick = 0;
        mc.displayGuiScreen(null);
    }

    /** The client's key handling runs inside the game's own client tick, which the viewer otherwise only runs with the replay clock. */
    private void driveClientTicks() {
        long now = System.nanoTime();
        if (now - menuLastTick < 50_000_000L) {
            return;
        }
        menuLastTick = now;
        try {
            menuDrove = true;
            mc.runTick();
        } catch (Throwable t) {
            SubFrameMod.LOG.warn("[viewer] client tick while handing over input failed: " + t);
        }
    }

    private void finishMenuRelease() {
        menuReleased = false;
        if (menuDrove) {
            // the world kept ticking while the client had control: go back to where the viewer was
            pendingPaused = menuWasPaused;
            seekRel(menuSavedPos);
        } else if (menuWasPaused) {
            paused = true;
        }
    }

    private void applyPlayerState() {
        EntityPlayerSP p = mc.thePlayer;
        PlayerState s = rec.stateAt(ticksRun);
        if (p != null && s != null) {
            s.applyTo(p);
            if (s.hasPose) {
                return;
            }
            // a clip from before the real pose was saved: work it out from the recorded movement
            advanceLimbs();
            p.limbSwing = limbPhase;
            p.limbSwingAmount = limbAmount;
            p.prevLimbSwingAmount = limbPrevAmount;
            p.renderYawOffset = bodyYaw;
            p.prevRenderYawOffset = prevBodyYaw;
            p.rotationYawHead = s.yaw;
            p.prevRotationYawHead = s.pyaw;
        }
    }

    /**
     * The walk animation comes from how far the player moved in each tick. The replay places the player directly, so the game sees no movement
     * and the legs would stand still; this is the game's own calculation run on the recorded positions. It starts over after a seek back or a
     * jump forward, so a tick always gets the same pose however it was reached.
     */
    private void advanceLimbs() {
        if (limbTick < 0 || ticksRun < limbTick || ticksRun > limbTick + 1) {
            limbTick = 0;
            limbPhase = limbAmount = limbPrevAmount = 0f;
            PlayerState first = rec.stateAt(1);
            bodyYaw = prevBodyYaw = first == null ? 0f : first.yaw;
        }
        for (; limbTick < ticksRun; limbTick++) {
            PlayerState s = rec.stateAt(limbTick + 1);
            if (s == null) {
                continue;
            }
            double dx = s.x - s.px, dz = s.z - s.pz;
            float target = Math.min(1f, (float) Math.sqrt(dx * dx + dz * dz) * 4f);
            limbPrevAmount = limbAmount;
            limbAmount += (target - limbAmount) * 0.4f;
            limbPhase += limbAmount;
            turnBody(s, dx, dz);
        }
    }

    /** The game's body rotation on the recorded movement: the body leans toward the way the player is travelling and stays within 75 degrees of the head. */
    private static float wrapDegrees(float degrees) {
        degrees %= 360f;
        return degrees >= 180f ? degrees - 360f : degrees < -180f ? degrees + 360f : degrees;
    }

    private void turnBody(PlayerState s, double dx, double dz) {
        prevBodyYaw = bodyYaw;
        float wanted = bodyYaw;
        if (dx * dx + dz * dz > 0.0025) {
            wanted = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        }
        if (s.swing > 0f) {
            wanted = s.yaw;
        }
        bodyYaw += wrapDegrees(wanted - bodyYaw) * 0.3f;
        float off = wrapDegrees(s.yaw - bodyYaw);
        off = Math.max(-75f, Math.min(75f, off));
        bodyYaw = s.yaw - off;
        if (off * off > 2500f) {
            bodyYaw += off * 0.2f;
        }
    }

    /**
     * The game reads mouse and keyboard input for the open screen only inside a client tick. The replay freezes the
     * game clock and runs ticks itself, so while paused (or in the menu) no tick runs and clicks and keys would never
     * be read. Read them here, once per displayed frame.
     */
    private void pumpInput() {
        // a mod screen that closes itself leaves no screen open: take the replay screen back so input keeps working
        reclaimScreen();
        net.minecraft.client.gui.GuiScreen s = mc.currentScreen;
        if (s != null) {
            try {
                s.handleInput();
                inputPumps++;
            } catch (Throwable t) {
                SubFrameMod.LOG.error("Reading input failed", t);
            }
            try {
                Freecam.input();
            } catch (Throwable t) {
                SubFrameMod.LOG.error("Free camera input failed", t);
            }
        }
    }

    int inputPumps;

    /** Called from the render-tick END hook, while the game framebuffer is bound. */
    void onRenderEnd() {
        if (finished) {
            return;
        }
        entryFbo = org.lwjgl.opengl.GL11.glGetInteger(0x8CA6);
        pumpInput();
        if (finished) {
            return;
        }
        if (!running) {
            // let the real render loop run a while so chunk meshes for the starting view are built
            if (mc.theWorld != null && mc.thePlayer != null && --warmup <= 0) {
                if (preview) {
                    running = true;
                    tCur = pendingSeekT >= 0 ? pendingSeekT : rec.startTime();
                    pendingSeekT = -1;
                    if (pendingPaused) {
                        paused = true;
                        pendingPaused = false;
                    }
                    lastReal = System.nanoTime();
                } else {
                    startOutput();
                }
            } else {
                reclaimScreen();
            }
            return;
        }
        if (preview) {
            previewStep();
            return;
        }
        if (diffMode) {
            diffStep();
            return;
        }
        double tEnd = rangeStartT + (frameIndex + 1) * frameDur;
        boolean capped = SubFrameMod.maxSeconds > 0 && frameIndex * frameDur >= SubFrameMod.maxSeconds * 1e9;
        if (tEnd > rangeEndT || capped) {
            finish("finished");
            return;
        }
        if (!windowReady()) {
            return;
        }
        if (useRt) {
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, rtFbo);
            org.lwjgl.opengl.GL11.glViewport(0, 0, outW, outH);
        }
        long frameStart = System.nanoTime();
        fTicks = 0;
        fSettle = 0;
        fPackets = 0;
        nsTicks = 0;
        nsRender = 0;
        nsWrite = 0;
        if (acc == null) {
            // raw output: one untouched frame per 1/fps of recorded time, to be blended (or not) by other software
            renderSubframe((long) (tEnd - frameDur));
        } else {
            acc.clear();
            for (int i = 0; i < subframes; i++) {
                double ti = tEnd - shutter * frameDur + (i + 0.5) / subframes * shutter * frameDur;
                renderSubframe((long) ti);
                acc.add(1f / subframes);
            }
            acc.resolve();
            // restoring the buffer bindings raises a harmless INVALID_OPERATION on setups where the game draws straight
            // into the window; clear it so the readback check below only reports real problems
            org.lwjgl.opengl.GL11.glGetError();
        }
        if (drawsCrosshairItself()) {
            try {
                CrosshairOverlay.draw();
            } catch (Throwable t) {
                if (crosshairFailures++ < 3) {
                    SubFrameMod.LOG.warn("Could not draw the crosshair on the finished frame: " + t);
                }
            }
            org.lwjgl.opengl.GL11.glGetError();
        }
        int pendingGlError = org.lwjgl.opengl.GL11.glGetError();
        if (pendingGlError != 0 && rlog != null && pendingErrors++ < 3) {
            rlog.important("frame " + frameIndex + ": GL error " + pendingGlError + " was already pending before the frame was read back (raised while drawing or blending)");
        }
        long wr0 = System.nanoTime();
        try {
            if (sink != null) {
                sink.writeFrame();
            }
        } catch (Exception e) {
            SubFrameMod.LOG.error("Writing frame failed", e);
            if (rlog != null) {
                rlog.important("frame " + frameIndex + ": WRITING THE FRAME FAILED: " + e);
            }
            sink = null;
        }
        nsWrite = System.nanoTime() - wr0;
        if (rlog != null) {
            logFrame(tEnd, System.nanoTime() - frameStart);
        }
        if (useRt) {
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, entryFbo);
        }
        frameIndex++;
        if (frameIndex % 60 == 0) {
            double done = (frameIndex * frameDur) / (rangeEndT - rangeStartT) * 100;
            Display.setTitle(String.format("SubFrame replay %.0f%%", done));
        }
    }

    /** One displayed frame of the viewer: advance the virtual clock by real time, unless paused or in a menu. */
    private void previewStep() {
        long now = System.nanoTime();
        boolean menuOpen = mc.currentScreen != screen;
        if (!paused && !menuOpen) {
            tCur += (now - lastReal);
        }
        lastReal = now;
        if (loopRange && range.isSet() && tCur >= rangeEndT) {
            seekRel(inNs());
            return;
        }
        if (tCur >= rec.endTime()) {
            restart(true);
            return;
        }
        renderSubframe((long) tCur);
    }

    /** Re-joins the recorded world from the start; the user's settings changes are kept. */
    private void restart(boolean asPreview) {
        preview = asPreview;
        running = false;
        warmup = SubFrameMod.warmupFrames;
        frameIndex = 0;
        paused = false;
        if (channel != null) {
            channel.close();
        }
        mc.loadWorld(null);
        connect();
    }

    // ---- viewer controls, called from ReplayScreen / ReplayMenuScreen ----

    private static final boolean CROSSHAIR_DIAG = Boolean.getBoolean("subframe.crosshairdiag");

    /** Developer check (-Dsubframe.crosshairdiag): every 2 s of video the crosshair handling changes: 0 = game only, 1 = game's cancelled, 2 = cancelled and drawn here. */
    private int crosshairPhase() {
        return CROSSHAIR_DIAG ? (int) ((frameIndex / 120) % 3) : 2;
    }

    private boolean crosshairActive() {
        return SubFrameMod.crosshairOverlay && running && !preview && !diffMode && !finished && !CrosshairOverlay.lunarCrosshairActive();
    }

    /** True while the game's own crosshair is cancelled for the subframes of a render. */
    boolean cancelsCrosshair() {
        return crosshairActive() && crosshairPhase() >= 1;
    }

    /** True while a render draws the crosshair once on the finished frame. */
    boolean drawsCrosshairItself() {
        return crosshairActive() && crosshairPhase() == 2;
    }

    private void restoreSession() {
        if (savedSession != null) {
            ((MinecraftAccessor) mc).subframe$setSession(savedSession);
            savedSession = null;
        }
    }

    private void applyRange() {
        if (range.isSet()) {
            rangeStartT = rec.startTime() + (long) (range.start * 1e9);
            rangeEndT = Math.min(rec.endTime(), rec.startTime() + (long) (range.end * 1e9));
            if (rangeEndT <= rangeStartT) {
                rangeStartT = rec.startTime();
                rangeEndT = rec.endTime();
            }
        } else {
            rangeStartT = rec.startTime();
            rangeEndT = rec.endTime();
        }
    }

    private java.util.List<KillMarks.Kill> killList;

    /** The kills the recording player got, in timeline order (found once, from the recorded chat). */
    java.util.List<KillMarks.Kill> kills() {
        if (killList == null) {
            try {
                killList = KillMarks.scan(rec);
            } catch (Throwable t) {
                SubFrameMod.LOG.warn("Could not look for kills: " + t);
                killList = new java.util.ArrayList<KillMarks.Kill>();
            }
        }
        return killList;
    }

    /** Length of the recording in ns. */
    long totalNs() {
        return rec.endTime() - rec.startTime();
    }

    /** Where the viewer is, in ns from the start of the recording. */
    long posNs() {
        return (long) Math.max(0, Math.min(totalNs(), tCur - rec.startTime()));
    }

    boolean rangeActive() {
        return range.isSet();
    }

    long inNs() {
        return rangeStartT - rec.startTime();
    }

    long outNs() {
        return rangeEndT - rec.startTime();
    }

    boolean isLooping() {
        return loopRange;
    }

    void toggleLoop() {
        loopRange = !loopRange;
    }

    /** Jumps the viewer to a time (ns from the start). Going back means re-joining the recorded world and catching up. */
    void seekRel(long ns) {
        if (!running || !preview) {
            return;
        }
        double t = rec.startTime() + Math.max(0, Math.min(totalNs() - 1, ns));
        if (t >= tCur) {
            tCur = t;
        } else {
            pendingSeekT = t;
            restart(true);
        }
    }

    private static final long MIN_RANGE_NS = 200_000_000L;

    /** Sets the first and last moment to render (ns from the start) and saves them next to the recording. */
    void setRange(long inNs, long outNs) {
        long total = totalNs();
        inNs = Math.max(0, Math.min(total - MIN_RANGE_NS, inNs));
        outNs = Math.max(inNs + MIN_RANGE_NS, Math.min(total, outNs));
        range.start = inNs / 1e9;
        range.end = outNs / 1e9;
        range.save(recFile);
        applyRange();
    }

    void clearRange() {
        range.start = range.end = -1;
        range.save(recFile);
        applyRange();
    }

    String rangeLabel() {
        return range.isSet() ? RenderRange.format(range.start) + " - " + RenderRange.format(range.end) : null;
    }

    boolean isPreview() {
        return preview;
    }

    boolean isPaused() {
        return paused;
    }

    void togglePause() {
        paused = !paused;
    }

    void restartPreview() {
        restart(true);
    }

    /** Renders the whole recording to an mp4 with the current settings and the output fps / subframes from the menu. */
    void startRender() {
        readRenderSettings();
        restart(false);
    }

    ReplayScreen screen() {
        return screen;
    }

    void quit() {
        finish("closed");
    }

    private void startOutput() {
        customSize = SubFrameMod.renderWidth > 0 && !preview && !diffMode;
        int w = customSize ? SubFrameMod.renderWidth : mc.displayWidth;
        int h = customSize ? SubFrameMod.renderHeight : mc.displayHeight;
        if (diffMode) {
            try {
                loadLiveFrames(w, h);
            } catch (Throwable t) {
                SubFrameMod.LOG.error("Could not load live frames for the diff", t);
                finish("error");
                return;
            }
            running = true;
            return;
        }
        try {
            if (subframes > 1) {
                acc = new Accumulator(w, h);
            }
            if (blurDt > 0) {
                setBlurClock(blurDt);
                SubFrameMod.LOG.info("[render] luka Motion Blur runs on every frame as if the game ran at " + outFps + " fps (same strength and mode as in game)");
            }
            if (SubFrameMod.exportVideo) {
                if (SubFrameMod.outputPng) {
                    File dir = new File(outFile.getParentFile(), outFile.getName().replaceAll("\\.mp4$", "") + "-frames");
                    sink = new PngSink(dir, w, h, outFps);
                } else {
                    String args = Encoders.args(SubFrameMod.blend || forcedBlend);
                    // H.264 level 6.2 (the top one NVENC accepts) allows about 4.18 M macroblocks per second: raw high-fps renders
                    // (e.g. 960 fps at 1080p) exceed it and NVENC fails with "Invalid Level", leaving an empty file
                    if (args.contains("h264_nvenc") && Encoders.gpuMaxFps(args, w, h) < outFps) {
                        String hevc = Encoders.hevcGpuArgs();
                        if (hevc != null) {
                            SubFrameMod.LOG.warn("[render] " + outFps + " fps at " + w + "x" + h + " is above H.264's limit; using HEVC on the GPU");
                            args = hevc;
                        }
                    }
                    // Some GPU encoders refuse frame rates above their level (about 2,000 fps at 1080p for HEVC; the NVIDIA driver
                    // tested on 2026-11-24 took 20,000 directly). A short test encode at the real size and rate decides; when it
                    // is refused, the encoder is told a rate it accepts and FfmpegSink rescales the finished file's timestamps
                    // to the real one: same frames, GPU speed
                    int encodeFps = outFps;
                    if (Encoders.isGpu(args) && !Encoders.acceptsRate(args, w, h, outFps)) {
                        encodeFps = Math.min(outFps, Encoders.gpuMaxFps(args, w, h));
                        while (encodeFps > 30 && !Encoders.acceptsRate(args, w, h, encodeFps)) {
                            encodeFps /= 2;
                        }
                        SubFrameMod.LOG.warn("[render] the GPU encoder refuses " + outFps + " fps at " + w + "x" + h + "; encoding at "
                                + encodeFps + " fps, then setting the file's frame rate to " + outFps);
                    }
                    Integer testRate = Integer.getInteger("subframe.test.encodeFps"); // rig check of the rescaling path
                    if (testRate != null && testRate < outFps) {
                        encodeFps = testRate;
                    }
                    SubFrameMod.LOG.info("[render] encoder arguments: " + args);
                    encoderUsed = args;
                    sink = new FfmpegSink(SubFrameMod.ffmpegPath, w, h, outFps, encodeFps, args, outFile, null);
                }
            }
        } catch (Throwable t) {
            SubFrameMod.LOG.error("Could not start offline renderer", t);
            finish("error");
            return;
        }
        outW = w;
        outH = h;
        // other mods leave a render alone while this is set (luka's "Limit FPS when unfocused" would throttle it to 30 fps)
        System.setProperty("luka.rendering", "true");
        if (customSize) {
            // the HUD keeps its share of the picture: the GUI scale grows with the render size (GUI scale 2 in a 1080p window
            // becomes 4 in a 4K render), and goes back when the render ends
            savedGuiScale = mc.gameSettings.guiScale;
            int windowScale = new net.minecraft.client.gui.ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
            mc.gameSettings.guiScale = Math.max(1, Math.round(windowScale * (float) h / Math.max(1, mc.displayHeight)));
            pinSize();
            {
                SubFrameMod.LOG.info("[render] render size " + w + "x" + h + " (window " + Display.getWidth() + "x" + Display.getHeight() + "), GUI scale " + windowScale + " -> " + mc.gameSettings.guiScale);
            }
        }
        try {
            String base = outFile.getName().replaceAll("\\.mp4$", "");
            rlog = new FileLog(new File(outFile.getParentFile(), "logs/render-" + base + ".log"));
            rlog.important("SubFrame render log for " + recFile.getName());
            for (String l : RenderDiag.environment(rec).split("\n")) {
                rlog.line(l);
            }
            rlog.line("encoder: " + encoderUsed);
            rlog.important("drawing target: framebuffer bound when each frame starts = " + entryFbo + ", game uses an off-screen framebuffer = "
                    + OpenGlHelper.isFramebufferEnabled() + ", game framebuffer object id=" + mc.getFramebuffer().framebufferObject
                    + " texture=" + mc.getFramebuffer().framebufferTexture);
            rlog.important("start state: " + RenderDiag.snapshot());
            createRenderTarget();
        } catch (Throwable t) {
            SubFrameMod.LOG.error("Could not open the render log", t);
        }
        lastProgressNs = System.nanoTime();
        running = true;
        startWatchdog();
    }

    /**
     * If no frame is finished for several seconds, write every thread's stack into the render log, so a hang inside the
     * game, another mod or the driver can be seen afterwards instead of guessed at.
     */
    private void startWatchdog() {
        final FileLog log = rlog;
        if (log == null) {
            return;
        }
        Thread t = new Thread("SubFrame watchdog") {
            @Override
            public void run() {
                long seen = -1;
                long since = System.nanoTime();
                int dumps = 0;
                while (Replay.active == Replay.this && dumps < 3) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        return;
                    }
                    long now = System.nanoTime();
                    if (frameIndex != seen) {
                        seen = frameIndex;
                        since = now;
                    } else if (now - since > 8_000_000_000L) {
                        StringBuilder sb = new StringBuilder("NO FRAME FOR 8 s at frame " + seen + ", thread stacks: ");
                        for (java.util.Map.Entry<Thread, StackTraceElement[]> en : Thread.getAllStackTraces().entrySet()) {
                            String n = en.getKey().getName();
                            if (!n.equals("Client thread") && !n.startsWith("SubFrame") && !n.contains("Render") && !n.contains("ffmpeg")) {
                                continue;
                            }
                            sb.append("[").append(n).append(" ").append(en.getKey().getState()).append(": ");
                            StackTraceElement[] st = en.getValue();
                            for (int i = 0; i < Math.min(st.length, 25); i++) {
                                sb.append(st[i]).append(" < ");
                            }
                            sb.append("] ");
                        }
                        log.important(sb.toString());
                        dumps++;
                        since = now;
                    }
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }

    /**
     * Rendering does not depend on the window: when the player alt-tabs or minimises the game, the game keeps being
     * drawn into an off-screen buffer of the render size. The game itself reacts to a minimised window by resizing
     * its buffers to nothing, so the size is put back before every frame.
     */
    private boolean windowReady() {
        if (windowDependent && !Display.isVisible()) {
            if (!waitingForWindow) {
                waitingForWindow = true;
                windowHiddenSince = System.nanoTime();
                String m = "render PAUSED at frame " + frameIndex + ": this client draws into the game window and the window is minimised. "
                        + "Bring the game window back and the render continues by itself.";
                SubFrameMod.LOG.warn("[render] " + m);
                if (rlog != null) {
                    rlog.important(m);
                }
            }
            Display.setTitle("SubFrame render PAUSED - bring this window back (frame " + frameIndex + ")");
            windowStableFrames = 0;
            return false;
        }
        if (waitingForWindow) {
            if (++windowStableFrames < 6) {
                return false;
            }
            waitingForWindow = false;
            String m = String.format("render RESUMED at frame %d after %.1f s paused", frameIndex, (System.nanoTime() - windowHiddenSince) / 1e9);
            SubFrameMod.LOG.info("[render] " + m);
            if (rlog != null) {
                rlog.important(m);
            }
            prevHash = Long.MIN_VALUE;
            identicalRun = 0;
        }
        if (mc.displayWidth != outW || mc.displayHeight != outH) {
            pinSize();
        }
        boolean hidden = !Display.isVisible() || !Display.isActive();
        if (hidden && !hiddenLogged) {
            hiddenLogged = true;
            diagNext = true;
            String m = "frame " + frameIndex + ": the window is hidden or in the background (visible=" + Display.isVisible()
                    + ", active=" + Display.isActive() + "), rendering carries on off-screen";
            SubFrameMod.LOG.info("[render] " + m);
            if (rlog != null) {
                rlog.important(m);
            }
        } else if (!hidden && hiddenLogged) {
            hiddenLogged = false;
            if (rlog != null) {
                rlog.important("frame " + frameIndex + ": window back in the foreground");
            }
        }
        return true;
    }

    /** Puts the game's view of the window size back to the render size (what a normal window resize does). */
    private void pinSize() {
        try {
            ((MinecraftAccessor) mc).subframe$resize(outW, outH);
            if (pinnedResizes++ < 3 && rlog != null) {
                rlog.important("frame " + frameIndex + ": window size was " + Display.getWidth() + "x" + Display.getHeight()
                        + ", game size set back to " + outW + "x" + outH);
            }
        } catch (Throwable t) {
            if (pinnedResizes++ < 3) {
                SubFrameMod.LOG.warn("Could not set the render size back: " + t);
            }
            mc.displayWidth = outW;
            mc.displayHeight = outH;
        }
    }

    /** A colour + depth/stencil target of the render size, for games that draw straight into the window. */
    private void createRenderTarget() {
        useRt = false;
        if (OpenGlHelper.isFramebufferEnabled() || preview || diffMode) {
            return;
        }
        // Prefer the game's own off-screen framebuffer (what most setups use anyway): some clients redraw into the
        // window whatever buffer is bound, and a hidden window has nothing to draw into.
        if (!OpenGlHelper.framebufferSupported && rlog != null) {
            rlog.important("the graphics driver reports no framebuffer support, drawing into the window");
        }
        if (OpenGlHelper.framebufferSupported) {
            try {
                savedFboEnable = mc.gameSettings.fboEnable;
                mc.gameSettings.fboEnable = true;
                pinSize();
                if (rlog != null) {
                    rlog.important("tried switching the game framebuffers on: fboEnable was " + savedFboEnable + ", now enabled="
                            + OpenGlHelper.isFramebufferEnabled() + ", game framebuffer id=" + mc.getFramebuffer().framebufferObject
                            + " " + mc.getFramebuffer().framebufferWidth + "x" + mc.getFramebuffer().framebufferHeight);
                }
                if (OpenGlHelper.isFramebufferEnabled() && mc.getFramebuffer().framebufferObject > 0) {
                    if (rlog != null) {
                        rlog.important("game framebuffers were off; switched them on for this render (game framebuffer id="
                                + mc.getFramebuffer().framebufferObject + " " + mc.getFramebuffer().framebufferWidth + "x" + mc.getFramebuffer().framebufferHeight + ")");
                    }
                    SubFrameMod.LOG.info("[render] game framebuffers switched on for the render");
                    return;
                }
            } catch (Throwable t) {
                SubFrameMod.LOG.warn("[render] could not switch the game framebuffers on: " + t);
                if (rlog != null) {
                    rlog.important("could not switch the game framebuffers on: " + t);
                }
            }
            mc.gameSettings.fboEnable = savedFboEnable;
            savedFboEnable = null;
            try {
                pinSize();
            } catch (Throwable ignored) {
                // keep going with the window as the target
            }
        }
        try {
            int prev = org.lwjgl.opengl.GL11.glGetInteger(0x8CA6);
            rtTex = org.lwjgl.opengl.GL11.glGenTextures();
            org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, rtTex);
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11.GL_NEAREST);
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11.GL_NEAREST);
            org.lwjgl.opengl.GL11.glTexImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_RGBA8, outW, outH, 0,
                    org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            rtDepth = org.lwjgl.opengl.GL30.glGenRenderbuffers();
            org.lwjgl.opengl.GL30.glBindRenderbuffer(org.lwjgl.opengl.GL30.GL_RENDERBUFFER, rtDepth);
            org.lwjgl.opengl.GL30.glRenderbufferStorage(org.lwjgl.opengl.GL30.GL_RENDERBUFFER, org.lwjgl.opengl.GL30.GL_DEPTH24_STENCIL8, outW, outH);
            rtFbo = org.lwjgl.opengl.GL30.glGenFramebuffers();
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, rtFbo);
            org.lwjgl.opengl.GL30.glFramebufferTexture2D(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_2D, rtTex, 0);
            org.lwjgl.opengl.GL30.glFramebufferRenderbuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, org.lwjgl.opengl.GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                    org.lwjgl.opengl.GL30.GL_RENDERBUFFER, rtDepth);
            int status = org.lwjgl.opengl.GL30.glCheckFramebufferStatus(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER);
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, prev);
            useRt = status == org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE;
            if (!useRt) {
                SubFrameMod.LOG.warn("[render] off-screen render target incomplete (status " + status + "), drawing into the window");
                deleteRenderTarget();
            }
            org.lwjgl.opengl.GL11.glGetError();
        } catch (Throwable t) {
            SubFrameMod.LOG.warn("[render] could not create an off-screen render target: " + t);
            useRt = false;
            deleteRenderTarget();
        }
    }

    /** Which framebuffers are bound and where the viewport is, plus the centre pixel the game just drew (for the render log). */
    private String glState() {
        try {
            java.nio.IntBuffer vp = org.lwjgl.BufferUtils.createIntBuffer(16);
            org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_VIEWPORT, vp);
            int draw = org.lwjgl.opengl.GL11.glGetInteger(0x8CA6);
            int read = org.lwjgl.opengl.GL11.glGetInteger(0x8CAA);
            int pack = org.lwjgl.opengl.GL11.glGetInteger(0x88ED);
            String px = "n/a";
            if (pack == 0) {
                java.nio.ByteBuffer b = org.lwjgl.BufferUtils.createByteBuffer(16);
                org.lwjgl.opengl.GL11.glReadPixels(outW / 2, outH / 2, 1, 1, org.lwjgl.opengl.GL11.GL_RGB, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, b);
                px = (b.get(0) & 255) + "," + (b.get(1) & 255) + "," + (b.get(2) & 255);
            }
            org.lwjgl.opengl.GL11.glGetError();
            return "drawFbo=" + draw + " readFbo=" + read + " (private target=" + rtFbo + " used=" + useRt + ", game framebuffer id="
                    + mc.getFramebuffer().framebufferObject + " " + mc.getFramebuffer().framebufferWidth + "x" + mc.getFramebuffer().framebufferHeight
                    + ", framebuffers enabled=" + OpenGlHelper.isFramebufferEnabled() + ") viewport=" + vp.get(0) + "," + vp.get(1) + "," + vp.get(2) + "," + vp.get(3)
                    + " display=" + mc.displayWidth + "x" + mc.displayHeight + " centrePixel=" + px;
        } catch (Throwable t) {
            return "glState failed: " + t;
        }
    }

    private void deleteRenderTarget() {
        try {
            if (rtFbo != 0) {
                org.lwjgl.opengl.GL30.glDeleteFramebuffers(rtFbo);
            }
            if (rtDepth != 0) {
                org.lwjgl.opengl.GL30.glDeleteRenderbuffers(rtDepth);
            }
            if (rtTex != 0) {
                org.lwjgl.opengl.GL11.glDeleteTextures(rtTex);
            }
        } catch (Throwable ignored) {
            // context may be gone
        }
        rtFbo = rtDepth = rtTex = 0;
    }

    /** Logs one finished output frame: freezes, slow frames, window changes and periodic progress. */
    private void logFrame(double tEnd, long wallNs) {
        long h = sink != null ? sink.lastHash() : 0;
        int glErr = sink != null ? sink.lastGlError() : 0;
        if (glErr != 0) {
            rlog.important("frame " + frameIndex + ": GL ERROR " + glErr + " while reading the frame back | " + RenderDiag.snapshot());
        }
        net.minecraft.client.shader.Framebuffer fb = mc.getFramebuffer();
        boolean windowOk = fb.framebufferWidth == outW && fb.framebufferHeight == outH;
        if (!windowOk && !windowWarned) {
            windowWarned = true;
            rlog.important("frame " + frameIndex + ": WINDOW CHANGED (framebuffer " + fb.framebufferWidth + "x" + fb.framebufferHeight
                    + ", started at " + outW + "x" + outH + ", visible=" + Display.isVisible() + ") | " + RenderDiag.snapshot());
        } else if (windowOk && windowWarned) {
            windowWarned = false;
            rlog.important("frame " + frameIndex + ": window back to normal");
        }
        int freezeMin = Math.max(10, outFps / 2); // half a second: shorter stills are just a still scene
        if (sink != null && h == prevHash) {
            identicalRun++;
            if (identicalRun == freezeMin) {
                String m = "FREEZE START: frame " + frameIndex + " is identical to the previous " + freezeMin + " (half a second or more; a real freeze lasts seconds) | virtual t=" + String.format("%.3f", (tEnd - rec.startTime()) / 1e9)
                        + "s g=" + String.format("%.3f", rec.gameTimeAt((long) tEnd)) + " ticksRun=" + ticksRun + " | " + RenderDiag.snapshot();
                rlog.important(m);
                SubFrameMod.LOG.warn("[render] " + m);
            } else if (identicalRun > freezeMin && identicalRun % 480 == 0) {
                rlog.important("still frozen after " + identicalRun + " identical frames (frame " + frameIndex + ") | " + RenderDiag.snapshot());
            }
        } else {
            if (identicalRun >= freezeMin) {
                String m = "FREEZE END: " + identicalRun + " identical frames, picture changes again at frame " + frameIndex;
                rlog.important(m);
                SubFrameMod.LOG.warn("[render] " + m);
            }
            identicalRun = 0;
        }
        prevHash = h;
        sumFrameNs += wallNs;
        sumRenderNs += nsRender;
        sumWriteNs += nsWrite;
        sumTicksNs += nsTicks;
        sumWaitNs += sink != null ? sink.lastWaitNs() : 0;
        double wallMs = wallNs / 1e6;
        if (wallMs > 150) {
            rlog.important(String.format("SLOW FRAME %d: %.0f ms (ticks %.0f ms for %d ticks, render %.0f ms incl. %d extra draws waiting for chunks, write %.0f ms, %d packets) | chunks: %s | %s",
                    frameIndex, wallMs, nsTicks / 1e6, fTicks, nsRender / 1e6, fSettle, nsWrite / 1e6, fPackets, chunks.describe(), RenderDiag.snapshot()));
        }
        if (frameIndex - lastProgressFrame >= 240) {
            long now = System.nanoTime();
            rlog.line(String.format("progress: frame %d, video %.2fs of %.2fs, %.0f frames/s, g=%.2f ticksRun=%d, last frame: %d ticks %d packets %d extra draws",
                    frameIndex, frameIndex * frameDur / 1e9, (rangeEndT - rangeStartT) / 1e9,
                    (frameIndex - lastProgressFrame) / ((now - lastProgressNs) / 1e9), rec.gameTimeAt((long) tEnd), ticksRun, fTicks, fPackets, fSettle));
            rlog.line("  world: " + RenderDiag.snapshot());
            diagNext = true;
            long n = Math.max(1, frameIndex - lastProgressFrame);
            rlog.line(String.format("  average per frame: total %.2f ms = render %.2f + ticks %.2f + readback/queue %.2f (of which waiting for ffmpeg %.2f) + other %.2f",
                    sumFrameNs / 1e6 / n, sumRenderNs / 1e6 / n, sumTicksNs / 1e6 / n, sumWriteNs / 1e6 / n, sumWaitNs / 1e6 / n,
                    (sumFrameNs - sumRenderNs - sumTicksNs - sumWriteNs) / 1e6 / n));
            sumFrameNs = sumRenderNs = sumWriteNs = sumTicksNs = sumWaitNs = 0;
            lastProgressNs = now;
            lastProgressFrame = frameIndex;
        }
    }

    private void renderSubframe(long t) {
        shownT = t;
        double g = rec.gameTimeAt(t);
        int target = (int) Math.floor(g);
        while (ticksRun < target) {
            tickOnce();
        }
        processPackets(rec.packetCutoff(t));
        float partial = (float) (g - target);
        lastPartial = partial;
        EntityPlayerSP p = mc.thePlayer;
        if (p != null) {
            p.rotationYaw = p.prevRotationYaw = rec.yawAt(t);
            p.rotationPitch = p.prevRotationPitch = rec.pitchAt(t);
        }
        Freecam.apply();
        Timer timer = ((MinecraftAccessor) mc).subframe$getTimer();
        timer.renderPartialTicks = partial;
        mc.ingameGUI.prevVignetteBrightness = rec.vignetteAt(t);
        // chunk meshes build on other threads: draw until none were pending at the start of the last draw
        boolean idleBefore = chunks.idle();
        long rd0 = System.nanoTime();
        mc.entityRenderer.updateCameraAndRender(partial);
        if (useRt && drawChecks < 5) {
            drawChecks++;
            if (org.lwjgl.opengl.GL11.glGetInteger(0x8CA6) != rtFbo && !windowDependent) {
                windowDependent = true;
                if (rlog != null) {
                    rlog.important("frame " + frameIndex + ": this client draws straight into the game window; the render will wait while the window is minimised");
                }
            }
        }
        if (diagNext && rlog != null) {
            diagNext = false;
            rlog.important("frame " + frameIndex + ": after the game drew: " + glState());
        }
        if (!preview) {
            // The draw left whatever chunks it did not get to in the list. Instead of redrawing the whole frame until they
            // are done, build them all, then draw once more.
            for (int round = 0; round < 6 && !(idleBefore && chunks.idle()); round++) {
                long deadline = System.nanoTime() + 3_000_000_000L;
                int pumps = 0;
                while (!chunks.idle() && System.nanoTime() < deadline) {
                    chunks.pump();
                    fSettle++;
                    if (chunks.stalled()) {
                        break; // the list is not getting shorter: the timeout handling below takes over
                    }
                    if (++pumps == 1500 && rlog != null) {
                        rlog.important("frame " + frameIndex + ": still waiting for chunk builders after 1500 polls | " + chunks.describe());
                    }
                }
                if (!chunks.idle()) {
                    // the builders did not settle within the deadline: waiting longer per frame would stall the render
                    if (rlog != null) {
                        rlog.important("frame " + frameIndex + ": chunk list did not settle, not waiting for the chunk list any more in this render | " + chunks.describe());
                    }
                    if (rlog != null && !probed) {
                        probed = true;
                        rlog.important("frame " + frameIndex + ": " + chunks.probe());
                    }
                    chunks.giveUp();
                }
                idleBefore = chunks.idle();
                mc.entityRenderer.updateCameraAndRender(partial);
            }
        }
        if (!preview && chunks.vulkanPending() >= 0) {
            if (!vulkanNoted && rlog != null) {
                vulkanNoted = true;
                rlog.important("frame " + frameIndex + ": Vulkan renderer: each frame waits until the terrain in view is built");
            }
            // Vulkan builds terrain while frames are drawn: each draw schedules the sections in view and its own threads
            // build them. Draw until nothing is being built and no new section got finished, twice in a row, then the
            // picture is complete; one more draw makes sure the last finished sections are in it
            long deadline = System.nanoTime() + 2_000_000_000L;
            int quiet = 0, draws = 0, building, lastCompiled = vulkanCompiledAfter; // what the last frame's wait ended on
            int firstCompiled = -1;
            String why = "settled";
            while ((building = chunks.vulkanPending()) >= 0 && quiet < 2 && System.nanoTime() < deadline) {
                int compiled = chunks.vulkanCompiled();
                if (firstCompiled < 0) {
                    firstCompiled = compiled;
                }
                if (building == 0 && compiled == lastCompiled) {
                    quiet++;
                } else {
                    quiet = 0;
                    if (building > 0 && chunks.vulkanStalled(building)) {
                        why = "stuck";
                        break;
                    }
                }
                lastCompiled = compiled;
                if (quiet >= 2) {
                    break;
                }
                mc.entityRenderer.updateCameraAndRender(partial);
                draws++;
                fSettle++;
            }
            vulkanCompiledAfter = chunks.vulkanCompiled();
            if (frameIndex < 5 && rlog != null) {
                rlog.line("frame " + frameIndex + ": Vulkan terrain wait: " + draws + " extra draws, sections finished " + firstCompiled + " -> "
                        + chunks.vulkanCompiled() + ", queued in view distance " + chunks.vulkanQueued() + ", "
                        + (System.nanoTime() >= deadline ? "2 s limit" : why));
            }
            if (quiet < 2 && System.nanoTime() >= deadline) {
                boolean off = chunks.vulkanTimedOut();
                if (rlog != null) {
                    rlog.important("frame " + frameIndex + ": Vulkan terrain still building after 2 s"
                            + (off ? "; not waiting for it any more in this render" : ""));
                }
            }
        }
        nsRender += System.nanoTime() - rd0;
    }

    private void loadLiveFrames(int w, int h) throws java.io.IOException {
        File f = LiveCapture.liveFile(recFile);
        // streamed one frame at a time: a long recording's live frames do not fit in memory
        liveIn = new java.io.DataInputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(f), 1 << 20));
        diffW = liveIn.readInt();
        diffH = liveIn.readInt();
        if (diffW != w || diffH != h) {
            throw new java.io.IOException("Window is " + w + "x" + h + " but live frames are "
                    + diffW + "x" + diffH + ": run the diff with the same window size");
        }
        diffLive = new byte[1][w * h * 3];
        diff = new FrameDiff(recFile);
        SubFrameMod.LOG.info("Diff: comparing the saved live frames of " + f.getName());
    }

    /** Renders exactly the live frame's moment as one unblended subframe and compares it with the saved live pixels. */
    private void diffStep() {
        int k;
        try {
            k = liveIn.readInt();
            liveIn.readFully(diffLive[0]);
        } catch (java.io.EOFException end) {
            logProbeSummary();
            SubFrameMod.LOG.info("DIFF RESULT: " + diff.finish());
            finish("diff finished");
            return;
        } catch (java.io.IOException e) {
            SubFrameMod.LOG.error("Reading live frames failed", e);
            finish("error");
            return;
        }
        renderSubframe(rec.frameT[k]);
        logProbeMismatch(k);
        diff.add(k, diffLive[0], LiveCapture.readFramebuffer(diffW, diffH), diffW, diffH);
    }

    private final int[] probeCounts = new int[Probe.SIZE];
    private final int[] probeLiveNonzero = new int[Probe.SIZE];
    private final int[] probeShown = new int[Probe.SIZE];
    private int probeFrames;

    /** Counts, per field, the frames whose camera / opponent state differs from the live game, and prints a few examples. */
    private void logProbeMismatch(int k) {
        double[] now = Probe.capture(lastPartial);
        probeFrames++;
        for (int i = 0; i < Probe.SIZE; i++) {
            double live = rec.frameProbe[k * Probe.SIZE + i];
            double tol = i == 3 || i == 4 ? 0.01 : 0.002;
            if (live != 0) {
                probeLiveNonzero[i]++;
            }
            if (Math.abs(live - now[i]) > tol) {
                probeCounts[i]++;
                if (i == 24 && probeShown[i] < 6) {
                    String liveCls = "?", nowCls = "?";
                    for (String key : rec.particles.keySet()) {
                        String c = key.split("[|]")[1];
                        if ((double) c.hashCode() == live) {
                            liveCls = c;
                        }
                        if ((double) c.hashCode() == now[i]) {
                            nowCls = c;
                        }
                    }
                    SubFrameMod.LOG.info("PROBE nearest particle class at frame " + k + ": live=" + liveCls + " replay=" + nowCls);
                }
                if (probeShown[i]++ < 3) {
                    SubFrameMod.LOG.info(String.format(java.util.Locale.ROOT,
                            "PROBE %s frame %d (ticksRun %d, g %.3f): live=%.4f replay=%.4f",
                            Probe.NAMES[i], k, ticksRun, rec.frameG[k], live, now[i]));
                }
            }
        }
    }

    /** Called for every particle the replayed game creates: restore the recorded random values if there is a match. */
    boolean particle(net.minecraft.client.particle.EntityFX fx) throws IllegalAccessException {
        ParticleState.Rec r = null;
        for (int dt : new int[]{0, -1, 1}) {
            // ambient potion swirls pick their spot from the player's own random stream, which a replay cannot
            // reproduce, so they get a wide search; everything else spawns at a precise place
            double radius = fx instanceof net.minecraft.client.particle.EntitySpellParticleFX ? 2.5 : 0.6;
            r = rec.takeParticle(ticksRun + dt, fx.getClass().getName(), fx.posX, fx.posY, fx.posZ, radius);
            if (r != null) {
                break;
            }
        }
        int[] stat = particleStats.get(fx.getClass().getSimpleName());
        if (stat == null) {
            stat = new int[2];
            particleStats.put(fx.getClass().getSimpleName(), stat);
        }
        stat[r == null ? 1 : 0]++;
        if (r == null) {
            if (particlesUnmatched++ < 0) {
                String cls = fx.getClass().getName();
                StringBuilder near = new StringBuilder();
                for (String k : rec.particles.keySet()) {
                    String[] parts = k.split("[|]");
                    if (parts[1].equals(cls) && Math.abs(Integer.parseInt(parts[0]) - ticksRun) <= 2 && near.length() < 300) {
                        near.append(' ').append(k.replace(cls, "C"));
                    }
                }
                SubFrameMod.LOG.info("PARTICLE UNMATCHED " + ParticleState.key(ticksRun, fx).replace(cls, "C") + " near:" + near);
            }
            // the recording is the authority for particle types it contains: an unclaimed replay spawn is a random
            // extra, and the real ones that the replay failed to make are rebuilt from the recording
            String c = fx.getClass().getName();
            return !(rec.classSeen.contains(c) && !rec.nonInjectable.contains(c));
        }
        particlesMatched++;
        ParticleState.apply(r, fx);
        ParticleState.reseed(fx, r.seed);
        return true;
    }

    /** Particle bookkeeping of a render, written to the render log: what the recording had and what the replay made of it. */
    private void logParticleSummary() {
        if (rlog == null) {
            return;
        }
        int recorded = 0, injectable = 0;
        java.util.TreeMap<String, int[]> byClass = new java.util.TreeMap<String, int[]>();
        for (java.util.Map.Entry<String, java.util.ArrayList<ParticleState.Rec>> e : rec.particles.entrySet()) {
            String cls = e.getKey().split("[|]")[1];
            cls = cls.substring(cls.lastIndexOf('.') + 1);
            int[] c = byClass.get(cls);
            if (c == null) {
                c = new int[2];
                byClass.put(cls, c);
            }
            c[0] += e.getValue().size();
            for (ParticleState.Rec r : e.getValue()) {
                recorded++;
                if (r.injectable) {
                    injectable++;
                    c[1]++;
                }
            }
        }
        rlog.important("PARTICLES in the recording not yet claimed at the end: " + recorded + " (" + injectable + " can be rebuilt); by class (count/rebuildable): "
                + describe(byClass));
        rlog.important("PARTICLES replay: matched with a recorded one=" + particlesMatched + ", spawned with no recorded match=" + particlesUnmatched
                + ", rebuilt from the recording=" + particlesInjected + ", rebuild failed=" + particlesInjectFailed
                + ", emitters injected=" + emittersInjected + ", particle setting=" + mc.gameSettings.particleSetting
                + ", effect renderer=" + mc.effectRenderer.getClass().getSimpleName());
        for (java.util.Map.Entry<String, int[]> e : particleStats.entrySet()) {
            rlog.line("  spawned " + e.getKey() + ": matched=" + e.getValue()[0] + " unmatched=" + e.getValue()[1]);
        }
    }

    private static String describe(java.util.Map<String, int[]> m) {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, int[]> e : m.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()[0]).append('/').append(e.getValue()[1]).append(' ');
        }
        return sb.toString();
    }

    private void logProbeSummary() {
        SubFrameMod.LOG.info("PARTICLES matched=" + particlesMatched + " unmatched=" + particlesUnmatched
                + " emittersInjected=" + emittersInjected + " particlesRebuilt=" + particlesInjected
                + " rebuildFailed=" + particlesInjectFailed);
        int emitTotal = 0, emitConsumed = 0;
        for (java.util.List<int[]> l : rec.emitters.values()) {
            for (int[] e : l) {
                emitTotal++;
                emitConsumed += e[2];
            }
        }
        SubFrameMod.LOG.info("EMITTERS recorded=" + emitTotal + " consumed(seen or injected)=" + emitConsumed);
        java.util.TreeMap<String, Integer> recorded = new java.util.TreeMap<String, Integer>();
        for (java.util.Map.Entry<String, java.util.ArrayList<ParticleState.Rec>> e : rec.particles.entrySet()) {
            String cls = e.getKey().split("[|]")[1];
            cls = cls.substring(cls.lastIndexOf('.') + 1);
            Integer c = recorded.get(cls);
            recorded.put(cls, (c == null ? 0 : c) + e.getValue().size());
        }
        SubFrameMod.LOG.info("PARTICLES left unclaimed in recording (by class, after replay): " + recorded);
        for (java.util.Map.Entry<String, int[]> e : particleStats.entrySet()) {
            SubFrameMod.LOG.info("PARTICLE CLASS " + e.getKey() + " matched=" + e.getValue()[0] + " unmatched=" + e.getValue()[1]);
        }
        StringBuilder sb = new StringBuilder("PROBE SUMMARY over " + probeFrames + " frames, mismatches:");
        for (int i = 0; i < Probe.SIZE; i++) {
            sb.append(' ').append(Probe.NAMES[i]).append('=').append(probeCounts[i]);
        }
        SubFrameMod.LOG.info(sb.toString());
        SubFrameMod.LOG.info("PROBE live frames where the value was non-zero: pHurt=" + probeLiveNonzero[12]
                + " zHurt=" + probeLiveNonzero[10] + " using(blocking)=" + probeLiveNonzero[18] + " of " + probeFrames);
    }

    /**
     * OptiFine prints "OpenGL Error" lines into the chat when the game's own drawing raises one. They would end up in
     * the video, and the render log already records GL errors, so they are switched off while rendering.
     */
    private void speedUpChunkBuilding(boolean on) {
        try {
            java.lang.reflect.Field f = GameSettings.class.getField("ofChunkUpdates");
            if (on) {
                savedChunkUpdates = f.getInt(mc.gameSettings);
                f.setInt(mc.gameSettings, 5);
            } else if (savedChunkUpdates != null) {
                f.setInt(mc.gameSettings, savedChunkUpdates);
            }
        } catch (Throwable ignored) {
            // OptiFine not installed: vanilla has no such limit
        }
    }

    private void muteGlErrorChat(boolean mute) {
        try {
            java.lang.reflect.Field f = GameSettings.class.getField("ofShowGlErrors");
            if (mute) {
                savedShowGlErrors = f.getBoolean(mc.gameSettings);
                f.setBoolean(mc.gameSettings, false);
            } else if (savedShowGlErrors != null) {
                f.setBoolean(mc.gameSettings, savedShowGlErrors);
            }
        } catch (Throwable ignored) {
            // OptiFine not installed
        }
    }

    private void finish(String why) {
        if (blurDt > 0) {
            setBlurClock(0);
            blurDt = 0;
        }
        Freecam.exit();
        if (finished) {
            return;
        }
        finished = true;
        active = null;
        Determinism.active = false;
        SubFrameMod.LOG.info("Replay " + why + " after " + frameIndex + " frames");
        if (!preview && !diffMode) {
            try {
                logParticleSummary();
            } catch (Throwable t) {
                SubFrameMod.LOG.warn("Could not write the particle summary: " + t);
            }
        }
        if (rlog != null) {
            rlog.important("render " + why + " after " + frameIndex + " frames");
            rlog.close();
            rlog = null;
        }
        if (sink != null) {
            sink.close();
        }
        if (acc != null) {
            acc.delete();
        }
        deleteRenderTarget();
        if (liveIn != null) {
            try {
                liveIn.close();
            } catch (java.io.IOException ignored) {
            }
        }
        ((MinecraftAccessor) mc).subframe$setTimer(savedTimer);
        GameSettings gs = mc.gameSettings;
        if (overrodeSettings) {
            gs.fovSetting = savedFov;
            gs.renderDistanceChunks = savedRenderDistance;
            gs.viewBobbing = savedBob != 0;
        }
        muteGlErrorChat(false);
        speedUpChunkBuilding(false);
        restoreSession();
        if (savedFboEnable != null) {
            mc.gameSettings.fboEnable = savedFboEnable;
            try {
                mc.getFramebuffer().deleteFramebuffer();
            ((MinecraftAccessor) mc).subframe$resize(
                        Math.max(1, Display.getWidth()), Math.max(1, Display.getHeight()));
            } catch (Throwable t) {
                SubFrameMod.LOG.warn("Could not restore the game framebuffer setting: " + t);
            }
            savedFboEnable = null;
        }
        gs.hideGUI = savedHideGui;
        gs.limitFramerate = savedFramerate;
        gs.enableVsync = savedVsync;
        Display.setVSyncEnabled(savedVsync);
        System.clearProperty("luka.rendering");
        if (customSize) {
            customSize = false;
            mc.gameSettings.guiScale = savedGuiScale;
            try {
                ((MinecraftAccessor) mc).subframe$resize(Math.max(1, Display.getWidth()), Math.max(1, Display.getHeight()));
            } catch (Throwable t) {
                SubFrameMod.LOG.warn("Could not set the game back to the window size: " + t);
            }
        }
        Display.setTitle(savedTitle != null ? savedTitle : "Minecraft 1.7.10");
        if (channel != null) {
            channel.close();
        }
        mc.loadWorld(null);
        if (returnToManager) {
            returnToManager = false;
            mc.displayGuiScreen(new ReplayManagerScreen(new GuiMainMenu()));
        } else {
            mc.displayGuiScreen(new GuiMainMenu());
        }
    }
}
