package dev.subframe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Dumps raw RGB of every Nth live frame next to the recording, as ground truth for the diff test.
 * File layout: int width, int height, then repeated {int recordedFrameIndex, w*h*3 bytes}.
 */
final class LiveCapture {
    /** Skip the first frames: chunks are still loading and the replay cannot match that. */
    private static final int FIRST_FRAME = 120;

    private static DataOutputStream out;
    private static int w, h;

    private LiveCapture() {
    }

    static File liveFile(File recording) {
        return new File(recording.getPath() + ".live");
    }

    static byte[] readFramebuffer(int w, int h) {
        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 3);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, w, h, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, buf);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
        byte[] bytes = new byte[w * h * 3];
        buf.get(bytes);
        return bytes;
    }

    /** Called at the end of a rendered frame, while the game framebuffer is bound. */
    static void capture(File recording, int index) {
        if (index < FIRST_FRAME || index % SubFrameMod.liveStride != 0) {
            return;
        }
        try {
            Framebuffer fb = Minecraft.getMinecraft().getFramebuffer();
            if (out == null) {
                w = fb.framebufferWidth;
                h = fb.framebufferHeight;
                out = new DataOutputStream(new BufferedOutputStream(
                        new FileOutputStream(liveFile(recording)), 1 << 20));
                out.writeInt(w);
                out.writeInt(h);
            }
            out.writeInt(index);
            out.write(readFramebuffer(w, h));
        } catch (IOException e) {
            SubFrameMod.LOG.error("Live capture failed", e);
            close();
        }
    }

    static void close() {
        if (out != null) {
            try {
                out.close();
            } catch (IOException ignored) {
            }
            out = null;
        }
    }
}
