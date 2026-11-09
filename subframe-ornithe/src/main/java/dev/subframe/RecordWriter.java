package dev.subframe;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

/** Writes length-prefixed records to a gzip file on its own thread, so game and network threads never block on disk. */
final class RecordWriter {
    private static final Object END = new Object();

    private final LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<Object>();
    private final DataOutputStream out;
    private final Thread thread;

    RecordWriter(File file) throws IOException {
        out = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(new FileOutputStream(file), 1 << 16, true), 1 << 16));
        thread = new Thread(new Runnable() {
            public void run() {
                try {
                    while (true) {
                        Object o = queue.take();
                        if (o == END) {
                            break;
                        }
                        if (o instanceof CountDownLatch) {
                            out.flush();
                            ((CountDownLatch) o).countDown();
                            continue;
                        }
                        byte[] rec = (byte[]) o;
                        out.writeInt(rec.length);
                        out.write(rec);
                        if (queue.isEmpty()) {
                            out.flush();
                        }
                    }
                } catch (Exception e) {
                    SubFrameMod.LOG.error("Recording writer failed", e);
                } finally {
                    try {
                        out.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }, "SubFrame-Writer");
        thread.setDaemon(true);
        thread.start();
    }

    void add(byte[] record) {
        queue.add(record);
    }

    /** Blocks until everything added so far is flushed to disk, so the file can be read while still open. */
    void flushAndWait() {
        CountDownLatch latch = new CountDownLatch(1);
        queue.add(latch);
        try {
            latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void close() {
        queue.add(END);
        try {
            thread.join(15000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
