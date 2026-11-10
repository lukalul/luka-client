package dev.cbui.render;

import dev.cbui.Cbui;
import dev.cbui.hud.AbstractHudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.gui.inventory.GuiEditSign;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import dev.luka.compat.VertexBuffer;
import dev.luka.compat.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * "GUI blur": while a menu is open in a world, the picture behind it is blurred (the game's own two-pass blur post-effect, radius 20).
 * It costs nothing during play: the effect is loaded when a menu opens and removed the moment it closes, and only effects this class started are removed.
 * Where post-effect shaders cannot load (Vulkan), the picture is shrunk step by step and stretched back instead, which needs only framebuffers.
 */
public final class GuiBlur {
    private static final ResourceLocation BLUR = new ResourceLocation("shaders/post/blur.json");
    private static final int STEPS = 4;
    private static boolean started;
    private static boolean broken;
    /** Vulkan has no GLSL post-effects: going straight to the fallback saves a failed load and its log spam on every first menu. */
    private static boolean shaderUnavailable = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("astatine");
    private static boolean scaling;
    private static final Framebuffer[] chain = new Framebuffer[STEPS];
    private static int chainWidth, chainHeight;

    private GuiBlur() { }

    public static void onScreen(GuiScreen screen) {
        if (broken) return;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            boolean want = screen != null && mc.theWorld != null && eligible(screen) && enabled();
            if (want == started) return;
            if (want) {
                if (mc.entityRenderer.isShaderActive()) return; // someone else's effect is running: leave it alone
                if (!shaderUnavailable) {
                    cbui$startBlur(mc);
                    shaderUnavailable = !mc.entityRenderer.isShaderActive(); // the game logs and swallows the failure, so asking once is enough
                }
                scaling = shaderUnavailable;
                started = true;
            } else {
                if (!scaling) mc.entityRenderer.deactivateShader();
                scaling = false;
                started = false;
            }
        } catch (Throwable error) {
            broken = true;
            started = false;
            scaling = false;
            Cbui.reportOnce("GUI blur", error);
        }
    }

    /** Called once per frame right after the world was drawn, before the HUD and the menu. */
    public static void afterWorld() {
        if (!scaling || broken) return;
        try {
            blurByScaling(Minecraft.getMinecraft());
        } catch (Throwable error) {
            broken = true;
            scaling = false;
            Cbui.reportOnce("GUI blur", error);
        }
    }

    private static void blurByScaling(Minecraft mc) {
        Framebuffer main = mc.getFramebuffer();
        int width = main.framebufferWidth, height = main.framebufferHeight;
        if (chain[0] == null || width != chainWidth || height != chainHeight) rebuild(width, height);
        Framebuffer previous = main;
        for (int step = 0; step < STEPS; step++) {
            copy(previous, chain[step]);
            previous = chain[step];
        }
        for (int step = STEPS - 2; step >= 0; step--) {
            copy(previous, chain[step]);
            previous = chain[step];
        }
        copy(previous, main);
        GlStateManager.enableAlpha();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void copy(Framebuffer from, Framebuffer to) {
        to.bindFramebuffer(true);
        int width = to.framebufferWidth, height = to.framebufferHeight;
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, width, height, 0.0D, 1000.0D, 3000.0D);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.0F, 0.0F, -2000.0F);
        GlStateManager.viewport(0, 0, width, height);
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.disableCull(); // Framebuffer.framebufferRender does not draw on Vulkan, it leaves culling on
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        from.bindFramebufferTexture();
        Tessellator tessellator = Tessellator.instance;
        VertexBuffer buffer = dev.luka.compat.VertexBuffer.INSTANCE;
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(0.0D, height, 0.0D).tex(0.0D, 0.0D).endVertex();
        buffer.pos(width, height, 0.0D).tex(1.0D, 0.0D).endVertex();
        buffer.pos(width, 0.0D, 0.0D).tex(1.0D, 1.0D).endVertex();
        buffer.pos(0.0D, 0.0D, 0.0D).tex(0.0D, 1.0D).endVertex();
        tessellator.draw();
        from.unbindFramebufferTexture();
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
    }

    private static void rebuild(int width, int height) {
        for (int step = 0; step < STEPS; step++) {
            if (chain[step] != null) chain[step].deleteFramebuffer();
            int divide = 2 << step;
            chain[step] = new Framebuffer(Math.max(1, width / divide), Math.max(1, height / divide), false);
            chain[step].setFramebufferFilter(GL11.GL_LINEAR);
        }
        chainWidth = width;
        chainHeight = height;
    }

    private static boolean enabled() {
        if (Cbui.hud() == null) return false;
        AbstractHudModule module = Cbui.hud().find("GUI blur");
        return module != null && module.enabled();
    }

    /** Books, signs and the death screen stay sharp so they stay readable and cheap. The replay viewer's screen is only its timeline over the picture: blurring it blurred the whole replay. */
    private static boolean eligible(GuiScreen screen) {
        if (screen.getClass().getName().equals("dev.subframe.ReplayScreen")) return false;
        // chat, the inventory and the other container screens are blurred too (the blur is behind the GUI, so text and items stay sharp)
        return !(screen instanceof GuiScreenBook) && !(screen instanceof GuiEditSign) && !(screen instanceof GuiGameOver);
    }

    /** What 1.8's EntityRenderer.loadShader does: 1.7.10 only cycles through its built-in effects. */
    private static void cbui$startBlur(Minecraft mc) {
        if (!net.minecraft.client.renderer.OpenGlHelper.shadersSupported) return;
        try {
            net.minecraft.client.shader.ShaderGroup group = new net.minecraft.client.shader.ShaderGroup(mc.getTextureManager(), mc.getResourceManager(), mc.getFramebuffer(), BLUR);
            group.createBindFramebuffers(mc.displayWidth, mc.displayHeight);
            ((dev.cbui.mixin.EntityRendererInvoker) mc.entityRenderer).cbui$setShaderGroup(group);
        } catch (Exception error) {
            System.out.println("[luka] blur shader unavailable: " + error);
        }
    }
}
