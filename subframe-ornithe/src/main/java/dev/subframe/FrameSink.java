package dev.subframe;

import java.io.IOException;

/** Receives each finished frame from the bound game framebuffer. */
interface FrameSink {
    void writeFrame() throws IOException;

    void close();

    /** Fingerprint of the frame that was just written, to spot a frozen picture. */
    default long lastHash() {
        return 0;
    }

    /** How long the last frame had to wait for the encoder to catch up, in nanoseconds. */
    default long lastWaitNs() {
        return 0;
    }

    /** OpenGL error raised while reading the frame back, or 0. */
    default int lastGlError() {
        return 0;
    }
}
