package dev.subframe;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

/** A plain text log file next to the recordings, with seconds-since-start on every line, flushed often. */
final class FileLog {
    private final PrintWriter out;
    private final long t0 = System.nanoTime();
    private int sinceFlush;

    FileLog(File file) throws IOException {
        file.getParentFile().mkdirs();
        out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8")));
    }

    synchronized void line(String s) {
        out.println(String.format("%9.3f  %s", (System.nanoTime() - t0) / 1e9, s));
        if (++sinceFlush >= 25) {
            out.flush();
            sinceFlush = 0;
        }
    }

    /** Lines that matter when something has gone wrong are flushed at once. */
    synchronized void important(String s) {
        line(s);
        out.flush();
        sinceFlush = 0;
    }

    synchronized void close() {
        out.flush();
        out.close();
    }
}
