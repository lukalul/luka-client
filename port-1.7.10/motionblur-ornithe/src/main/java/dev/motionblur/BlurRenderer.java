package dev.motionblur;

import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/**
 * Temporal blur with three modes:
 * <ul>
 *   <li>V1 phosphor: {@code history = max(history * f, frame)} - bright things leave fading ghosts</li>
 *   <li>V2 blend: {@code history = history * w + frame * (1 - w)} - symmetric smear</li>
 *   <li>V3 capped blend: V2 with the history weight limited to 0.675 - never gets long or mushy</li>
 * </ul>
 * The weights are scaled by frame time so the look is the same at any FPS. The history lives in 16-bit float textures
 * (rendered into through a framebuffer), because at hundreds of FPS the weight of a new frame is far below 1/255 and an
 * 8-bit history would stop updating (stuck ghosts). If float framebuffers are unavailable, an 8-bit history copied from
 * the screen is used instead (V1 then behaves like V2).
 */
public final class BlurRenderer {
    private static final BlurState STATE = new BlurState();
    /** Exactly the state groups touched by the blend pass; avoids the far costlier GL_ALL_ATTRIB_BITS. */
    private static final int BLEND_ATTRIB_BITS = GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
        | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_TRANSFORM_BIT | GL11.GL_LIGHTING_BIT
        | GL11.GL_FOG_BIT | GL11.GL_SCISSOR_BIT | GL11.GL_VIEWPORT_BIT;

    // 8-bit fallback: one texture holding the previous output
    private static int texture;
    // preferred path: scene copy (8-bit) + two float history textures (the second only used by V1's ping-pong)
    private static int sceneTexture;
    private static final int[] historyTexture = new int[2];
    private static final int[] historyFbo = new int[2];
    private static int current; // index of the history that holds the latest result
    private static boolean fboTried;
    private static boolean fboOk;

    private static int textureWidth = -1;
    private static int textureHeight = -1;
    private static long lastBlendNanos;
    private static EntityPlayerSP lastPlayer;
    private static int lastDimension = Integer.MIN_VALUE;
    private static int dbgFrames, dbgBlends, dbgCaptures;
    private static double dbgWeight;

    /** Offline renders (the replay mod): when above 0, every blended frame counts as this many seconds instead of the real time between frames. */
    public static volatile double fixedDt;

    /** Starts the blur history afresh (the next frame seeds it). */
    public static void reset() {
        release();
    }

    // optional GPU timing by luka's profiler (reflection: this mod also runs without luka)
    private static java.lang.invoke.MethodHandle gpuBegin, gpuEnd;
    private static Object gpuPhase;
    private static boolean gpuLooked;

    private static void gpu(boolean begin) {
        try {
            if (!gpuLooked) {
                gpuLooked = true;
                Class<?> profiler = Class.forName("dev.cbui.Profiler");
                Class<?> phase = Class.forName("dev.cbui.Profiler$GpuPhase");
                gpuPhase = Enum.valueOf((Class) phase, "BLUR");
                java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.publicLookup();
                java.lang.invoke.MethodType type = java.lang.invoke.MethodType.methodType(void.class, phase);
                gpuBegin = lookup.findStatic(profiler, "gpuBegin", type);
                gpuEnd = lookup.findStatic(profiler, "gpuEnd", type);
            }
            if (gpuBegin == null) return;
            if (begin) gpuBegin.invoke(gpuPhase); else gpuEnd.invoke(gpuPhase);
        } catch (Throwable t) {
            gpuBegin = null;
        }
    }

    private BlurRenderer() { }

    /** Called after all world/post processing and before vanilla starts its GUI profiler section. */
    public static void render(Minecraft mc) {
        if (!MotionBlurSettings.enabled) return; // required zero-cost disabled fast path
        try {
            // The settings flag can be switched on without enabledChanged() ever running (a config that loads as
            // enabled): then BlurState stayed off and no frame was ever blended. Keep the two in step here.
            STATE.setEnabled(true);
            if (MotionBlurSettings.level <= 0) {
                STATE.clearHistory();
                return;
            }
            EntityPlayerSP player = mc.thePlayer;
            if (player != lastPlayer || (player != null && player.dimension != lastDimension)) {
                STATE.clearHistory();
                lastPlayer = player;
                lastDimension = player == null ? Integer.MIN_VALUE : player.dimension;
            }
            boolean allowed = mc.currentScreen == null || MotionBlurSettings.blurMenus
                || mc.currentScreen.getClass().getName().equals("dev.subframe.ReplayScreen"); // the replay viewer is only a timeline over the picture
            BlurState.Action action = STATE.next(
                mc.displayWidth, mc.displayHeight, mc.theWorld, allowed, MotionBlurSettings.frameSkip);
            dbgFrames++;
            int mode = MotionBlurSettings.mode;
            gpu(true);
            if (action == BlurState.Action.CAPTURE) {
                dbgCaptures++;
                process(mc.displayWidth, mc.displayHeight, 0f, true, mode);
                lastBlendNanos = System.nanoTime();
            } else if (action == BlurState.Action.BLEND) {
                long now = System.nanoTime();
                double dt = fixedDt > 0 ? fixedDt : lastBlendNanos == 0L ? AlphaMath.REFERENCE_DT : (now - lastBlendNanos) / 1_000_000_000.0;
                lastBlendNanos = now;
                float keep = (float) AlphaMath.retention(AlphaMath.frameWeight(MotionBlurSettings.level, mode), dt);
                dbgBlends++;
                dbgWeight += keep;
                process(mc.displayWidth, mc.displayHeight, keep, false, mode);
            }
            gpu(false);
            if (dbgFrames >= 600) debugReport(mc);
        } catch (Throwable t) {
            gpu(false);
            MotionBlur.report("render", t);
        }
    }

