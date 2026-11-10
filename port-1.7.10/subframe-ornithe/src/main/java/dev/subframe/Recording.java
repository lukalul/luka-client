package dev.subframe;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

/** A recording loaded fully into memory for offline playback. */
final class Recording {
    static final class Pkt {
        long t;
        int tick;
        int id;
        byte[] data;
    }

    String playerName;
    UUID playerId;
    float fov;
    int renderDistance;
    boolean viewBobbing;
    /** Client ticks completed when the world first existed; replay world ticks start counting here. */
    int joinTick;
    long seed;
    /** Every particle class in the recording, and those that hold a reference that cannot be recorded. */
    final java.util.HashSet<String> classSeen = new java.util.HashSet<String>();
    final java.util.HashSet<String> nonInjectable = new java.util.HashSet<String>();
    /** Particle bucket keys ("tick|class") by tick, to find the unclaimed ones once a tick is safely past. */
    final java.util.TreeMap<Integer, java.util.HashSet<String>> keysByTick =
            new java.util.TreeMap<Integer, java.util.HashSet<String>>();
    /** Emitter calls by tick: {entityId, particleTypeId, consumed}. */
    final java.util.HashMap<Integer, java.util.List<int[]>> emitters = new java.util.HashMap<Integer, java.util.List<int[]>>();
    /** Recorded particles by tick, class and quantised position; several can share a key. */
    final java.util.HashMap<String, java.util.ArrayList<ParticleState.Rec>> particles =
            new java.util.HashMap<String, java.util.ArrayList<ParticleState.Rec>>();

    final List<Pkt> packets = new ArrayList<Pkt>();
    final TreeMap<Integer, PlayerState> ticks = new TreeMap<Integer, PlayerState>();
    /** Wall-clock time at which each client tick started in the live game. */
    final java.util.HashMap<Integer, Long> tickStart = new java.util.HashMap<Integer, Long>();

    long[] frameT = new long[0];
    long[] frameStart = new long[0];
    /** Instant at which the live loop had applied everything for this frame: first tick start, else frame start. */
    long[] frameApplied = new long[0];
    double[] frameG = new double[0];
    float[] frameYaw = new float[0];
    float[] framePitch = new float[0];
    float[] frameVignette = new float[0];
    double[] frameProbe = new double[0];
    int frameCount;

