package dev.subframe;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Reads the bound framebuffer back and pipes raw RGB frames into an ffmpeg process. The pipe is written by its own
 * thread through a small queue, so the game keeps rendering the next frames while ffmpeg is still encoding.
 */
final class FfmpegSink implements FrameSink {
    private static final byte[] END = new byte[0];
    private static final int SLOTS = 6;

    private final int w, h;
    private final Process proc;
    private final OutputStream out;
    private final ByteBuffer buf;
    private final ArrayBlockingQueue<byte[]> free = new ArrayBlockingQueue<byte[]>(SLOTS);
    private final ArrayBlockingQueue<byte[]> full = new ArrayBlockingQueue<byte[]>(SLOTS + 1);
    private final Thread writer;
    private volatile IOException failure;
    private long lastHash;
    private int lastGlError;
    private long lastWaitNs;

    // asynchronous readback: two pixel-pack buffers; the frame read now is collected on the next call, while the
    // game is already drawing the following one, so the CPU never stalls waiting for the graphics card
    private static final int PACK = 0x88EB, STREAM_READ = 0x88E1;
    private final int[] pbo = new int[2];
    private boolean usePbo;
    private boolean havePrevious;
    private int current;
    private int pboFailures;

    /** Set when the encoder runs at a lower frame rate than the video's (see the constructor): the file it writes first. */
    private File slowFile;
    private final String exe;
    private final File outFile;
    private final int fps, encodeFps;
    private final boolean hvc1;

    FfmpegSink(String exe, int w, int h, int fps, String encoderArgs, File outFile, String blurFilter) throws IOException {
        this(exe, w, h, fps, fps, encoderArgs, outFile, blurFilter);
    }

