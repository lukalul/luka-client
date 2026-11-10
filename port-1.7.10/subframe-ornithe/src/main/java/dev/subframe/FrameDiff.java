package dev.subframe;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

/** Compares replayed frames to the live frames they should reproduce and writes a report. */
final class FrameDiff {
    private final File dir;
    private final String base;
    private final StringBuilder lines = new StringBuilder();

    private int frames;
    private double sumMean;
    private double sumExact;
    private double sumOver8;
    private int exactFrames;
    private int savedBad;
    private byte[] prevLive, prevReplay;
    private int neighbourLogged;

    private static String avg(byte[] a) {
        long r = 0, g = 0, b = 0;
        int n = a.length / 3;
        for (int i = 0; i < n; i++) {
            r += a[i * 3] & 255;
            g += a[i * 3 + 1] & 255;
            b += a[i * 3 + 2] & 255;
        }
        return (r / n) + "," + (g / n) + "," + (b / n);
    }

    private static double meanAbs(byte[] a, byte[] b) {
        long sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += Math.abs((a[i] & 255) - (b[i] & 255));
        }
        return sum / (double) a.length;
    }

    private int worstIndex = -1;
    private double worstMean = -1;
    private byte[] worstLive, worstReplay;
    private int w, h;

    FrameDiff(File recording) {
        this.dir = recording.getParentFile();
        this.base = recording.getName().replaceAll("\\.sfr$", "");
    }

    void add(int index, byte[] live, byte[] replay, int w, int h) {
        this.w = w;
        this.h = h;
        long sum = 0;
        int exact = 0, over8 = 0;
        int pixels = w * h;
        for (int i = 0; i < pixels; i++) {
            int o = i * 3;
            int d0 = Math.abs((live[o] & 255) - (replay[o] & 255));
            int d1 = Math.abs((live[o + 1] & 255) - (replay[o + 1] & 255));
            int d2 = Math.abs((live[o + 2] & 255) - (replay[o + 2] & 255));
            sum += d0 + d1 + d2;
            int m = Math.max(d0, Math.max(d1, d2));
            if (m == 0) {
                exact++;
            }
            if (m > 8) {
                over8++;
            }
        }
        double mean = sum / (double) (pixels * 3L);
        double pExact = 100.0 * exact / pixels;
        double pOver8 = 100.0 * over8 / pixels;
        frames++;
        sumMean += mean;
        sumExact += pExact;
        sumOver8 += pOver8;
        if (exact == pixels) {
            exactFrames++;
        }
        lines.append(String.format(Locale.ROOT, "frame %5d  meanAbsDiff %7.4f  identical px %6.2f%%  px off by >8 %6.2f%%%n",
                index, mean, pExact, pOver8));
        if (mean > worstMean) {
            worstMean = mean;
            worstIndex = index;
            worstLive = live;
            worstReplay = replay;
        }
        if (frames == 1) {
            writePngs("first", index, live, replay);
        }
        if (pOver8 > 5 && prevLive != null && neighbourLogged < 40) {
            neighbourLogged++;
            SubFrameMod.LOG.info(String.format(Locale.ROOT,
                    "BADFRAME %d: avg RGB live=%s replay=%s",
                    index, avg(live), avg(replay)));
        }
        prevLive = live.clone();
        prevReplay = replay.clone();
        if (pOver8 > 5 && savedBad < 4 && frames % 40 == 0) {
            savedBad++;
            writePngs("bad" + savedBad, index, live, replay);
        }
    }

    String finish() {
        if (frames == 0) {
            return "No frames compared.";
        }
        if (worstIndex >= 0) {
            writePngs("worst", worstIndex, worstLive, worstReplay);
        }
        String summary = String.format(Locale.ROOT,
                "Compared %d frames: %d fully identical. Mean abs diff %.4f / 255, identical pixels %.2f%%, "
                        + "pixels off by more than 8: %.2f%%. Worst frame %d (mean abs diff %.4f).",
                frames, exactFrames, sumMean / frames, sumExact / frames, sumOver8 / frames, worstIndex, worstMean);
        try {
            PrintWriter pw = new PrintWriter(new File(dir, base + ".diff.txt"), "UTF-8");
            pw.println(summary);
            pw.println();
            pw.print(lines);
            pw.close();
        } catch (IOException e) {
            SubFrameMod.LOG.error("Could not write diff report", e);
        }
        return summary;
    }

    private void writePngs(String tag, int index, byte[] live, byte[] replay) {
        try {
            ImageIO.write(toImage(live, null), "png", new File(dir, base + "." + tag + "-live.png"));
            ImageIO.write(toImage(replay, null), "png", new File(dir, base + "." + tag + "-replay.png"));
            ImageIO.write(toImage(live, replay), "png", new File(dir, base + "." + tag + "-diffx8.png"));
        } catch (IOException e) {
            SubFrameMod.LOG.error("Could not write diff images for frame " + index, e);
        }
    }

    /** GL rows run bottom-up, so flip. With a second buffer, draws |a-b| scaled by 8. */
    private BufferedImage toImage(byte[] a, byte[] b) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int o = (y * w + x) * 3;
                int r, g, bl;
                if (b == null) {
                    r = a[o] & 255;
                    g = a[o + 1] & 255;
                    bl = a[o + 2] & 255;
                } else {
                    r = Math.min(255, 8 * Math.abs((a[o] & 255) - (b[o] & 255)));
                    g = Math.min(255, 8 * Math.abs((a[o + 1] & 255) - (b[o + 1] & 255)));
                    bl = Math.min(255, 8 * Math.abs((a[o + 2] & 255) - (b[o + 2] & 255)));
                }
                img.setRGB(x, h - 1 - y, (r << 16) | (g << 8) | bl);
            }
        }
        return img;
    }
}