    static Recording load(File f) throws IOException {
        Recording r = new Recording();
        DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(new FileInputStream(f), 1 << 16), 1 << 16));
        try {
            int magic = in.readInt();
            if (magic == Recorder.MAGIC_1_8_9) {
                throw new IOException("Recorded on Minecraft 1.8.9, which this build cannot read: " + f);
            }
            if (magic != Recorder.MAGIC) {
                throw new IOException("Not a SubFrame recording: " + f);
            }
            int version = in.readInt();
            if (version != Recorder.VERSION) {
                throw new IOException("Unsupported recording version " + version);
            }
            r.playerName = in.readUTF();
            String uuid = in.readUTF();
            r.playerId = uuid.isEmpty() ? null : UUID.fromString(uuid);
            r.fov = in.readFloat();
            r.renderDistance = in.readInt();
            r.viewBobbing = in.readBoolean();
            r.seed = in.readLong();
            r.readRecords(in);
        } finally {
            in.close();
        }
        r.computeApplied();
        if (r.frameCount < 2) {
            throw new IOException("Recording has no rendered frames");
        }
        return r;
    }

    private void readRecords(DataInputStream in) throws IOException {
        try {
            while (true) {
                int len = in.readInt();
                byte[] rec = new byte[len];
                in.readFully(rec);
                DataInputStream d = new DataInputStream(new java.io.ByteArrayInputStream(rec));
                switch (d.readByte()) {
                    case Recorder.REC_PACKET: {
                        Pkt p = new Pkt();
                        p.t = d.readLong();
                        p.tick = d.readInt();
                        p.id = d.readInt();
                        p.data = new byte[d.readInt()];
                        d.readFully(p.data);
                        packets.add(p);
                        break;
                    }
                    case Recorder.REC_TICK: {
                        int n = d.readInt();
                        ticks.put(n, PlayerState.read(d));
                        break;
                    }
                    case Recorder.REC_FRAME: {
                        addFrame(d.readLong(), d.readDouble(), d.readFloat(), d.readFloat(), d.readFloat());
                        frameStart[frameCount - 1] = d.readLong();
                        for (int i = 0; i < Probe.SIZE; i++) {
                            frameProbe[(frameCount - 1) * Probe.SIZE + i] = d.readDouble();
                        }
                        break;
                    }
                    case Recorder.REC_TICKSTART: {
                        int n = d.readInt();
                        tickStart.put(n, d.readLong());
                        break;
                    }
                    case Recorder.REC_PARTICLE: {
                        int tk = d.readInt();
                        String cls = d.readUTF();
                        String key = tk + "|" + cls;
                        int qx = d.readInt(), qy = d.readInt(), qz = d.readInt();
                        long pseed = d.readLong();
                        java.util.ArrayList<ParticleState.Rec> q = particles.get(key);
                        if (q == null) {
                            q = new java.util.ArrayList<ParticleState.Rec>();
                            particles.put(key, q);
                        }
                        ParticleState.Rec pr = ParticleState.read(d, pseed);
                        pr.cls = cls;
                        pr.tick = tk;
                        classSeen.add(cls);
                        if (!pr.injectable) {
                            nonInjectable.add(cls);
                        }
                        java.util.HashSet<String> ks = keysByTick.get(tk);
                        if (ks == null) {
                            ks = new java.util.HashSet<String>();
                            keysByTick.put(tk, ks);
                        }
                        ks.add(key);
                        pr.x = qx / 1024.0;
                        pr.y = qy / 1024.0;
                        pr.z = qz / 1024.0;
                        q.add(pr);
                        break;
                    }
                    case Recorder.REC_EMITTER: {
                        int tk = d.readInt();
                        java.util.List<int[]> l = emitters.get(tk);
                        if (l == null) {
                            l = new java.util.ArrayList<int[]>();
                            emitters.put(tk, l);
                        }
                        l.add(new int[]{d.readInt(), d.readInt(), 0});
                        break;
                    }
                    case Recorder.REC_JOIN:
                        joinTick = d.readInt();
                        break;
                    default:
                        break;
                }
            }
        } catch (EOFException end) {
            // normal end of stream
        } catch (ZipException truncated) {
            SubFrameMod.LOG.warn("Recording ends abruptly: " + truncated.getMessage());
        }
    }

    private void addFrame(long t, double g, float yaw, float pitch, float vignette) {
        if (frameCount == frameT.length) {
            int n = Math.max(1024, frameCount * 2);
            frameT = java.util.Arrays.copyOf(frameT, n);
            frameG = java.util.Arrays.copyOf(frameG, n);
            frameYaw = java.util.Arrays.copyOf(frameYaw, n);
            framePitch = java.util.Arrays.copyOf(framePitch, n);
            frameVignette = java.util.Arrays.copyOf(frameVignette, n);
            frameStart = java.util.Arrays.copyOf(frameStart, n);
            frameProbe = java.util.Arrays.copyOf(frameProbe, n * Probe.SIZE);
        }
        frameVignette[frameCount] = vignette;
        frameT[frameCount] = t;
        frameG[frameCount] = g;
        frameYaw[frameCount] = yaw;
        framePitch[frameCount] = pitch;
        frameCount++;
    }

    /**
     * The live loop applies queued packets at the start of an iteration, then runs ticks, then renders. So the
     * moment a frame's world state was settled is the start of its first tick, or its render start if no tick ran.
     */
    private void computeApplied() {
        frameApplied = new long[frameCount];
        for (int k = 0; k < frameCount; k++) {
            long applied = frameStart[k];
            if (k > 0) {
                int firstTick = (int) Math.floor(frameG[k - 1]) + 1;
                if (Math.floor(frameG[k]) >= firstTick) {
                    long s = tickStartTime(firstTick);
                    if (s >= 0 && s < applied) {
                        applied = s;
                    }
                }
            }
            if (k > 0 && applied < frameApplied[k - 1]) {
                applied = frameApplied[k - 1];
            }
            frameApplied[k] = applied;
        }
    }

    long startTime() {
        return frameT[0];
    }

    long endTime() {
        return frameT[frameCount - 1];
    }

    /** Index i such that frameT[i] <= t < frameT[i+1], clamped. */
    private int segment(long t) {
        int lo = 0, hi = frameCount - 2;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (frameT[mid] <= t) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    private double frac(int i, long t) {
        double span = frameT[i + 1] - frameT[i];
        if (span <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, (t - frameT[i]) / span));
    }

    /** Continuous game time (ticks completed + partial tick) at wall-clock time t. */
    double gameTimeAt(long t) {
        int i = segment(t);
        double f = frac(i, t);
        return frameG[i] + (frameG[i + 1] - frameG[i]) * f;
    }

    /**
     * Packets the live game had processed when it drew the frame at time t. The live loop processes
     * packets at the start of an iteration, i.e. roughly when the previous frame ended, so the cutoff
     * is the last recorded frame strictly before t.
     */
    long packetCutoff(long t) {
        int lo = 0, hi = frameCount - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (frameApplied[mid] <= t) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return frameApplied[lo];
    }

    /** Vignette brightness going into the live frame drawn at t: the value the previous frame ended with. */
    float vignetteAt(long t) {
        return frameVignette[segment(t - 1)];
    }

    /** Packets processed before tick n ran: those that arrived before it started. -1 if unknown. */
    long tickStartTime(int n) {
        Long t = tickStart.get(n);
        return t == null ? -1 : t;
    }

    /** Removes and returns the recorded particles up to this tick that no replayed spawn claimed. */
    java.util.List<ParticleState.Rec> dueParticles(int upToTick) {
        java.util.List<ParticleState.Rec> out = new java.util.ArrayList<ParticleState.Rec>();
        java.util.Iterator<java.util.Map.Entry<Integer, java.util.HashSet<String>>> it =
                keysByTick.headMap(upToTick, true).entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<Integer, java.util.HashSet<String>> e = it.next();
            for (String key : e.getValue()) {
                java.util.ArrayList<ParticleState.Rec> q = particles.remove(key);
                if (q != null) {
                    out.addAll(q);
                }
            }
            it.remove();
        }
        return out;
    }

    /** The next recorded particle for this key, or null. */
    ParticleState.Rec takeParticle(int tick, String cls, double x, double y, double z, double maxDist) {
        java.util.ArrayList<ParticleState.Rec> q = particles.get(tick + "|" + cls);
        if (q == null) {
            return null;
        }
        int best = -1;
        double bestD = maxDist * maxDist;
        for (int i = 0; i < q.size(); i++) {
            ParticleState.Rec r = q.get(i);
            double d = (r.x - x) * (r.x - x) + (r.y - y) * (r.y - y) + (r.z - z) * (r.z - z);
            if (d <= bestD) {
                bestD = d;
                best = i;
            }
        }
        return best < 0 ? null : q.remove(best);
    }

    float yawAt(long t) {
        int i = segment(t);
        return (float) (frameYaw[i] + (frameYaw[i + 1] - frameYaw[i]) * frac(i, t));
    }

    float pitchAt(long t) {
        int i = segment(t);
        return (float) (framePitch[i] + (framePitch[i + 1] - framePitch[i]) * frac(i, t));
    }

    PlayerState stateAt(int tick) {
        java.util.Map.Entry<Integer, PlayerState> e = ticks.floorEntry(tick);
        return e == null ? null : e.getValue();
    }
}