    /** Writes how many frames were blended (only when config/motionblur-debug exists). */
    private static void debugReport(Minecraft mc) {
        try {
            java.io.File marker = new java.io.File(mc.mcDataDir, "config/motionblur-debug");
            if (marker.exists()) {
                String line = String.format(java.util.Locale.ROOT,
                    "frames=%d captures=%d blends=%d avgKeep=%.4f level=%d mode=V%d floatHistory=%b%n",
                    dbgFrames, dbgCaptures, dbgBlends, dbgBlends == 0 ? 0.0 : dbgWeight / dbgBlends,
                    MotionBlurSettings.level, MotionBlurSettings.mode + 1, fboOk);
                java.nio.file.Files.write(new java.io.File(mc.mcDataDir, "config/motionblur-debug.log").toPath(),
                    line.getBytes("UTF-8"), java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
            }
        } catch (Throwable ignored) { }
        dbgFrames = 0; dbgBlends = 0; dbgCaptures = 0; dbgWeight = 0.0;
    }

    public static void enabledChanged(boolean enabled) {
        STATE.setEnabled(enabled);
        lastBlendNanos = 0L;
        lastPlayer = null;
        lastDimension = Integer.MIN_VALUE;
        if (!enabled) release();
    }

    public static void release() {
        STATE.setEnabled(false);
        if (texture != 0) {
            GL11.glDeleteTextures(texture);
            texture = 0;
        }
        deleteFbo();
        textureWidth = -1;
        textureHeight = -1;
        lastBlendNanos = 0L;
    }

    private static void process(int width, int height, float keep, boolean first, int mode) {
        if (ensureFbo(width, height)) {
            if (mode == MotionBlurSettings.MODE_PHOSPHOR && !first) phosphorPass(width, height, keep);
            else blendPass(width, height, keep, first);
        } else if (first) capture(width, height);
        else blendAndCapture(width, height, keep);
    }

    // ---- float history path ----

    private static boolean ensureFbo(int width, int height) {
        if (fboTried && !fboOk) return false;
        if (historyFbo[0] != 0 && width == textureWidth && height == textureHeight) return true;
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            int previousFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            deleteFbo();
            sceneTexture = makeTexture(width, height, GL11.GL_RGBA8, GL11.GL_UNSIGNED_BYTE);
            for (int i = 0; i < 2; i++) {
                historyTexture[i] = makeTexture(width, height, GL30.GL_RGBA16F, GL11.GL_FLOAT);
                historyFbo[i] = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, historyFbo[i]);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, historyTexture[i], 0);
                int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
                if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
                    fboTried = true;
                    fboOk = false;
                    deleteFbo();
                    MotionBlur.report("float history framebuffer incomplete (status " + status + "), using 8-bit history", null);
                    return false;
                }
            }
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
            current = 0;
            fboTried = true;
            fboOk = true;
            textureWidth = width;
            textureHeight = height;
            return true;
        } catch (Throwable t) {
            fboTried = true;
            fboOk = false;
            deleteFbo();
            MotionBlur.report("float history unavailable, using 8-bit history", t);
            return false;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        }
    }

    private static int makeTexture(int width, int height, int internalFormat, int type) {
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0,
            GL11.GL_RGBA, type, (ByteBuffer) null);
        return id;
    }

    private static void deleteFbo() {
        for (int i = 0; i < 2; i++) {
            try {
                if (historyFbo[i] != 0) GL30.glDeleteFramebuffers(historyFbo[i]);
            } catch (Throwable ignored) { }
            historyFbo[i] = 0;
            if (historyTexture[i] != 0) GL11.glDeleteTextures(historyTexture[i]);
            historyTexture[i] = 0;
        }
        if (sceneTexture != 0) GL11.glDeleteTextures(sceneTexture);
        sceneTexture = 0;
        current = 0;
    }

    /** V2/V3 (and the seeding frame): copy the frame, mix it into the float history, then show the history. */
    private static void blendPass(int width, int height, float keep, boolean first) {
        int previousFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        boolean[] saved = new boolean[4];
        try {
            beginPass(saved);
            // 1. the finished frame -> scene texture
            forceOpaque();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneTexture);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
            // 2. history = history * keep + frame * (1 - keep)   (the first frame just seeds the history)
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, historyFbo[current]);
            GL11.glViewport(0, 0, width, height);
            if (first) {
                GL11.glDisable(GL11.GL_BLEND);
                drawQuad(sceneTexture, 1.0F, 1.0F);
            } else {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                drawQuad(sceneTexture, 1.0F, 1.0F - keep);
            }
            // 3. show the history
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
            GL11.glViewport(0, 0, width, height);
            GL11.glDisable(GL11.GL_BLEND);
            drawQuad(historyTexture[current], 1.0F, 1.0F);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
            endPass(saved);
        }
    }

    /** V1: history' = max(history * f, frame), done by ping-pong between two float textures and a GL_MAX blend. */
    private static void phosphorPass(int width, int height, float keep) {
        int previousFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        boolean[] saved = new boolean[4];
        int next = 1 - current;
        try {
            beginPass(saved);
            forceOpaque();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneTexture);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, historyFbo[next]);
            GL11.glViewport(0, 0, width, height);
            // history dimmed by f ...
            GL11.glDisable(GL11.GL_BLEND);
            drawQuad(historyTexture[current], keep, 1.0F);
            // ... then the frame wins wherever it is brighter
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendEquation(GL14.GL_MAX);
            drawQuad(sceneTexture, 1.0F, 1.0F);
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            current = next;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
            GL11.glViewport(0, 0, width, height);
            GL11.glDisable(GL11.GL_BLEND);
            drawQuad(historyTexture[current], 1.0F, 1.0F);
        } finally {
            try { GL14.glBlendEquation(GL14.GL_FUNC_ADD); } catch (Throwable ignored) { }
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
            endPass(saved);
        }
    }

    /**
     * The sky (and anything else drawn without writing alpha) leaves alpha 0 in the screen. The blend weights come from
     * the copied texture's alpha, so those pixels never updated and stayed at whatever the seeding frame had (a black sky).
     * Writing alpha 1 over the whole screen (colour untouched) makes every pixel take part. Needs the colour state saved.
     */
    private static void forceOpaque() {
        GL11.glColorMask(false, false, false, true);
        GL11.glClearColor(0f, 0f, 0f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glColorMask(true, true, true, true);
    }

    private static void forceOpaqueScoped() {
        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
        try {
            forceOpaque();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void beginPass(boolean[] saved) {
        GL11.glPushAttrib(BLEND_ATTRIB_BITS);
        saved[0] = true;
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        saved[1] = true;
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        saved[2] = true;
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        saved[3] = true;
        GL11.glLoadIdentity();
        quadState();
    }

    private static void endPass(boolean[] saved) {
        if (saved[3]) { GL11.glMatrixMode(GL11.GL_TEXTURE); GL11.glPopMatrix(); }
        if (saved[2]) { GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix(); }
        if (saved[1]) { GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix(); }
        if (saved[0]) GL11.glPopAttrib();
    }

    private static void quadState() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColorMask(true, true, true, true);
    }

    /** Draws the texture over the whole target; {@code scale} multiplies the colour, {@code alpha} the opacity. */
    private static void drawQuad(int textureId, float scale, float alpha) {
        GL11.glColor4f(scale, scale, scale, alpha);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F); GL11.glVertex2f(0.0F, 0.0F);
        GL11.glTexCoord2f(1.0F, 0.0F); GL11.glVertex2f(1.0F, 0.0F);
        GL11.glTexCoord2f(1.0F, 1.0F); GL11.glVertex2f(1.0F, 1.0F);
        GL11.glTexCoord2f(0.0F, 1.0F); GL11.glVertex2f(0.0F, 1.0F);
        GL11.glEnd();
    }

    // ---- 8-bit fallback path ----

    private static void capture(int width, int height) {
        int previousActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            forceOpaqueScoped();
            ensureTexture(width, height);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActive);
        }
    }

    private static void blendAndCapture(int width, int height, float keep) {
        boolean[] saved = new boolean[4];
        try {
            beginPass(saved);
            forceOpaque();
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            ensureTexture(width, height);
            drawQuad(texture, 1.0F, keep); // previous output drawn over the new frame with the history's weight
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
        } finally {
            endPass(saved);
        }
    }

    private static void ensureTexture(int width, int height) {
        if (texture == 0) texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        if (width == textureWidth && height == textureHeight && !fboOk) return;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        textureWidth = width;
        textureHeight = height;
    }
}
