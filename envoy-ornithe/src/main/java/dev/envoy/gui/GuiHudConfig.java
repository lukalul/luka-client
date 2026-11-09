package dev.envoy.gui;

import dev.envoy.EnvoyConfig;
import dev.envoy.render.CooldownRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import java.util.Collections;
import java.util.List;

public final class GuiHudConfig extends GuiScreen {
    private static final int DONE = 0, RESET = 1, DIRECTION = 2;
    private final GuiScreen parent;
    private final List<HudElement> elements;
    private HudElement grabbed;
    private int grabX, grabY;
    private GuiButton direction;

    public GuiHudConfig(GuiScreen parent) {
        this.parent = parent;
        this.elements = Collections.<HudElement>singletonList(new CooldownElement());
    }

    @Override
    public void initGui() {
        buttonList.clear();
        direction = new GuiButton(DIRECTION, width / 2 - 100, height - 52, 200, 20, directionText());
        buttonList.add(direction);
        buttonList.add(new GuiButton(DONE, width / 2 - 152, height - 28, 150, 20, I18n.format("envoy.config.done")));
        buttonList.add(new GuiButton(RESET, width / 2 + 2, height - 28, 150, 20, I18n.format("envoy.config.reset")));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("envoy.config.title"), width / 2, 12, 0xFFFFFF);
        drawCenteredString(fontRendererObj, I18n.format("envoy.config.hint"), width / 2, 24, 0xFFAAAAAA);
        for (HudElement element : elements) paintElement(element);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void paintElement(HudElement element) {
        int x = Math.round(element.getX() * width);
        int y = Math.round(element.getY() * height);
        int tint = element == grabbed ? 0x804CAF50 : 0x40FFFFFF;
        drawRect(x - element.halfWidth(), y - element.halfHeight(), x + element.halfWidth(), y + element.halfHeight(), tint);
        element.renderPreview(x, y);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            for (HudElement candidate : elements) {
                if (!candidate.contains(mouseX, mouseY, width, height)) continue;
                grabbed = candidate;
                grabX = mouseX - Math.round(candidate.getX() * width);
                grabY = mouseY - Math.round(candidate.getY() * height);
                break;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long elapsed) {
        if (grabbed == null) return;
        grabbed.setX(screenFraction(mouseX - grabX, width));
        grabbed.setY(screenFraction(mouseY - grabY, height));
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        grabbed = null;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == DONE) mc.displayGuiScreen(parent);
        else if (button.id == RESET) {
            restoreDefaults();
            direction.displayString = directionText();
        } else if (button.id == DIRECTION) {
            EnvoyConfig.cooldownDirection = EnvoyConfig.cooldownDirection.next();
            direction.displayString = directionText();
        }
    }

    @Override
    public void onGuiClosed() {
        EnvoyConfig.save();
    }

    private static void restoreDefaults() {
        EnvoyConfig.cooldownX = EnvoyConfig.DEFAULT_COOLDOWN_X;
        EnvoyConfig.cooldownY = EnvoyConfig.DEFAULT_COOLDOWN_Y;
        EnvoyConfig.cooldownDirection = EnvoyConfig.CooldownDirection.RIGHT;
    }

    private static float screenFraction(int pixel, int extent) {
        float fraction = pixel / (float) extent;
        if (fraction < 0.02F) return 0.02F;
        return fraction > 0.98F ? 0.98F : fraction;
    }

    private static String directionText() {
        String directionName = I18n.format(EnvoyConfig.cooldownDirection.translationKey());
        return I18n.format("envoy.config.direction", directionName);
    }

    private static final class CooldownElement extends HudElement {
        @Override public String name() { return "cooldown"; }
        @Override public float getX() { return EnvoyConfig.cooldownX; }
        @Override public void setX(float value) { EnvoyConfig.cooldownX = value; }
        @Override public float getY() { return EnvoyConfig.cooldownY; }
        @Override public void setY(float value) { EnvoyConfig.cooldownY = value; }
        @Override public int halfWidth() { return 16; }
        @Override public int halfHeight() { return 16; }
        @Override public void renderPreview(int centerX, int centerY) {
            for (int slot = 0; slot < 3; slot++) {
                int[] point = CooldownRenderer.slotPosition(centerX, centerY, slot);
                CooldownRenderer.renderPreview(point[0], point[1]);
            }
        }
    }
}
