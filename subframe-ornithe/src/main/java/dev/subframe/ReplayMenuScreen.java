package dev.subframe;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** Viewer menu: change game options while watching, pick the render quality, start the render. */
final class ReplayMenuScreen extends GuiScreen {
    private static final int[] FPS = {30, 60, 120, 240, 480, 960};
    private static final int[] SUBFRAMES = {1, 4, 8, 16, 32, 64, 128};

    private static final int RESUME = 0, OPTIONS = 1, FPS_BTN = 2, SUB_BTN = 3, RENDER = 4, QUIT = 5, BLEND_BTN = 6, OUT_BTN = 7, ENC_BTN = 8, GAME_MENU_BTN = 9;

    private GuiButton fpsButton, subButton, blendButton, outButton, encButton;

    @Override
    public void initGui() {
        buttonList.clear();
        int x = width / 2 - 100;
        int y = height / 4;
        buttonList.add(new GuiButton(RESUME, x, y, 200, 20, "Back to replay"));
        buttonList.add(new GuiButton(OPTIONS, x, y + 28, 200, 20, "Options (video, resource packs, shaders)..."));
        buttonList.add(new GuiButton(GAME_MENU_BTN, x, y + 52, 200, 20, "Game menu (other mods' settings)..."));
        fpsButton = new GuiButton(FPS_BTN, x, y + 90, 200, 20, "");
        blendButton = new GuiButton(BLEND_BTN, x, y + 114, 200, 20, "");
        subButton = new GuiButton(SUB_BTN, x, y + 138, 200, 20, "");
        outButton = new GuiButton(OUT_BTN, x, y + 162, 200, 20, "");
        buttonList.add(fpsButton);
        buttonList.add(blendButton);
        buttonList.add(subButton);
        buttonList.add(outButton);
        encButton = new GuiButton(ENC_BTN, x, y + 186, 200, 20, "");
        buttonList.add(encButton);
        Replay active = Replay.active;
        String sel = active != null ? active.rangeLabel() : null;
        buttonList.add(new GuiButton(RENDER, x, y + 218, 200, 20, sel == null ? "Render video with these settings" : "Render " + sel + " with these settings"));
        buttonList.add(new GuiButton(QUIT, x, y + 246, 200, 20, "Close replay"));
        refreshLabels();
    }

    private void refreshLabels() {
        fpsButton.displayString = "Video fps: " + SubFrameMod.outputFps;
        blendButton.displayString = "Blending in mod: " + (SubFrameMod.blend ? "ON" : "OFF (raw high-fps frames)");
        subButton.displayString = SubFrameMod.blend ? "Subframes per frame: " + SubFrameMod.subframes : "Subframes: n/a (blending off)";
        subButton.enabled = SubFrameMod.blend;
        encButton.displayString = "Encoder: " + Encoders.label();
        outButton.displayString = "Output: " + (SubFrameMod.outputPng ? "PNG folder" : "video file");
    }

    private static int next(int[] values, int current) {
        for (int v : values) {
            if (v > current) {
                return v;
            }
        }
        return values[0];
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        Replay r = Replay.active;
        if (r == null) {
            return;
        }
        switch (b.id) {
            case RESUME:
                mc.displayGuiScreen(r.screen());
                break;
            case OPTIONS:
                mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings));
                break;
            case FPS_BTN:
                SubFrameMod.outputFps = next(FPS, SubFrameMod.outputFps);
                refreshLabels();
                break;
            case SUB_BTN:
                SubFrameMod.subframes = next(SUBFRAMES, SubFrameMod.subframes);
                refreshLabels();
                break;
            case BLEND_BTN:
                SubFrameMod.blend = !SubFrameMod.blend;
                refreshLabels();
                break;
            case OUT_BTN:
                SubFrameMod.outputPng = !SubFrameMod.outputPng;
                refreshLabels();
                break;
            case GAME_MENU_BTN:
                mc.displayGuiScreen(new ReplayPauseMenu());
                break;
            case ENC_BTN:
                SubFrameMod.encoderMode = Encoders.nextMode();
                refreshLabels();
                break;
            case RENDER:
                mc.displayGuiScreen(r.screen());
                r.startRender();
                break;
            case QUIT:
                r.quit();
                break;
            default:
                break;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            Replay r = Replay.active;
            if (r != null) {
                mc.displayGuiScreen(r.screen());
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "SubFrame replay", width / 2, height / 4 - 20, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
