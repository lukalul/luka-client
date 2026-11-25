package dev.subframe;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import dev.subframe.mixin.NetworkManagerAccessor;
import dev.subframe.mixin.DataWatcherAccessor;
import net.minecraft.util.ChatComponentText;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * One connection. While connected it quietly spools every clientbound play packet to a temporary file (deleted
 * when you leave unless you recorded). Pressing the start key begins a take: the local player's client-only
 * state per tick, the camera per frame, particles and so on. Pressing stop (or leaving) writes the recording:
 * the spooled packets (so the world is complete when the take starts) plus the take.
 */
final class Recorder {
    static final int MAGIC = 0x53465231; // "SFR1"
    static final int VERSION = 16;
    static final int REC_PACKET = 1, REC_TICK = 2, REC_FRAME = 3, REC_JOIN = 4, REC_TICKSTART = 5, REC_PARTICLE = 6, REC_EMITTER = 7;

    static volatile Recorder active;

    private final long t0 = System.nanoTime();
    private final long seed = new java.util.Random().nextLong();
    private final File dir;
    private final File spoolFile;
    private final RecordWriter spool;
    private volatile boolean capturing;
    private RecordWriter take;
    private File takeFile;
    private File finalFile;
    private Thread assembler;

    private volatile int tick;
    private boolean sawWorld;
    private boolean autoStarted;
    private int particleTick = -1, particleSeq;
    private int frameCount;
    private long renderStart;

    // statistics for the recording log
    private volatile int packetsSaved, packetsFailed;
    private int particlesSaved, takeFrames, gapsOver33, gapsOver100;
    private long takeStartNs, lastFrameNs, maxGapNs, takeEndTick, takeStartTick;
    private final StringBuilder takeNotes = new StringBuilder();

    // header values of the take that is being recorded
    private String playerName = "";
    private String playerId = "";
    private float fov;
    private int renderDistance;
    private boolean viewBobbing;

    private interface Body {
        void write(DataOutputStream d) throws IOException;
    }

    private Recorder(File dir, File spoolFile) throws IOException {
        this.dir = dir;
        this.spoolFile = spoolFile;
        this.spool = new RecordWriter(spoolFile);
    }