    /**
     * encodeFps below fps: for a GPU encoder that refuses the real frame rate. It is told encodeFps and writes
     * <out>.part.mp4; close() then copies that into outFile with every timestamp scaled by encodeFps / fps (no
     * re-encoding, seconds), which gives the real frame rate.
     */
    FfmpegSink(String exe, int w, int h, int fps, int encodeFps, String encoderArgs, File outFile, String blurFilter) throws IOException {
        this.w = w;
        this.h = h;
        this.exe = exe;
        this.outFile = outFile;
        this.fps = fps;
        this.encodeFps = Math.min(encodeFps, fps);
        this.hvc1 = encoderArgs.contains("hvc1");
        List<String> cmd = new ArrayList<String>();
        cmd.add(exe);
        cmd.addAll(Arrays.asList("-y", "-loglevel", "error"));
        cmd.addAll(Encoders.hwInput(encoderArgs)); // the VAAPI device, for AMD and Intel cards on Linux
        cmd.addAll(Arrays.asList("-f", "rawvideo", "-pix_fmt", "rgb24",
                "-s", w + "x" + h, "-framerate", String.valueOf(this.encodeFps), "-i", "-",
                "-vf", "vflip,crop=trunc(iw/2)*2:trunc(ih/2)*2" + (blurFilter != null ? "," + blurFilter : "") + Encoders.hwFilter(encoderArgs)));
        cmd.addAll(Arrays.asList(encoderArgs.trim().split("\\s+")));
        if (this.encodeFps < fps) {
            slowFile = new File(outFile.getPath() + ".part.mp4");
            cmd.add(slowFile.getAbsolutePath());
        } else {
            cmd.add(outFile.getAbsolutePath());
        }
        proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        out = proc.getOutputStream();
        final InputStream log = proc.getInputStream();
        Thread drain = new Thread(new Runnable() {
            public void run() {
                try {
                    byte[] b = new byte[4096];
                    int n;
                    StringBuilder sb = new StringBuilder();
                    while ((n = log.read(b)) > 0) {
                        sb.append(new String(b, 0, n));
                    }
                    if (sb.length() > 0) {
                        SubFrameMod.LOG.warn("ffmpeg: " + sb);
                    }
                } catch (IOException ignored) {
                }
            }
        }, "SubFrame-ffmpeg-log");
        drain.setDaemon(true);
        drain.start();
        buf = BufferUtils.createByteBuffer(w * h * 3);
        try {
            if (SubFrameMod.asyncReadback && !Boolean.getBoolean("subframe.syncreadback")) {
                GL11.glGetError(); // an error left from earlier must not turn the faster readback off
                for (int i = 0; i < 2; i++) {
                    pbo[i] = GL15.glGenBuffers();
                    GL15.glBindBuffer(PACK, pbo[i]);
                    GL15.glBufferData(PACK, (long) w * h * 3, STREAM_READ);
                }
                GL15.glBindBuffer(PACK, 0);
                usePbo = GL11.glGetError() == 0;
            }
        } catch (Throwable t) {
            usePbo = false;
            SubFrameMod.LOG.warn("Asynchronous readback is not available, reading synchronously: " + t);
        }
        for (int i = 0; i < SLOTS; i++) {
            free.add(new byte[w * h * 3]);
        }
        writer = new Thread(new Runnable() {
            public void run() {
                try {
                    while (true) {
                        byte[] b = full.take();
                        if (b == END) {
                            break;
                        }
                        if (failure == null) {
                            try {
                                out.write(b);
                            } catch (IOException e) {
                                failure = e;
                            }
                        }
                        free.add(b);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "SubFrame-ffmpeg-pipe");
        writer.setDaemon(true);
        writer.start();
    }

    /** Reads the currently bound framebuffer and queues it for ffmpeg (the frame before it, when reading asynchronously). */
    public void writeFrame() throws IOException {
        if (failure != null) {
            throw failure;
        }
        if (usePbo) {
            writeFrameAsync();
            return;
        }
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        buf.clear();
        GL11.glReadPixels(0, 0, w, h, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, buf);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
        lastGlError = GL11.glGetError();
        long t0 = System.nanoTime();
        byte[] slot;
        try {
            slot = free.take(); // blocks only when ffmpeg is more than a few frames behind
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        lastWaitNs = System.nanoTime() - t0;
        buf.get(slot);
        lastHash = RenderDiag.hash(slot);
        full.add(slot);
    }

    private void writeFrameAsync() throws IOException {
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL15.glBindBuffer(PACK, pbo[current]);
        GL11.glReadPixels(0, 0, w, h, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, 0L);
        lastGlError = GL11.glGetError();
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
        if (havePrevious) {
            collect(pbo[1 - current]);
        }
        GL15.glBindBuffer(PACK, 0);
        havePrevious = true;
        current = 1 - current;
    }

    /** Copies one finished pixel-pack buffer into a free slot and queues it. */
    private void collect(int buffer) throws IOException {
        GL15.glBindBuffer(PACK, buffer);
        ByteBuffer mapped = GL15.glMapBuffer(PACK, GL15.GL_READ_ONLY, (long) w * h * 3, null);
        if (mapped == null) {
            if (pboFailures++ < 3) {
                SubFrameMod.LOG.warn("Could not map the readback buffer (GL error " + GL11.glGetError() + ")");
            }
            return;
        }
        long t0 = System.nanoTime();
        byte[] slot;
        try {
            slot = free.take();
        } catch (InterruptedException e) {
            GL15.glUnmapBuffer(PACK);
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        lastWaitNs = System.nanoTime() - t0;
        mapped.get(slot);
        GL15.glUnmapBuffer(PACK);
        lastHash = RenderDiag.hash(slot);
        full.add(slot);
    }

    /** Collects the frame that is still in flight. Needs the OpenGL context, so it is done on the game thread. */
    private void flushLast() {
        if (usePbo && havePrevious) {
            try {
                collect(pbo[1 - current]);
            } catch (IOException e) {
                SubFrameMod.LOG.warn("Could not collect the last frame: " + e);
            } finally {
                GL15.glBindBuffer(PACK, 0);
                havePrevious = false;
            }
        }
    }

    public long lastHash() {
        return lastHash;
    }

    public int lastGlError() {
        return lastGlError;
    }

    public long lastWaitNs() {
        return lastWaitNs;
    }

    public void close() {
        flushLast();
        try {
            full.put(END);
            writer.join(60000);
            out.close();
            int code = proc.waitFor();
            if (slowFile != null) {
                if (code == 0 && slowFile.length() > 0) {
                    retime();
                } else {
                    SubFrameMod.LOG.error("ffmpeg ended with code " + code + "; the encoded file is kept at " + slowFile);
                }
            }
        } catch (Exception e) {
            SubFrameMod.LOG.error("Closing ffmpeg failed", e);
        }
    }

    /** Copies the slow file into the real one with its timestamps scaled to the real frame rate, without encoding again. */
    private void retime() throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<String>(Arrays.asList(exe, "-y", "-loglevel", "error",
                "-itsscale", String.format(java.util.Locale.ROOT, "%.12f", encodeFps / (double) fps),
                "-i", slowFile.getAbsolutePath(), "-c", "copy", "-video_track_timescale", String.valueOf(fps)));
        if (hvc1) {
            cmd.addAll(Arrays.asList("-tag:v", "hvc1"));
        }
        cmd.add(outFile.getAbsolutePath());
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        p.getOutputStream().close();
        java.io.ByteArrayOutputStream log = new java.io.ByteArrayOutputStream();
        InputStream in = p.getInputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) > 0) {
            log.write(b, 0, n);
        }
        int code = p.waitFor();
        if (code == 0 && outFile.length() > 0) {
            SubFrameMod.LOG.info("[render] set the video's frame rate to " + fps + " fps: " + outFile.getName());
            if (!slowFile.delete()) {
                slowFile.deleteOnExit();
            }
        } else {
            SubFrameMod.LOG.error("Setting the frame rate failed (code " + code + "): " + log + "; the encoded file is kept at " + slowFile);
        }
    }
}
