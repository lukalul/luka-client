package dev.cbui.gui;

import dev.cbui.Cbui;
import dev.cbui.font.LukaFontRenderer;
import dev.cbui.font.Fonts;
import dev.cbui.render.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import dev.luka.compat.VertexBuffer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import dev.luka.compat.DefaultVertexFormats;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/**
 * luka's title screen: a slowly turning panorama (blurred like the vanilla title screen), the luka logo with a ring that swings back and
 * forth, SINGLEPLAYER / MULTIPLAYER under it, a thin bar of text buttons along the top, a quit icon at the top right and the usual footer. The layout,
 * logo, colours and animation follow luka's own look; the entries for this client's extras sit in the top bar next to OPTIONS.
 * Subclasses {@link GuiMainMenu} so other code that expects the title screen type keeps working; everything is drawn here.
 */
public final class LukaMainMenu extends GuiMainMenu {
    private static final ResourceLocation OUTER = new ResourceLocation("cbui", "logo_255_outer.png");
    private static final ResourceLocation INNER = new ResourceLocation("cbui", "logo_108_inner.png");
    private static final ResourceLocation SMALL = new ResourceLocation("cbui", "logo_42.png");
    private static final ResourceLocation QUIT = new ResourceLocation("cbui", "icons/delete-64.png");
    private static final ResourceLocation[] PANORAMA = new ResourceLocation[6];
    static {
        for (int i = 0; i < 6; i++) PANORAMA[i] = new ResourceLocation("cbui", "panorama/" + i + ".png");
    }
    private static final int RED = 0xFF8942DA;
    private static final float RADIUS = 27;

    private static boolean introPlayed;

