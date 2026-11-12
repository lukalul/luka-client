package dev.motionblur;

import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/**
 * "Hand only": the world stays sharp and only the first-person hand and held item get the blur (the swing, the sway when
 * turning, the item use pose). The hand is drawn into its own transparent layer instead of the screen; that layer is mixed
 * into a float history ({@code history = history * keep + hand * (1 - keep)}, colours premultiplied by their coverage)
 * and the history is laid over the finished world.
 */
public final class HandBlur {
    private static int handTexture, handFbo, handDepth;
    private static int historyTexture, historyFbo;
    private static int width = -1, height = -1;
    private static boolean failed;
    /** The hand of this frame went into the layer (renderHand ran); otherwise the layer is cleared before mixing. */
    private static boolean drawn;
    private static boolean inHand;
    private static int screenFbo;

    private HandBlur() { }

    /** True when this frame's hand should go into the hand layer. */
    public static boolean wanted() {
        return MotionBlurSettings.enabled && MotionBlurSettings.handOnly && MotionBlurSettings.level > 0 && !failed;
    }

    /** renderHand starts: draw into the hand layer, cleared to transparent. */
    public static void beginHand(int screenWidth, int screenHeight) {
        if (!wanted() || inHand) return;
        try {
            if (!ensure(screenWidth, screenHeight)) return;
            screenFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, handFbo);
            GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glPopAttrib();
            inHand = true;
        } catch (Throwable t) {
            fail("hand layer", t);
        }
    }

    /** renderHand is done: back to the screen. */
    public static void endHand() {
        if (!inHand) return;
        inHand = false;
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, screenFbo);
            drawn = true;
        } catch (Throwable t) {
            fail("hand layer", t);
        }
    }

    /** After the world (and where the hand would have been drawn): mix the hand layer into the history and show it. */
    static void composite(int screenWidth, int screenHeight, float keep, boolean first, BlurRenderer.Pass pass) {
        if (failed || !ensure(screenWidth, screenHeight)) return;
        int previousFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        boolean begun = false;
        try {
            if (!drawn) {
                // no hand this frame (third person, F1, a menu that hides it): the trail fades out on its own
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, handFbo);
                GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
                GL11.glClearColor(0f, 0f, 0f, 0f);
                GL11.glColorMask(true, true, true, true);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                GL11.glPopAttrib();
            }
            drawn = false;
            pass.begin();
            begun = true;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, historyFbo);
            GL11.glViewport(0, 0, width, height);
            if (first) {
                GL11.glDisable(GL11.GL_BLEND);
                pass.quad(handTexture, 1f, 1f);
            } else {
                GL11.glEnable(GL11.GL_BLEND);
                // history *= keep (every channel)
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glBlendFunc(GL11.GL_ZERO, GL11.GL_SRC_ALPHA);
                pass.quad(0, 0f, keep);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                // history += hand * (1 - keep)
                GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
                float add = 1f - keep;
                pass.quad(handTexture, add, add);
            }
            // the history over the sharp world (premultiplied)
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
            GL11.glViewport(0, 0, width, height);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            pass.quad(historyTexture, 1f, 1f);
        } catch (Throwable t) {
            fail("hand blur", t);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
            if (begun) pass.end();
        }
    }

    private static boolean ensure(int w, int h) {
        if (failed) return false;
        if (handFbo != 0 && w == width && h == height) return true;
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        try {
            release();
            handTexture = texture(w, h, GL11.GL_RGBA8, GL11.GL_UNSIGNED_BYTE);
            historyTexture = texture(w, h, GL30.GL_RGBA16F, GL11.GL_FLOAT);
            handDepth = GL30.glGenRenderbuffers();
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, handDepth);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, w, h);
            handFbo = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, handFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, handTexture, 0);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, handDepth);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            historyFbo = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, historyFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, historyTexture, 0);
            int historyStatus = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE || historyStatus != GL30.GL_FRAMEBUFFER_COMPLETE) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
                fail("hand layer framebuffer incomplete (" + status + ", " + historyStatus + ")", null);
                return false;
            }
            width = w;
            height = h;
            return true;
        } catch (Throwable t) {
            fail("hand layer", t);
            return false;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        }
    }

    private static int texture(int w, int h, int internalFormat, int type) {
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, w, h, 0, GL11.GL_RGBA, type, (ByteBuffer) null);
        return id;
    }

    /** Frees the layer and the history (resizing, turning the blur off). */
    public static void release() {
        try {
            if (handFbo != 0) GL30.glDeleteFramebuffers(handFbo);
            if (historyFbo != 0) GL30.glDeleteFramebuffers(historyFbo);
            if (handDepth != 0) GL30.glDeleteRenderbuffers(handDepth);
            if (handTexture != 0) GL11.glDeleteTextures(handTexture);
            if (historyTexture != 0) GL11.glDeleteTextures(historyTexture);
        } catch (Throwable ignored) { }
        handFbo = historyFbo = handDepth = handTexture = historyTexture = 0;
        width = height = -1;
        drawn = false;
    }

    private static void fail(String where, Throwable t) {
        // the hand is then drawn on the screen as usual (no blur on it) instead of disappearing
        failed = true;
        inHand = false;
        MotionBlur.LOG.error("MotionBlur hand-only mode failed in {}; the hand is drawn without blur", where, t);
        release();
    }
}