    /** A connection started: begin spooling packets. Nothing is saved until the player starts a take. */
    static void start(NetworkManager nm) {
        if (active != null || Replay.active != null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        File dir = new File(mc.mcDataDir, "subframe");
        dir.mkdirs();
        try {
            String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            Recorder r = new Recorder(dir, new File(dir, ".spool-" + ts + ".tmp"));
            ((NetworkManagerAccessor) nm).subframe$getChannel().pipeline()
                    .addBefore("packet_handler", "subframe_tap", new PacketTap());
            Determinism.seed = r.seed;
            Determinism.active = true;
            active = r;
            SubFrameMod.LOG.info("SubFrame is listening (not recording). Press the start key to record.");
        } catch (Exception e) {
            SubFrameMod.LOG.error("Could not start the packet spool", e);
        }
    }

    /** The connection ended: finish any take, then delete the spool. */
    static void stop() {
        Recorder r = active;
        active = null;
        if (r == null) {
            return;
        }
        if (r.capturing) {
            r.stopCapture();
        }
        if (r.assembler != null) {
            try {
                r.assembler.join(60000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        r.spool.close();
        if (!r.spoolFile.delete()) {
            r.spoolFile.deleteOnExit();
        }
        if (Replay.active == null) {
            Determinism.active = false;
        }
    }

    static boolean isCapturing() {
        Recorder r = active;
        return r != null && r.capturing;
    }

    /** Start key. */
    static void startKey() {
        Recorder r = active;
        Minecraft mc = Minecraft.getMinecraft();
        if (r == null || mc.theWorld == null || mc.thePlayer == null) {
            chat("Join a world first, then press the start key.");
        } else if (r.capturing) {
            chat("Already recording. Press the stop key to finish.");
        } else {
            r.startCapture();
        }
    }

    /** Stop key. */
    static void stopKey() {
        Recorder r = active;
        if (r == null || !r.capturing) {
            chat("Not recording.");
        } else {
            r.stopCapture();
        }
    }

    static void chat(final String message) {
        final Minecraft mc = Minecraft.getMinecraft();
        Runnable show = new Runnable() {
            public void run() {
                if (mc.thePlayer != null) {
                    mc.thePlayer.addChatMessage(new ChatComponentText("[SubFrame] " + message));
                }
            }
        };
        if (mc.isCallingFromMinecraftThread()) {
            show.run();
        } else {
            mc.addScheduledTask(show);
        }
        SubFrameMod.LOG.info(message);
    }

    /** Begins a take. Must run on the client thread. */
    synchronized void startCapture() {
        if (capturing) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        try {
            String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            finalFile = new File(dir, "rec-" + ts + ".sfr");
            takeFile = new File(dir, ".take-" + ts + ".tmp");
            take = new RecordWriter(takeFile);
            GameSettings gs = mc.gameSettings;
            playerName = mc.getSession().getProfile().getName();
            playerId = mc.getSession().getProfile().getId() == null ? "" : mc.getSession().getProfile().getId().toString();
            fov = gs.fovSetting;
            renderDistance = gs.renderDistanceChunks;
            viewBobbing = gs.viewBobbing;
            frameCount = 0;
            takeFrames = gapsOver33 = gapsOver100 = particlesSaved = 0;
            maxGapNs = lastFrameNs = 0;
            takeStartNs = System.nanoTime();
            takeStartTick = tick;
            takeNotes.setLength(0);
            capturing = true;
            // the replay's first frame needs the player where it is right now
            final EntityPlayerSP p = mc.thePlayer;
            if (p != null) {
                final int n = tick;
                final PlayerState s = PlayerState.capture(p);
                emit(REC_TICK, new Body() {
                    public void write(DataOutputStream d) throws IOException {
                        d.writeInt(n);
                        s.write(d);
                    }
                });
            }
            chat("Recording started.");
        } catch (Exception e) {
            capturing = false;
            SubFrameMod.LOG.error("Could not start recording", e);
            chat("Could not start recording: " + e);
        }
    }

    /** Ends the take and writes the recording in the background. */
    synchronized void stopCapture() {
        if (!capturing) {
            return;
        }
        capturing = false;
        LiveCapture.close();
        writeTakeLog();
        final RecordWriter t = take;
        final File takeF = takeFile;
        final File outF = finalFile;
        take = null;
        t.close();
        chat("Recording stopped. Saving " + outF.getName() + "...");
        assembler = new Thread(new Runnable() {
            public void run() {
                try {
                    spool.flushAndWait();
                    assemble(outF, takeF);
                    chat("Saved " + outF.getName() + ". Watch it from the Replays button on the main menu (or F8 there).");
                } catch (Exception e) {
                    SubFrameMod.LOG.error("Could not save the recording", e);
                    chat("Could not save the recording: " + e);
                } finally {
                    takeF.delete();
                }
            }
        }, "SubFrame-Assemble");
        assembler.start();
    }

    /** Writes subframe/logs/record-<name>.log: how smooth the live game was, and anything that failed to save. */
    private void writeTakeLog() {
        try {
            double secs = (System.nanoTime() - takeStartNs) / 1e9;
            FileLog log = new FileLog(new File(dir, "logs/record-" + finalFile.getName().replaceAll("\\.sfr$", "") + ".log"));
            log.line("SubFrame recording log for " + finalFile.getName());
            log.line(String.format("take: %.1f s, %d frames (%.0f per second), %d ticks (%d to %d)", secs, takeFrames,
                    takeFrames / Math.max(0.001, secs), tick - takeStartTick, takeStartTick, (long) tick));
            log.line(String.format("live frame gaps: longest %.1f ms, %d over 33 ms, %d over 100 ms (your game stalls would show here)",
                    maxGapNs / 1e6, gapsOver33, gapsOver100));
            log.line("packets saved so far this session: " + packetsSaved + ", failed to save: " + packetsFailed
                    + (packetsFailed > 0 ? "  <-- these are missing from the recording" : ""));
            log.line("particles recorded: " + particlesSaved);
            log.line("java " + System.getProperty("java.version") + ", mods: " + net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods().size());
            StringBuilder mods = new StringBuilder();
            for (net.fabricmc.loader.api.ModContainer m : net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods()) {
                mods.append(m.getMetadata().getId()).append(' ')
                        .append(m.getMetadata().getVersion().getFriendlyString()).append("; ");
            }
            log.line(mods.toString());
            if (takeNotes.length() > 0) {
                log.line(takeNotes.toString());
            }
            log.close();
            SubFrameMod.LOG.info(String.format("Take log: %.1f s, %d frames, longest live gap %.1f ms, %d packets failed",
                    secs, takeFrames, maxGapNs / 1e6, packetsFailed));
        } catch (Exception e) {
            SubFrameMod.LOG.error("Could not write the recording log", e);
        }
    }

    /** A packet could not be saved (called by the packet tap). */
    void packetFailed(String what, Throwable t) {
        packetsFailed++;
        if (takeNotes.length() < 4000) {
            takeNotes.append("packet failed: ").append(what).append(" - ").append(t).append("; ");
        }
    }

    private void assemble(File out, File takeF) throws IOException {
        DataOutputStream o = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(new FileOutputStream(out), 1 << 16), 1 << 16));
        try {
            o.writeInt(MAGIC);
            o.writeInt(VERSION);
            o.writeUTF(playerName);
            o.writeUTF(playerId);
            o.writeFloat(fov);
            o.writeInt(renderDistance);
            o.writeBoolean(viewBobbing);
            o.writeLong(seed);
            copyRecords(spoolFile, o);
            copyRecords(takeF, o);
        } finally {
            o.close();
        }
    }

    /** Copies whole records; a file that is still open ends abruptly, which just ends the copy. */
    private static void copyRecords(File f, DataOutputStream out) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(new FileInputStream(f), 1 << 16), 1 << 16));
        try {
            while (true) {
                int len = in.readInt();
                byte[] rec = new byte[len];
                in.readFully(rec);
                out.writeInt(len);
                out.write(rec);
            }
        } catch (IOException end) {
            // end of the readable data
        } finally {
            in.close();
        }
    }

    /** Packets and the join marker describe the world, so they always go to the spool; everything else only during a take. */
    private void emit(int type, Body body) {
        boolean world = type == REC_PACKET || type == REC_JOIN;
        if (!world && !capturing) {
            return;
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(bos);
            d.writeByte(type);
            body.write(d);
            byte[] rec = bos.toByteArray();
            if (world) {
                spool.add(rec);
            } else {
                RecordWriter t = take;
                if (t != null) {
                    t.add(rec);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private long now() {
        return System.nanoTime() - t0;
    }

    /**
     * Writes a decoded packet back to bytes. On a real server, spawn-player and spawn-mob packets arrive with their
     * entity metadata only as a list (the DataWatcher they write from is null), so writing them throws; rebuild the
     * DataWatcher from the list first. Singleplayer packets are built by the server and write fine.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    /**
     * Writing a packet out reads any data buffer it holds (a custom payload keeps its bytes in one), which would leave
     * the packet empty for the game that still has to process it. The read positions are noted and put back.
     */
    private static java.util.List<Object[]> bufferPositions(Packet p) {
        java.util.List<Object[]> out = new java.util.ArrayList<Object[]>();
        for (Class<?> c = p.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) && ByteBuf.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        Object b = f.get(p);
                        if (b instanceof ByteBuf) {
                            out.add(new Object[]{b, ((ByteBuf) b).readerIndex()});
                        }
                    } catch (Throwable ignored) {
                        // not readable: nothing to put back
                    }
                }
            }
        }
        return out;
    }

    static byte[] serialize(Packet p) throws IOException {
        java.util.List<Object[]> positions = bufferPositions(p);
        try {
            return serializeUnguarded(p);
        } finally {
            for (Object[] o : positions) {
                try {
                    ((ByteBuf) o[0]).readerIndex((Integer) o[1]);
                } catch (Throwable ignored) {
                    // buffer already released
                }
            }
        }
    }

    private static byte[] serializeUnguarded(Packet p) throws IOException {
        ByteBuf buf = Unpooled.buffer();
        try {
            try {
                p.writePacketData(new PacketBuffer(buf));
            } catch (NullPointerException missingWatcher) {
                buf.clear();
                java.lang.reflect.Field watcherField = null, listField = null;
                for (java.lang.reflect.Field f : p.getClass().getDeclaredFields()) {
                    if (f.getType() == net.minecraft.entity.DataWatcher.class) {
                        watcherField = f;
                    } else if (f.getType() == java.util.List.class) {
                        listField = f;
                    }
                }
                if (watcherField == null || listField == null) {
                    throw missingWatcher;
                }
                watcherField.setAccessible(true);
                listField.setAccessible(true);
                java.util.List<net.minecraft.entity.DataWatcher.WatchableObject> list =
                        (java.util.List<net.minecraft.entity.DataWatcher.WatchableObject>) listField.get(p);
                net.minecraft.entity.DataWatcher watcher = new net.minecraft.entity.DataWatcher(null);
                java.util.Map<Integer, net.minecraft.entity.DataWatcher.WatchableObject> map =
                        ((DataWatcherAccessor) watcher).subframe$getWatchedObjects();
                if (list != null) {
                    for (net.minecraft.entity.DataWatcher.WatchableObject o : list) {
                        map.put(o.getDataValueId(), o);
                    }
                }
                watcherField.set(p, watcher);
                p.writePacketData(new PacketBuffer(buf));
            }
            byte[] out = new byte[buf.readableBytes()];
            buf.readBytes(out);
            return out;
        } catch (IllegalAccessException e) {
            throw new IOException(e);
        } finally {
            buf.release();
        }
    }

    /** Called on the netty thread, before the packet is processed, so the packet is still unmodified. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    void packet(Packet p) throws IOException {
        final Integer id = EnumConnectionState.PLAY.getPacketId(EnumPacketDirection.CLIENTBOUND, p);
        if (id == null) {
            return;
        }
        final byte[] data = serialize(p);
        packetsSaved++;
        final long t = now();
        final int tk = tick;
        emit(REC_PACKET, new Body() {
            public void write(DataOutputStream d) throws IOException {
                d.writeLong(t);
                d.writeInt(tk);
                d.writeInt(id);
                d.writeInt(data.length);
                d.write(data);
            }
        });
    }

    /** Start of a rendered frame, after this loop iteration's packets and ticks have run. */
    void onRenderStart() {
        renderStart = now();
    }

    int currentTick() {
        return tick;
    }

    /** A particle emitter (crit sparks etc.) was created, by a packet or by the player's own hit. */
    void emitter(final int tk, final int entityId, final int typeId) {
        emit(REC_EMITTER, new Body() {
            public void write(DataOutputStream d) throws IOException {
                d.writeInt(tk);
                d.writeInt(entityId);
                d.writeInt(typeId);
            }
        });
    }

    /** Called for every particle the client creates: seeds its own Random and records its random-derived fields. */
    void particle(net.minecraft.client.particle.EntityFX fx) throws IOException {
        final int tk = tick;
        if (tk != particleTick) {
            particleTick = tk;
            particleSeq = 0;
        }
        final long pseed = Determinism.mix(Determinism.mix(seed, tk), particleSeq++);
        ParticleState.reseed(fx, pseed);
        if (!capturing) {
            return;
        }
        particlesSaved++;
        final int kx = ParticleState.quantise(fx.posX), ky = ParticleState.quantise(fx.posY), kz = ParticleState.quantise(fx.posZ);
        final String cls = fx.getClass().getName();
        ByteArrayOutputStream fields = new ByteArrayOutputStream();
        ParticleState.write(new DataOutputStream(fields), fx);
        final byte[] payload = fields.toByteArray();
        emit(REC_PARTICLE, new Body() {
            public void write(DataOutputStream d) throws IOException {
                d.writeInt(tk);
                d.writeUTF(cls);
                d.writeInt(kx);
                d.writeInt(ky);
                d.writeInt(kz);
                d.writeLong(pseed);
                d.write(payload);
            }
        });
    }

    /**
     * Start of a client tick. The live loop processes queued packets right before running ticks, so a packet that
     * arrived before this instant was applied before this tick and one that arrived after it was applied after.
     */
    void onTickStart() {
        if (!capturing) {
            return;
        }
        final int n = tick + 1;
        final long t = now();
        emit(REC_TICKSTART, new Body() {
            public void write(DataOutputStream d) throws IOException {
                d.writeInt(n);
                d.writeLong(t);
            }
        });
    }

    /** Window id of the container (chest, furnace, ...) open in the live game; 0 = none. */
    private int liveWindow;
    /** The player's own inventory screen is open in the live game. */
    private boolean liveInventory;

    /**
     * Closing a chest and opening or closing your own inventory happen in the client only: the server sends no packet
     * for them, so a replay could not tell. They are written into the recorded packets: a close-window packet for the
     * container, and an empty custom-payload packet on a SubFrame channel (Replay.INVENTORY_OPEN / _CLOSED) for the
     * inventory, which the game ignores, so older versions play these recordings as before.
     */
    private void trackScreens(Minecraft mc, net.minecraft.entity.player.EntityPlayer p) {
        try {
            int window = p.openContainer != null && p.openContainer != p.inventoryContainer ? p.openContainer.windowId : 0;
            if (liveWindow != 0 && window != liveWindow) {
                packet(new net.minecraft.network.play.server.S2EPacketCloseWindow(liveWindow));
            }
            liveWindow = window;
            boolean inventory = mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiInventory
                    || mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainerCreative;
            if (inventory != liveInventory) {
                liveInventory = inventory;
                String channel = inventory ? Replay.INVENTORY_OPEN : Replay.INVENTORY_CLOSED;
                packet(new net.minecraft.network.play.server.S3FPacketCustomPayload(channel, new PacketBuffer(Unpooled.buffer(0))));
            }
        } catch (Throwable t) {
            SubFrameMod.LOG.error("Recording a screen change failed", t);
        }
    }

    /** End of a client tick: the state after tick n is stored as snapshot n. */
    void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null) {
            // singleplayer pauses the world while the loading-terrain screen is open; those ticks do not count
            if (!sawWorld && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiDownloadTerrain)) {
                // ticks completed before the world existed: the live game ran no world ticks during them
                final int joinTick = tick;
                emit(REC_JOIN, new Body() {
                    public void write(DataOutputStream d) throws IOException {
                        d.writeInt(joinTick);
                    }
                });
            }
            if (!(mc.currentScreen instanceof net.minecraft.client.gui.GuiDownloadTerrain)) {
                sawWorld = true;
            }
        } else if (sawWorld) {
            // the disconnect event does not reach us on every exit path (e.g. leaving a singleplayer world)
            stop();
            return;
        }
        final EntityPlayerSP p = mc.thePlayer;
        final int n = ++tick;
        if (SubFrameMod.autoRecord && !autoStarted && sawWorld && p != null) {
            autoStarted = true;
            startCapture();
        }
        if (p != null) {
            trackScreens(mc, p);
        }
        if (p == null || !capturing) {
            return;
        }
        final PlayerState s = PlayerState.capture(p);
        emit(REC_TICK, new Body() {
            public void write(DataOutputStream d) throws IOException {
                d.writeInt(n);
                s.write(d);
            }
        });
    }

    /** End of a rendered frame: the camera angles are applied by the mouse during the frame, so read them now. */
    void onFrame(float partial) {
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        if (p == null || !capturing) {
            return;
        }
        long nowNs = System.nanoTime();
        if (lastFrameNs != 0) {
            long gap = nowNs - lastFrameNs;
            maxGapNs = Math.max(maxGapNs, gap);
            if (gap > 33_000_000L) {
                gapsOver33++;
            }
            if (gap > 100_000_000L) {
                gapsOver100++;
            }
        }
        lastFrameNs = nowNs;
        takeFrames++;
        final long t = now();
        final double g = tick + partial;
        final float yaw = p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * partial;
        final float pitch = p.prevRotationPitch + (p.rotationPitch - p.prevRotationPitch) * partial;
        // the vignette adapts a little on every rendered frame, so its value depends on frame count, not time
        final float vignette = Minecraft.getMinecraft().ingameGUI.prevVignetteBrightness;
        final double[] probe = Probe.capture(partial);
        final long start = renderStart;
        emit(REC_FRAME, new Body() {
            public void write(DataOutputStream d) throws IOException {
                d.writeLong(t);
                d.writeDouble(g);
                d.writeFloat(yaw);
                d.writeFloat(pitch);
                d.writeFloat(vignette);
                d.writeLong(start);
                for (double x : probe) {
                    d.writeDouble(x);
                }
            }
        });
        int index = frameCount++;
        if (SubFrameMod.captureLive) {
            LiveCapture.capture(finalFile, index);
        }
    }
}