    private final List<Button> mainButtons = new ArrayList<>();
    private final List<Button> topButtons = new ArrayList<>();
    private net.minecraft.client.shader.Framebuffer panoramaImage, panoramaCopy;
    private long openedAt;
    private int panoramaTicks;
    private boolean intro;
    private boolean panoramaFailed;
    @Override public void initGui() {
        try {
            openedAt = System.currentTimeMillis();
            intro = !introPlayed;
            panoramaImage = new net.minecraft.client.shader.Framebuffer(256, 256, false);
            panoramaCopy = new net.minecraft.client.shader.Framebuffer(256, 256, false);
            panoramaImage.setFramebufferFilter(9729);
            panoramaCopy.setFramebufferFilter(9729);

            mainButtons.clear();
            topButtons.clear();
            mainButtons.add(new Button("SINGLEPLAYER", () -> mc.displayGuiScreen(new GuiSelectWorld(this))));
            mainButtons.add(new Button("MULTIPLAYER", () -> mc.displayGuiScreen(new GuiMultiplayer(this))));
            topButtons.add(new Button("OPTIONS", () -> mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings))));
            topButtons.add(new Button("MODULES", () -> mc.displayGuiScreen(new ModuleMenu(this))));
            topButtons.add(new Button("MODS", () -> mc.displayGuiScreen(new ModsScreen(this))));
            if (dev.cbui.Release.SOCIAL) {
                topButtons.add(new Button("COSMETICS", () -> mc.displayGuiScreen(new CosmeticsScreen(this))));
                topButtons.add(new Button("FRIENDS", () -> mc.displayGuiScreen(new FriendsScreen(this))));
            }
            if (classExists("dev.subframe.ReplayManagerScreen")) topButtons.add(new Button("REPLAYS", () -> openScreen("dev.subframe.ReplayManagerScreen")));
            layout();
        } catch (Throwable error) { fallback("initialization", error); }
    }

    private void layout() {
        float bx = width / 2f - 50;
        mainButtons.get(0).set(bx, height / 2f + 5, 100, 12);
        mainButtons.get(1).set(bx, height / 2f + 24, 100, 12);
        LukaFontRenderer font = Fonts.regular();
        float limit = width - 40;
        float x = 124;
        for (int i = 0; i < topButtons.size(); i++) {
            Button b = topButtons.get(i);
            float w = font.getStringWidth(b.label) + 12;
            b.set(x, 6, w, 20);
            x += w + 2;
        }
        while (topButtons.size() > 3 && topButtons.get(topButtons.size() - 1).x + topButtons.get(topButtons.size() - 1).w > limit) topButtons.remove(topButtons.size() - 1);
    }

    @Override public void onGuiClosed() {
        try {
            if (panoramaImage != null) panoramaImage.deleteFramebuffer();
            if (panoramaCopy != null) panoramaCopy.deleteFramebuffer();
        } catch (Throwable ignored) { }
        panoramaImage = panoramaCopy = null;
    }

    @Override public void updateScreen() { panoramaTicks++; }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        try {
            long now = System.currentTimeMillis();
            float intro = this.intro ? Math.min(1f, (now - openedAt) / 750f) : 1f;
            float slide = intro * intro * (3 - 2 * intro);

            GlStateManager.disableAlpha();
            drawBackground(partialTicks);
            GlStateManager.enableAlpha();
            drawGradientRect(0, 0, width, height, 0x5FFFFFFF, 0x2FFFFFFF);
            drawGradientRect(0, 0, width, 160, 0xDF000000, 0);

            drawTopBar(mouseX, mouseY, now);

            Button single = mainButtons.get(0), multi = mainButtons.get(1);
            drawRect((int)(single.x - 20), (int)(height / 2f - 80), (int)(single.x + single.w + 20), (int)(multi.y + multi.h + 14), 0x2F000000);
            for (Button b : mainButtons) b.drawGradient(mouseX, mouseY, now);
            drawLogo(now, slide);

            LukaFontRenderer small = Fonts.regular();
            small.drawStringWithShadow("luka " + Cbui.VERSION, 5, height - 14, 0x70FFFFFF);
            String legal = "luka is an independent client and is not affiliated with Mojang or Microsoft. Minecraft is a trademark of Mojang AB.";
            small.drawStringWithShadow(legal, width - small.getStringWidth(legal) - 5, height - 14, 0x70FFFFFF);

            if (this.intro && now - openedAt < 400) drawRect(0, 0, width, height, ((int)(255 * (1f - (now - openedAt) / 400f)) << 24));
            if (this.intro && now - openedAt > 1200) introPlayed = true;
        } catch (Throwable error) { fallback("drawing", error); }
    }

    private void drawTopBar(int mouseX, int mouseY, long now) {
        boolean overTitle = mouseX < topButtons.get(0).x && mouseY < 30;
        GlStateManager.color(1, 1, 1, 1);
        Draw.texture(SMALL, 8, 6, 20, 20, 1f);
        Fonts.bold(15).drawString("luka", 36, 8, overTitle ? RED : 0xFFD9C2FF);
        for (Button b : topButtons) b.drawText(mouseX, mouseY, now);

        String name = mc.getSession().getUsername();
        LukaFontRenderer font = Fonts.regular();
        float nameLeft = width - 35 - font.getStringWidth(name);
        Button last = topButtons.get(topButtons.size() - 1);
        boolean overName = Draw.inside(mouseX, mouseY, nameLeft - 4, 4, font.getStringWidth(name) + 8, 22);
        nameX = nameLeft - 4;
        nameW = font.getStringWidth(name) + 8;
        if (last.x + last.w + 10 < nameLeft) font.drawStringWithShadow(name, nameLeft, 11, overName ? 0xFFD9C2FF : 0xFFFFFFFF);
        // account chip, top left: the account in use; a click opens the account switcher
        String pending = dev.cbui.account.AccountLauncher.status;
        String chip = pending.isEmpty() ? name + "   |   ACCOUNTS" : pending;
        chipW = font.getStringWidth(chip) + 16;
        boolean overChip = Draw.inside(mouseX, mouseY, 8, 34, chipW, 18);
        Draw.rounded(8, 34, 8 + chipW, 52, 5, overChip ? 0xB02F2540 : 0x90140F1C);
        font.drawStringWithShadow(chip, 16, 39, overChip ? 0xFFD9C2FF : 0xFFE9DDFB);
        boolean overQuit = Draw.inside(mouseX, mouseY, width - 30, 7, 23, 17);
        GlStateManager.color(1, 1, 1, 1);
        Draw.texture(QUIT, width - 26.5f, 9, 14, 14, overQuit ? 1f : 0.75f);
        if (overQuit) Draw.rect(width - 30, 25, width - 7, 26, RED);
    }

    private void drawLogo(long now, float slide) {
        float x0 = width / 2f - RADIUS;
        float y0 = height / 2f - RADIUS - 35 * slide;
        float swing = ((now % 8000L) / 4000f);
        if (swing > 1f) swing = 2f - swing;
        float angle = 180f * (1f - MathHelper.cos(swing * (float)Math.PI)) / 2f;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x0 + RADIUS, y0 + RADIUS, 0);
        GlStateManager.rotate(angle, 0, 0, 1);
        GlStateManager.translate(-RADIUS, -RADIUS, 0);
        Draw.texture(OUTER, 0, 0, RADIUS * 2, RADIUS * 2, 1f);
        GlStateManager.popMatrix();
        Draw.texture(INNER, x0, y0, RADIUS * 2, RADIUS * 2, 1f);
        if (slide < 1f) {
            float bx = width / 2f - 80, by = height - 40;
            Draw.rounded(bx, by, bx + 160, by + 10, 8, (((int)(255 * (1f - slide))) << 24) | (RED & 0xFFFFFF));
        }
    }

    // ---- background: the vanilla title screen's panorama + blur, with luka's own six pictures ----

    private void drawBackground(float partialTicks) {
        if (!panoramaFailed) {
            try { renderSkybox(partialTicks); return; }
            catch (Throwable error) { panoramaFailed = true; Cbui.reportOnce("menu panorama", error); mc.getFramebuffer().bindFramebuffer(true); GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight); }
        }
        drawGradientRect(0, 0, width, height, 0xFF100C13, 0xFF211A29);
    }

    private void renderSkybox(float partialTicks) {
        panoramaImage.bindFramebuffer(true);
        drawPanorama(partialTicks);
        for (int i = 0; i < 7; i++) rotateAndBlur();
        mc.getFramebuffer().bindFramebuffer(true);
        GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
        panoramaImage.bindFramebufferTexture();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        float f = width > height ? 120f / width : 120f / height;
        float f1 = height * f / 256f;
        float f2 = width * f / 256f;
        Tessellator tessellator = Tessellator.instance;
        VertexBuffer wr = dev.luka.compat.VertexBuffer.INSTANCE;
        wr.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        wr.pos(0, height, zLevel).tex(0.5f - f1, 0.5f + f2).color(1f, 1f, 1f, 1f).endVertex();
        wr.pos(width, height, zLevel).tex(0.5f - f1, 0.5f - f2).color(1f, 1f, 1f, 1f).endVertex();
        wr.pos(width, 0, zLevel).tex(0.5f + f1, 0.5f - f2).color(1f, 1f, 1f, 1f).endVertex();
        wr.pos(0, 0, zLevel).tex(0.5f + f1, 0.5f + f2).color(1f, 1f, 1f, 1f).endVertex();
        tessellator.draw();
        panoramaImage.unbindFramebufferTexture();
    }

    private void drawPanorama(float partialTicks) {
        Tessellator tessellator = Tessellator.instance;
        VertexBuffer wr = dev.luka.compat.VertexBuffer.INSTANCE;
        GlStateManager.matrixMode(5889);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        Project.gluPerspective(120f, 1f, 0.05f, 10f);
        GlStateManager.matrixMode(5888);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.rotate(180f, 1f, 0f, 0f);
        GlStateManager.rotate(90f, 0f, 0f, 1f);
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        int passes = 8;
        for (int k = 0; k < passes * passes; k++) {
            GlStateManager.pushMatrix();
            float tx = ((float)(k % passes) / passes - 0.5f) / 64f;
            float ty = ((float)(k / passes) / passes - 0.5f) / 64f;
            GlStateManager.translate(tx, ty, 0f);
            GlStateManager.rotate(MathHelper.sin((panoramaTicks + partialTicks) / 400f) * 25f + 20f, 1f, 0f, 0f);
            GlStateManager.rotate(-(panoramaTicks + partialTicks) * 0.1f, 0f, 1f, 0f);
            for (int face = 0; face < 6; face++) {
                GlStateManager.pushMatrix();
                if (face == 1) GlStateManager.rotate(90f, 0f, 1f, 0f);
                if (face == 2) GlStateManager.rotate(180f, 0f, 1f, 0f);
                if (face == 3) GlStateManager.rotate(-90f, 0f, 1f, 0f);
                if (face == 4) GlStateManager.rotate(90f, 1f, 0f, 0f);
                if (face == 5) GlStateManager.rotate(-90f, 1f, 0f, 0f);
                mc.getTextureManager().bindTexture(PANORAMA[face]);
                wr.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
                int alpha = 255 / (k + 1);
                wr.pos(-1, -1, 1).tex(0, 0).color(255, 255, 255, alpha).endVertex();
                wr.pos(1, -1, 1).tex(1, 0).color(255, 255, 255, alpha).endVertex();
                wr.pos(1, 1, 1).tex(1, 1).color(255, 255, 255, alpha).endVertex();
                wr.pos(-1, 1, 1).tex(0, 1).color(255, 255, 255, alpha).endVertex();
                tessellator.draw();
                GlStateManager.popMatrix();
            }
            GlStateManager.popMatrix();
            GlStateManager.colorMask(true, true, true, false);
        }
        wr.setTranslation(0, 0, 0);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.matrixMode(5889);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(5888);
        GlStateManager.popMatrix();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
    }

    private void rotateAndBlur() {
        // copy the picture so far, then blend the copy back over it, slightly shifted: the two framebuffers stand in for a copy of the screen
        panoramaCopy.bindFramebuffer(true);
        panoramaImage.bindFramebufferTexture();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.color(1f, 1f, 1f, 1f);
        Tessellator copyTessellator = Tessellator.instance;
        VertexBuffer copy = dev.luka.compat.VertexBuffer.INSTANCE;
        copy.begin(7, DefaultVertexFormats.POSITION_TEX);
        copy.pos(0, height, zLevel).tex(0f, 0f).endVertex();
        copy.pos(width, height, zLevel).tex(1f, 0f).endVertex();
        copy.pos(width, 0, zLevel).tex(1f, 1f).endVertex();
        copy.pos(0, 0, zLevel).tex(0f, 1f).endVertex();
        copyTessellator.draw();
        panoramaImage.unbindFramebufferTexture();
        panoramaImage.bindFramebuffer(true);
        panoramaCopy.bindFramebufferTexture();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.colorMask(true, true, true, false);
        Tessellator tessellator = Tessellator.instance;
        VertexBuffer wr = dev.luka.compat.VertexBuffer.INSTANCE;
        wr.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        GlStateManager.disableAlpha();
        int layers = 3;
        for (int j = 0; j < layers; j++) {
            float alpha = 1f / (j + 1);
            float shift = (float)(j - layers / 2) / 256f;
            wr.pos(width, height, zLevel).tex(0f + shift, 1f).color(1f, 1f, 1f, alpha).endVertex();
            wr.pos(width, 0, zLevel).tex(1f + shift, 1f).color(1f, 1f, 1f, alpha).endVertex();
            wr.pos(0, 0, zLevel).tex(1f + shift, 0f).color(1f, 1f, 1f, alpha).endVertex();
            wr.pos(0, height, zLevel).tex(0f + shift, 0f).color(1f, 1f, 1f, alpha).endVertex();
        }
        tessellator.draw();
        panoramaCopy.unbindFramebufferTexture();
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.colorMask(true, true, true, true);
    }

    // ---- input ----

    private float nameX, nameW, chipW;

    @Override protected void mouseClicked(int mouseX, int mouseY, int button) {
        try {
            if (button != 0) return;
            if (chipW > 0 && Draw.inside(mouseX, mouseY, 8, 34, chipW, 18)) { click(null); openAccounts(); return; }
            if (nameW > 0 && Draw.inside(mouseX, mouseY, nameX, 4, nameW, 22)) { click(null); openAccounts(); return; }
            for (Button b : mainButtons) if (b.inside(mouseX, mouseY)) { click(b); return; }
            for (Button b : topButtons) if (b.inside(mouseX, mouseY)) { click(b); return; }
            if (Draw.inside(mouseX, mouseY, width - 30, 7, 23, 17)) { click(null); mc.shutdown(); }
        } catch (Throwable error) { fallback("mouse input", error); }
    }

    /** The launcher's account window does the picking and signing in; the in-game screen is only the fallback when there is no launcher. */
    private void openAccounts() {
        if (dev.cbui.account.AccountLauncher.busy()) return;
        if (!dev.cbui.account.AccountLauncher.open()) mc.displayGuiScreen(new AccountScreen(this));
    }

    private void click(Button b) {
        mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.createPositionedSoundRecord(new ResourceLocation("gui.button.press"), 1.0f));
        if (b != null) b.action.run();
    }

    @Override protected void keyTyped(char typedChar, int keyCode) {
        // Escape does nothing here: the luka menu is the title screen
    }

    private void unusedKeyHook(int keyCode) {
        try { if (keyCode == -1) { Cbui.openVanillaMenuOnce(); mc.displayGuiScreen(new GuiMainMenu()); } }
        catch (Throwable error) { fallback("keyboard input", error); }
    }

    private void fallback(String area, Throwable error) {
        Cbui.reportOnce("main-menu " + area, error);
        Cbui.openVanillaMenuOnce();
        mc.displayGuiScreen(new GuiMainMenu());
    }

    // ---- optional entries ----

    private static boolean classExists(String name) {
        try { Class.forName(name, false, LukaMainMenu.class.getClassLoader()); return true; } catch (Throwable ignored) { return false; }
    }

    private void openScreen(String name) {
        try {
            Class<?> type = Class.forName(name);
            Object instance = null;
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                Class<?>[] p = constructor.getParameterTypes();
                if (p.length == 0) { constructor.setAccessible(true); instance = constructor.newInstance(); break; }
                if (p.length == 1 && GuiScreen.class.isAssignableFrom(p[0])) { constructor.setAccessible(true); instance = constructor.newInstance(this); break; }
            }
            if (!(instance instanceof GuiScreen)) throw new IllegalStateException(name + " has no supported GuiScreen constructor");
            mc.displayGuiScreen((GuiScreen)instance);
        } catch (Throwable error) { Cbui.reportOnce("optional screen " + name, error); }
    }


    /** One clickable label: either a dark gradient bar or a plain text entry for the top bar. */
    private static final class Button {
        final String label;
        final Runnable action;
        float x, y, w, h;
        private float hover;
        private long lastFrame;

        Button(String label, Runnable action) { this.label = label; this.action = action; }

        void set(float x, float y, float w, float h) { this.x = x; this.y = y; this.w = w; this.h = h; }
        boolean inside(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }

        private void fade(boolean over, long now) {
            float dt = lastFrame == 0 ? 0 : Math.min(100, now - lastFrame) / 150f;
            lastFrame = now;
            hover = over ? Math.min(1f, hover + dt) : Math.max(0f, hover - dt);
        }

        void drawGradient(int mx, int my, long now) {
            fade(inside(mx, my), now);
            int left = Draw.lerpColor(0xFF262626, 0xFF7550A0, hover);
            int mid = Draw.lerpColor(0xFF2A2A2A, 0xFF7A55A5, hover);
            int right = Draw.lerpColor(0xFF323232, 0xFF8C64B9, hover);
            Draw.gradientH(x, y, x + w / 2f, y + h, left, mid);
            Draw.gradientH(x + w / 2f, y, x + w, y + h, mid, right);
            LukaFontRenderer font = Fonts.regular();
            font.drawCentered(label, x + w / 2f, y + 0.5f, 0xFFFFFFFF);
        }

        void drawText(int mx, int my, long now) {
            fade(inside(mx, my), now);
            LukaFontRenderer font = Fonts.regular();
            font.drawCentered(label, x + w / 2f, y + 6, Draw.lerpColor(0xFFFFFFFF, RED, hover));
            if (hover > 0.01f) Draw.rect(x + 4, y + 17, x + w - 4, y + 18, (((int)(255 * hover)) << 24) | (RED & 0xFFFFFF));
        }
    }
}
