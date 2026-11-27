package dev.subframe;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * A list of clips rendered one after another without anyone at the game, for the luka replay editor:
 * {@code -Dsubframe.renderjob=<job.properties>}. Each clip (a recording, a start and an end in seconds from the
 * recording's start) becomes its own video; then ffmpeg joins them into the job's output and the game quits.
 * Progress goes to the status file the editor reads ({@code key=value} lines, replaced atomically).
 *
 * <pre>
 * output=C:/.../My edit.mp4      status=C:/.../status.properties
 * fps=60  blend=true  subframes=16  width=1920  height=1080      (optional; left out = the player's settings)
 * intensity=1.0  weighting=ascending                              (optional; frame blending, see BlendPlan)
 * clips=2
 * clip.0.file=C:/.../rec-1.sfr  clip.0.start=1.5  clip.0.end=8.25
 * clip.1.file=C:/.../rec-2.sfr  clip.1.start=0    clip.1.end=4
 * </pre>
 * With {@code preview=true} each clip becomes a folder of PNG frames instead ({@code clip.N.out}, a name ending in .mp4:
 * the frames go to the same name with -frames instead of .mp4, numbered from 000001.png), and nothing is joined: the
 * editor's picture strips. No {@code output} is needed then.
 */
final class RenderJob {
    static final String PROPERTY = "subframe.renderjob";
    private static RenderJob job;

    private final File jobFile;
    private final Properties p = new Properties();
    private final List<File> segments = new ArrayList<File>();
    private File output, status, work;
    private int clips, next, menuTicks;
    private boolean running, done, preview;
    private long lastStatus;
    /** One job of a RenderServer: when it ends, the game stays for the next one instead of quitting. */
    private final boolean serverMode;
    private String lastState = "starting";

    private RenderJob(File jobFile, boolean serverMode) {
        this.jobFile = jobFile;
        this.serverMode = serverMode;
    }

    /** A job the render server took from its folder; step() it every tick until finished(). */
    static RenderJob forServer(File jobFile) {
        RenderJob j = new RenderJob(jobFile, true);
        j.load();
        return j;
    }

    boolean finished() {
        return done;
    }

    /** The last state written to the status file (done, error, ...). */
    String state() {
        return lastState;
    }

    /** The job is over: a single job's game quits, a render server's game waits for the next job. */
    private void endGame() {
        if (!serverMode) {
            Minecraft.getMinecraft().shutdown();
        }
    }

    /** Client tick end: start the next clip once the game sits at the main menu with no replay running. */
    static void tick() {
        String path = System.getProperty(PROPERTY);
        if (path == null) {
            return;
        }
        if (job == null) {
            job = new RenderJob(new File(path), false);
            job.load();
        }
        job.step();
    }

    /** This game renders for the replay editor: one job, or a render server that takes job after job. */
    static boolean active() {
        return System.getProperty(PROPERTY) != null || RenderServer.active();
    }

    private void load() {
        try {
            InputStream in = new FileInputStream(jobFile);
            try {
                p.load(new java.io.InputStreamReader(in, "UTF-8"));
            } finally {
                in.close();
            }
            preview = Boolean.parseBoolean(p.getProperty("preview", "false").trim());
            status = new File(p.getProperty("status", jobFile.getPath() + ".status"));
            clips = Integer.parseInt(p.getProperty("clips", "0").trim());
            if (clips < 1) {
                throw new IllegalArgumentException("the job has no clips");
            }
            if (!preview) {
                output = new File(p.getProperty("output"));
                work = new File(output.getParentFile(), "." + output.getName() + ".parts");
                work.mkdirs();
            }
            SubFrameMod.outputPng = preview;
            SubFrameMod.exportVideo = true;
            SubFrameMod.maxSeconds = 0;
            SubFrameMod.outputFps = intOr("fps", SubFrameMod.outputFps, 1, 1_000_000);
            SubFrameMod.subframes = intOr("subframes", SubFrameMod.subframes, 1, 4096);
            if (p.getProperty("blend") != null) {
                SubFrameMod.blend = Boolean.parseBoolean(p.getProperty("blend").trim());
            }
            if (p.getProperty("intensity") != null) {
                try {
                    SubFrameMod.shutter = BlendPlan.clampIntensity(Double.parseDouble(p.getProperty("intensity").trim()));
                } catch (NumberFormatException ignored) {
                    // keep the player's intensity
                }
            }
            if (p.getProperty("weighting") != null) {
                SubFrameMod.weighting = BlendPlan.normalizeName(p.getProperty("weighting"));
            }
            if (p.getProperty("width") != null && p.getProperty("height") != null) {
                SubFrameMod.renderWidth = intOr("width", 0, 0, ReplayMenuScreen.MAX_W) / 2 * 2;
                SubFrameMod.renderHeight = intOr("height", 0, 0, ReplayMenuScreen.MAX_H) / 2 * 2;
            }
            if (p.getProperty("showContainers") != null) {
                SubFrameMod.showContainers = Boolean.parseBoolean(p.getProperty("showContainers").trim());
            }
            // the editor's look options (HUD, hidden things, FOV, time, ...), for this render only
            RenderLook.load(p);
            RenderLook.applyHud();
            RenderLook.applyResourcePack(Minecraft.getMinecraft());
            SubFrameMod.LOG.info("[job] " + clips + " clip(s) to " + (preview ? "preview frames" : output) + " at " + SubFrameMod.outputFps + " fps");
            write("starting", null);
        } catch (Throwable t) {
            SubFrameMod.LOG.error("[job] could not read " + jobFile, t);
            fail("Could not read the render job: " + t.getMessage());
        }
    }

    private int intOr(String key, int fallback, int min, int max) {
        String v = p.getProperty(key);
        if (v == null) {
            return fallback;
        }
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(v.trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    void step() {
        if (done) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        Replay r = Replay.active;
        if (running) {
            if (r != null) {
                if (System.currentTimeMillis() - lastStatus > 500) {
                    write("rendering", null);
                }
                return;
            }
            running = false;
            String why = Replay.lastFinish;
            File seg = segments.get(segments.size() - 1);
            boolean made = preview ? pngCount(framesDir(seg)) > 0 : seg.isFile() && seg.length() > 0;
            if (why == null || !why.startsWith("finished") || !made) {
                fail("Clip " + next + " did not render (" + why + "). See the render log in the recording's logs folder.");
                return;
            }
            menuTicks = 0;
        }
        if (mc.theWorld != null || mc.currentScreen == null) {
            return;
        }
        // let the menu settle (luka's menu, resource reloads) before joining the replay world
        if (++menuTicks < 40) {
            return;
        }
        if (next < clips) {
            startClip(next++);
            return;
        }
        if (preview) {
            done = true;
            SubFrameMod.LOG.info("[job] previews done");
            write("done", null);
            endGame();
            return;
        }
        join();
    }

    private void startClip(int i) {
        String file = p.getProperty("clip." + i + ".file");
        double start = dbl("clip." + i + ".start", 0), end = dbl("clip." + i + ".end", -1);
        File seg = preview ? new File(p.getProperty("clip." + i + ".out", file + ".preview.mp4"))
                : new File(work, String.format(Locale.ROOT, "clip-%03d.mp4", i));
        if (preview) {
            seg.getParentFile().mkdirs();
            deleteTree(framesDir(seg));
        }
        seg.delete();
        segments.add(seg);
        SubFrameMod.LOG.info("[job] clip " + (i + 1) + "/" + clips + ": " + file + " " + start + "-" + end + " s, speed "
                + dbl("clip." + i + ".speed", 1));
        Replay.lastFinish = null;
        double speed = Math.max(0.05, Math.min(20, dbl("clip." + i + ".speed", 1)));
        if (file == null || !Replay.startJob(new File(file), start, end, seg, speed)) {
            fail("Clip " + (i + 1) + " could not start (" + file + ").");
            return;
        }
        running = true;
        write("rendering", null);
    }

    private double dbl(String key, double fallback) {
        try {
            return Double.parseDouble(p.getProperty(key, "").trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Joins the clip videos without re-encoding (they share size, frame rate and encoder). */
    private void join() {
        done = true;
        try {
            write("joining", null);
            output.delete();
            if (segments.size() == 1) {
                if (!segments.get(0).renameTo(output)) {
                    throw new java.io.IOException("could not move " + segments.get(0) + " to " + output);
                }
            } else {
                File list = new File(work, "list.txt");
                Writer w = new OutputStreamWriter(new FileOutputStream(list), "UTF-8");
                try {
                    for (File s : segments) {
                        w.write("file '" + s.getAbsolutePath().replace("\\", "/").replace("'", "'\\''") + "'\n");
                    }
                } finally {
                    w.close();
                }
                ProcessBuilder pb = new ProcessBuilder(SubFrameMod.ffmpegPath, "-hide_banner", "-loglevel", "error", "-y",
                        "-f", "concat", "-safe", "0", "-i", list.getAbsolutePath(), "-c", "copy", "-movflags", "+faststart",
                        output.getAbsolutePath()).redirectErrorStream(true).redirectOutput(new File(work, "join.log"));
                int code = pb.start().waitFor();
                if (code != 0 || !output.isFile() || output.length() == 0) {
                    throw new java.io.IOException("ffmpeg could not join the clips (exit " + code + ", see " + new File(work, "join.log") + ")");
                }
            }
            // the clips, the list and the clips' render logs (written beside each clip, in logs/)
            deleteTree(work);
            SubFrameMod.LOG.info("[job] done: " + output);
            write("done", null);
        } catch (Throwable t) {
            SubFrameMod.LOG.error("[job] joining failed", t);
            write("error", t.getMessage());
        }
        endGame();
    }

    /** Where Replay puts the PNG frames of a render to this video name. */
    static File framesDir(File video) {
        return new File(video.getParentFile(), video.getName().replaceAll("\\.mp4$", "") + "-frames");
    }

    private static int pngCount(File dir) {
        String[] names = dir.list();
        int n = 0;
        if (names != null) {
            for (String name : names) {
                if (name.endsWith(".png")) {
                    n++;
                }
            }
        }
        return n;
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteTree(k);
            }
        }
        f.delete();
    }

    private void fail(String message) {
        done = true;
        write("error", message);
        endGame();
    }

    private void write(String state, String message) {
        lastStatus = System.currentTimeMillis();
        lastState = message == null ? state : state + ": " + message;
        if (status == null) {
            return;
        }
        Properties s = new Properties();
        s.setProperty("state", state);
        s.setProperty("clip", Integer.toString(Math.max(1, next)));
        s.setProperty("clips", Integer.toString(clips));
        Replay r = Replay.active;
        s.setProperty("progress", String.format(Locale.ROOT, "%.4f", r != null ? r.jobProgress() : "done".equals(state) ? 1.0 : 0.0));
        if (output != null) {
            s.setProperty("output", output.getAbsolutePath());
        }
        if (message != null) {
            s.setProperty("message", message);
        }
        try {
            File tmp = new File(status.getPath() + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            try {
                s.store(new OutputStreamWriter(out, "UTF-8"), null);
            } finally {
                out.close();
            }
            status.delete();
            tmp.renameTo(status);
        } catch (Throwable t) {
            SubFrameMod.LOG.warn("[job] could not write " + status + ": " + t);
        }
    }
}
