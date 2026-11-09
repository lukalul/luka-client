package dev.subframe;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

/**
 * The part of a recording to render, in seconds from the recording start. Saved next to the recording
 * (name.sfr.range) so a render started from the manager uses the same selection as the viewer.
 */
final class RenderRange {
    /** Negative means no selection: the whole recording is rendered. */
    double start = -1;
    double end = -1;

    boolean isSet() {
        return start >= 0 && end > start;
    }

    static File file(File recording) {
        return new File(recording.getPath() + ".range");
    }

    static RenderRange load(File recording) {
        RenderRange r = new RenderRange();
        File f = file(recording);
        if (!f.isFile()) {
            return r;
        }
        try {
            BufferedReader in = new BufferedReader(new FileReader(f));
            try {
                String[] parts = in.readLine().trim().split("\\s+");
                r.start = Double.parseDouble(parts[0]);
                r.end = Double.parseDouble(parts[1]);
            } finally {
                in.close();
            }
        } catch (Exception e) {
            r.start = r.end = -1;
        }
        return r;
    }

    void save(File recording) {
        File f = file(recording);
        if (!isSet()) {
            f.delete();
            return;
        }
        try {
            FileWriter w = new FileWriter(f);
            try {
                w.write(String.format(Locale.ROOT, "%.3f %.3f%n", start, end));
            } finally {
                w.close();
            }
        } catch (IOException e) {
            SubFrameMod.LOG.warn("Could not save the render range of " + recording.getName() + ": " + e);
        }
    }

    /** m:ss.s for display. */
    static String format(double seconds) {
        long tenths = Math.round(seconds * 10);
        long m = tenths / 600;
        double s = (tenths % 600) / 10.0;
        if (m >= 60) {
            return String.format(Locale.ROOT, "%d:%02d:%04.1f", m / 60, m % 60, s);
        }
        return String.format(Locale.ROOT, "%d:%04.1f", m, s);
    }
}
