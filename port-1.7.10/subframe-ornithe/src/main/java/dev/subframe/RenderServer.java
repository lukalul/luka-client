package dev.subframe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Properties;

/**
 * A game the luka replay editor keeps running for all its previews and renders: {@code -Dsubframe.renderserver=<dir>}.
 * Starting the game is the slow part of a render (20 to 40 s); this way it happens once per editor session.
 *
 * <p>The editor drops render jobs (the same {@code key=value} files as {@code -Dsubframe.renderjob}) into the folder as
 * {@code <name>.job}. The game takes the oldest one when it sits at the main menu, renames it {@code .running}, renders
 * it (progress in the job's status file, as for a single job) and renames it {@code .done}. Then every setting the job
 * changed is put back, so the next job starts from the player's own settings.
 *
 * <p>{@code server.properties} in the folder says {@code state=starting|idle|busy} and the running job. The game quits
 * when a {@code quit} file appears, or when {@code alive} (touched by the editor every few seconds) gets older than
 * {@link #ALIVE_MS}: an editor that crashed or was killed never leaves a game behind.
 */
final class RenderServer {
    static final String PROPERTY = "subframe.renderserver";
    /** The editor touches the alive file every 2 s; a game that has heard nothing for this long quits. */
    static final long ALIVE_MS = 30_000L;

    private static RenderServer server;

    private final File dir;
    private RenderJob current;
    private File currentFile;
    private boolean ready, quitting;
    private long lastCheck, lastScan;
    private int jobsDone;

    // the settings each job may change, as the game had them when the server started
    private int outputFps, subframes, maxSeconds, renderWidth, renderHeight;
    private boolean exportVideo, blend, outputPng, showContainers;
    private float fov, gamma;
    private int renderDistance, particles;
    private boolean bobbing;

    private RenderServer(File dir) {
        this.dir = dir;
    }

    static boolean active() {
        return System.getProperty(PROPERTY) != null;
    }

    /** Client tick end. */
    static void tick() {
        String path = System.getProperty(PROPERTY);
        if (path == null) {
            return;
        }
        if (server == null) {
            server = new RenderServer(new File(path));
            server.dir.mkdirs();
            server.writeState("starting", null);
        }
        server.step();
    }

    private void step() {
        if (quitting) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.currentTimeMillis();
        if (now - lastCheck > 1000) {
            lastCheck = now;
            if (new File(dir, "quit").isFile()) {
                quit("the editor asked");
                return;
            }
            File alive = new File(dir, "alive");
            if (ready && (!alive.isFile() || now - alive.lastModified() > ALIVE_MS)) {
                quit("the editor is gone (no heartbeat for " + ALIVE_MS / 1000 + " s)");
                return;
            }
        }
        if (current != null) {
            current.step();
            if (!current.finished()) {
                return;
            }
            finishJob();
        }
        // a new job starts from the main menu, like the game's first one
        if (mc.theWorld != null || mc.currentScreen == null || Replay.active != null) {
            return;
        }
        if (!ready) {
            snapshot(mc);
            ready = true;
            writeState("idle", null);
            SubFrameMod.LOG.info("[server] ready for render jobs in " + dir);
        }
        // look for new jobs a few times a second, not every tick
        if (now - lastScan < 250) {
            return;
        }
        lastScan = now;
        File next = nextJob();
        if (next == null) {
            return;
        }
        File running = new File(dir, next.getName().replaceAll("\\.job$", ".running"));
        running.delete();
        if (!next.renameTo(running)) {
            SubFrameMod.LOG.warn("[server] could not take " + next);
            return;
        }
        restore(mc);
        currentFile = running;
        writeState("busy", running.getName());
        SubFrameMod.LOG.info("[server] job " + (jobsDone + 1) + ": " + running.getName());
        current = RenderJob.forServer(running);
    }

    private void finishJob() {
        Minecraft mc = Minecraft.getMinecraft();
        jobsDone++;
        File doneFile = new File(dir, currentFile.getName().replaceAll("\\.running$", ".done"));
        doneFile.delete();
        currentFile.renameTo(doneFile);
        Runtime rt = Runtime.getRuntime();
        // heap including garbage not collected yet (the game's JVM options may ignore System.gc): a rough number only
        SubFrameMod.LOG.info("[server] job " + jobsDone + " finished: " + current.state() + "; heap " + ((rt.totalMemory() - rt.freeMemory()) >> 20)
                + " MB used of " + (rt.maxMemory() >> 20) + ", threads " + Thread.activeCount());
        current = null;
        currentFile = null;
        restore(mc);
        writeState("idle", null);
    }

    /** The oldest waiting job, or null. */
    private File nextJob() {
        File[] jobs = dir.listFiles((d, name) -> name.endsWith(".job"));
        if (jobs == null || jobs.length == 0) {
            return null;
        }
        Arrays.sort(jobs, (a, b) -> a.getName().compareTo(b.getName()));
        return jobs[0];
    }

    private void snapshot(Minecraft mc) {
        outputFps = SubFrameMod.outputFps;
        subframes = SubFrameMod.subframes;
        maxSeconds = SubFrameMod.maxSeconds;
        renderWidth = SubFrameMod.renderWidth;
        renderHeight = SubFrameMod.renderHeight;
        exportVideo = SubFrameMod.exportVideo;
        blend = SubFrameMod.blend;
        outputPng = SubFrameMod.outputPng;
        showContainers = SubFrameMod.showContainers;
        GameSettings gs = mc.gameSettings;
        fov = gs.fovSetting;
        gamma = gs.gammaSetting;
        renderDistance = gs.renderDistanceChunks;
        particles = gs.particleSetting;
        bobbing = gs.viewBobbing;
        RenderLook.snapshotHud();
    }

    /** Back to the settings the server started with (what the previous job changed). */
    private void restore(Minecraft mc) {
        if (!ready) {
            return;
        }
        SubFrameMod.outputFps = outputFps;
        SubFrameMod.subframes = subframes;
        SubFrameMod.maxSeconds = maxSeconds;
        SubFrameMod.renderWidth = renderWidth;
        SubFrameMod.renderHeight = renderHeight;
        SubFrameMod.exportVideo = exportVideo;
        SubFrameMod.blend = blend;
        SubFrameMod.outputPng = outputPng;
        SubFrameMod.showContainers = showContainers;
        GameSettings gs = mc.gameSettings;
        gs.fovSetting = fov;
        gs.gammaSetting = gamma;
        gs.renderDistanceChunks = renderDistance;
        gs.particleSetting = particles;
        gs.viewBobbing = bobbing;
        RenderLook.reset();
        RenderLook.restoreHud();
        // the resource pack stays as the last job left it: the next job switches only if it wants another one
    }

    private void quit(String why) {
        quitting = true;
        SubFrameMod.LOG.info("[server] quitting after " + jobsDone + " job(s): " + why);
        writeState("stopped", null);
        new File(dir, "quit").delete();
        Minecraft.getMinecraft().shutdown();
    }

    private void writeState(String state, String job) {
        Properties s = new Properties();
        s.setProperty("state", state);
        s.setProperty("jobs", Integer.toString(jobsDone));
        if (job != null) {
            s.setProperty("job", job);
        }
        try {
            File tmp = new File(dir, "server.properties.tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            try {
                s.store(new OutputStreamWriter(out, "UTF-8"), null);
            } finally {
                out.close();
            }
            File target = new File(dir, "server.properties");
            target.delete();
            tmp.renameTo(target);
        } catch (Throwable t) {
            SubFrameMod.LOG.warn("[server] could not write the server state: " + t);
        }
    }
}
