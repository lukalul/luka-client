package dev.envoy.render;

import dev.envoy.LunarHost;
import org.lwjgl.BufferUtils;
import java.nio.IntBuffer;
import java.nio.ByteBuffer;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureUtil;

public final class GlowFramebuffer {

    private static final int GL_DEPTH_COMPONENT24 = 33190;
    private static final int GL_FRAMEBUFFER_BINDING = 36006;

    private final int width;
    private final int height;
    private int framebufferObject = -1;
    private int framebufferTexture = -1;
    private int depthBuffer = -1;

    public GlowFramebuffer(int width, int height) {
        this.width = width;
        this.height = height;
        this.create();
    }

    private void create() {
        this.framebufferObject = OpenGlHelper.m_25440982();
        this.framebufferTexture = TextureUtil.glGenTextures();
        this.depthBuffer = OpenGlHelper.m_15601432();
        allocateColorTexture();
        attachBuffers();
        GlStateManager.bindTexture(0);
        OpenGlHelper.m_06725793(OpenGlHelper.f_84571442, 0);
    }

    private void allocateColorTexture() {
        GlStateManager.bindTexture(this.framebufferTexture);
        GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 10496.0F);
        GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 10496.0F);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, this.width, this.height, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
    }

    private void attachBuffers() {
        OpenGlHelper.m_06725793(OpenGlHelper.f_84571442, this.framebufferObject);
        OpenGlHelper.m_06264030(OpenGlHelper.f_84571442, OpenGlHelper.f_03042584,
            GL11.GL_TEXTURE_2D, this.framebufferTexture, 0);

        OpenGlHelper.m_99919886(OpenGlHelper.f_50936782, this.depthBuffer);
        OpenGlHelper.m_24921241(OpenGlHelper.f_50936782, GL_DEPTH_COMPONENT24, this.width, this.height);
        OpenGlHelper.m_01372162(OpenGlHelper.f_84571442, OpenGlHelper.f_16625824,
            OpenGlHelper.f_50936782, this.depthBuffer);
    }

    private int previousFramebuffer = -1;
    private final int[] previousViewport = new int[4];

    public void bindFramebuffer() {
        if (LunarHost.isLunar()) {
            this.previousFramebuffer = GL11.glGetInteger(GL_FRAMEBUFFER_BINDING);
            IntBuffer viewport = BufferUtils.createIntBuffer(4);
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
            viewport.get(this.previousViewport, 0, 4);
        }
        OpenGlHelper.m_06725793(OpenGlHelper.f_84571442, this.framebufferObject);
        GlStateManager.viewport(0, 0, this.width, this.height);
        GlStateManager.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    public void unbindFramebuffer() {
        if (this.previousFramebuffer >= 0) {
            OpenGlHelper.m_06725793(OpenGlHelper.f_84571442, this.previousFramebuffer);
            GL11.glViewport(this.previousViewport[0], this.previousViewport[1],
                this.previousViewport[2], this.previousViewport[3]);
            this.previousFramebuffer = -1;
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (OpenGlHelper.isFramebufferEnabled()) {
            mc.getFramebuffer().bindFramebuffer(true);
        } else {
            OpenGlHelper.m_06725793(OpenGlHelper.f_84571442, 0);
            GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
        }
    }

    public int getFramebufferTexture() {
        return this.framebufferTexture;
    }

    public void cleanup() {
        releaseDepth();
        releaseColor();
        releaseFramebuffer();
    }

    private void releaseDepth() {
        if (this.depthBuffer >= 0) {
            OpenGlHelper.m_68927372(this.depthBuffer);
            this.depthBuffer = -1;
        }
    }

    private void releaseColor() {
        if (this.framebufferTexture >= 0) {
            TextureUtil.deleteTexture(this.framebufferTexture);
            this.framebufferTexture = -1;
        }
    }

    private void releaseFramebuffer() {
        if (this.framebufferObject >= 0) {
            OpenGlHelper.m_06725793(OpenGlHelper.f_84571442, 0);
            OpenGlHelper.m_34336064(this.framebufferObject);
            this.framebufferObject = -1;
        }
    }
}
