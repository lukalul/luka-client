package dev.subframe;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Writes every frame as frames/000001.png, 000002.png ... so any other tool (ffmpeg, Blender, Smoothie,
 * After Effects) can blend or edit them. PNG encoding runs on a worker thread so the game keeps rendering.
 */
final class PngSink implements FrameSink {
    private static final byte[] END = new byte[0];

    private final File dir;
    private final int w, h;
    private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<byte[]>(8);
    private final Thread worker;
    private int index;
    private long lastHash;
    private int lastGlError;

    PngSink(File dir, int w, int h, int fps) throws IOException {
        this.dir = dir;
        this.w = w;
        this.h = h;
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + dir);
        }
        PrintWriter pw = new PrintWriter(new File(dir, "README.txt"), "UTF-8");
        pw.println("SubFrame frame sequence: " + w + "x" + h + " at " + fps + " fps, numbered from 000001.png.");
        pw.println();
        pw.println("Turn it into a video at the same speed:");
        pw.println("  ffmpeg -framerate " + fps + " -i %06d.png -c:v libx264 -crf 12 -pix_fmt yuv420p out.mp4");
        pw.println();
        int k = Math.max(1, fps / 60);
        pw.println("Blend every " + k + " frames into one (" + fps + " fps -> 60 fps, tested with ffmpeg 7):");
        pw.println("  ffmpeg -framerate " + fps + " -i %06d.png -vf \"tmix=frames=" + k + ",select='not(mod(n\\," + k
                + "))',setpts=N/(60*TB)\" -r 60 -c:v libx264 -crf 12 -pix_fmt yuv420p blended.mp4");
        pw.close();
        worker = new Thread(new Runnable() {
            public void run() {
                try {
                    int n = 0;
                    while (true) {
                        byte[] px = queue.take();
                        if (px == END) {
                            break;
                        }
                        ImageIO.write(toImage(px), "png", new File(PngSink.this.dir, String.format("%06d.png", ++n)));
                    }
                } catch (Exception e) {
                    SubFrameMod.LOG.error("PNG writer failed", e);
                }
            }
        }, "SubFrame-PNG");
        worker.start();
    }

    /** GL rows run bottom-up, so flip. */
    private BufferedImage toImage(byte[] px) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            int o = y * w * 3;
            for (int x = 0; x < w; x++, o += 3) {
                row[x] = ((px[o] & 255) << 16) | ((px[o + 1] & 255) << 8) | (px[o + 2] & 255);
            }
            img.setRGB(0, h - 1 - y, w, 1, row, 0, w);
        }
        return img;
    }

    public void writeFrame() throws IOException {
        byte[] px = LiveCapture.readFramebuffer(w, h);
        lastGlError = org.lwjgl.opengl.GL11.glGetError();
        lastHash = RenderDiag.hash(px);
        try {
            queue.put(px);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        index++;
    }

    public long lastHash() {
        return lastHash;
    }

    public int lastGlError() {
        return lastGlError;
    }

    public void close() {
        try {
            queue.put(END);
            worker.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        SubFrameMod.LOG.info("Wrote " + index + " PNG frames to " + dir);
    }
}
