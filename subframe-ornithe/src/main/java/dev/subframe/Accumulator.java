package dev.subframe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * Float16 accumulation buffers ("slots", one per output frame being blended at the same time). Each subframe is copied once
 * ({@link #capture}), converted from sRGB to linear light, scaled by its weight and added to every slot it counts in
 * ({@link #addCaptured}); {@link #resolve} converts a slot's sum back to sRGB and draws it into the game framebuffer.
 */
final class Accumulator {
    private static final String VERT =
            "void main() { gl_Position = ftransform(); gl_TexCoord[0] = gl_MultiTexCoord0; }";
    private static final String FRAG_ADD =
            "uniform sampler2D tex; uniform float weight;\n"
            + "void main() { vec3 c = texture2D(tex, gl_TexCoord[0].xy).rgb;\n"
            + "  gl_FragColor = vec4(pow(c, vec3(2.2)) * weight, weight); }";
    private static final String FRAG_OUT =
            "uniform sampler2D tex;\n"
            + "void main() { vec3 c = texture2D(tex, gl_TexCoord[0].xy).rgb;\n"
            + "  gl_FragColor = vec4(pow(c, vec3(1.0 / 2.2)), 1.0); }";

    private final int w, h;
    /** One float16 texture and framebuffer per slot. */
    private final int[] tex, fbo;
    /** Receives a copy of the frame the game just drew, wherever it drew it (window or off-screen buffer). */
    private final int staging;
    private final int progAdd, progOut;

    Accumulator(int w, int h, int slots) {
        this.w = w;
        this.h = h;
        int count = Math.max(1, slots);
        tex = new int[count];
        fbo = new int[count];
        GL11.glPushAttrib(GL11.GL_TEXTURE_BIT);
        for (int i = 0; i < count; i++) {
            tex[i] = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex[i]);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, w, h, 0, GL11.GL_RGBA, GL11.GL_FLOAT,
                    (ByteBuffer) null);
        }
        staging = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, staging);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                (ByteBuffer) null);
        GL11.glPopAttrib();
        for (int i = 0; i < count; i++) {
            fbo[i] = OpenGlHelper.glGenFramebuffers();
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo[i]);
            OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, tex[i], 0);
        }
        restoreGameFramebuffer();
        progAdd = program(FRAG_ADD);
        progOut = program(FRAG_OUT);
    }

    private static int program(String frag) {
        int vs = compile(GL20.GL_VERTEX_SHADER, VERT);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, frag);
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, vs);
        GL20.glAttachShader(p, fs);
        GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("Shader link failed: " + GL20.glGetProgramInfoLog(p, 4096));
        }
        return p;
    }

    private static int compile(int type, String src) {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("Shader compile failed: " + GL20.glGetShaderInfoLog(s, 4096));
        }
        return s;
    }

    /**
     * The framebuffer that is bound right now. The game's own Framebuffer object cannot be trusted for this: with
     * some mods (Patcher, OptiFine) its id field reads -1 while the game draws into another buffer entirely.
     */
    private static int boundFbo() {
        return GL11.glGetInteger(0x8CA6); // GL_DRAW_FRAMEBUFFER_BINDING
    }

    private int restoreTarget;

    private void restoreGameFramebuffer() {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, restoreTarget);
    }

    private void beginPass(int targetFbo, int prog, int srcTex, boolean additive) {
        restoreTarget = boundFbo();
        if (targetFbo < 0) {
            targetFbo = restoreTarget;
        }
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, 1, 0, 1, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, targetFbo);
        GL11.glViewport(0, 0, w, h);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_FOG);
        if (additive) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, srcTex);
        GL20.glUseProgram(prog);
        GL20.glUniform1i(GL20.glGetUniformLocation(prog, "tex"), 0);
    }

    private void endPass() {
        GL20.glUseProgram(0);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
        restoreGameFramebuffer();
    }

    private static void quad() {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(0, 0);
        GL11.glTexCoord2f(1, 0); GL11.glVertex2f(1, 0);
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(1, 1);
        GL11.glTexCoord2f(0, 1); GL11.glVertex2f(0, 1);
        GL11.glEnd();
    }

    private int checks;

    /** Debug aid: names the first accumulation step that raises an OpenGL error. */
    private void check(String where) {
        int e = GL11.glGetError();
        if (e != 0 && checks++ < 6) {
            SubFrameMod.LOG.warn("[accumulator] GL error " + e + " after " + where);
        }
    }

    int slots() {
        return fbo.length;
    }

    void clear(int slot) {
        restoreTarget = boundFbo();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo[slot]);
        GL11.glClearColor(0, 0, 0, 0);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glPopAttrib();
        restoreGameFramebuffer();
    }

    /** Copies the frame the game just drew, for {@link #addCaptured}. */
    void capture() {
        copyScreen();
    }

    /** Adds the captured frame to a slot, scaled by weight, in linear light. */
    void addCaptured(int slot, float weight) {
        beginPass(fbo[slot], progAdd, staging, true);
        GL20.glUniform1f(GL20.glGetUniformLocation(progAdd, "weight"), weight);
        quad();
        endPass();
    }

    /** Copies the frame that is bound for reading (the window's back buffer, or the game's own buffer) into the staging texture. */
    private void copyScreen() {
        GL11.glPushAttrib(GL11.GL_TEXTURE_BIT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, staging);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
        GL11.glPopAttrib();
    }

    /** Draws a slot's accumulated image into the game framebuffer as sRGB. */
    void resolve(int slot) {
        Framebuffer fb = Minecraft.getMinecraft().getFramebuffer();
        beginPass(fb.framebufferObject, progOut, tex[slot], false);
        quad();
        endPass();
    }

    void delete() {
        for (int i = 0; i < fbo.length; i++) {
            GL11.glDeleteTextures(tex[i]);
            OpenGlHelper.glDeleteFramebuffers(fbo[i]);
        }
        GL11.glDeleteTextures(staging);
        GL20.glDeleteProgram(progAdd);
        GL20.glDeleteProgram(progOut);
    }
}
