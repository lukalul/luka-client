package dev.subframe;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** Viewer menu: change game options while watching, pick the render quality, start the render. */
final class ReplayMenuScreen extends GuiScreen {
    private static final int[] FPS = {30, 60, 120, 240, 480, 960};
    /** Same bounds as {@code outputFps} in subframe.properties. */
    private static final int MIN_FPS = 1, MAX_FPS = 1000;
    private static final int[] SUBFRAMES = {1, 4, 8, 16, 32, 64, 128};

    private static final int RESUME = 0, OPTIONS = 1, FPS_BTN = 2, SUB_BTN = 3, RENDER = 4, QUIT = 5, BLEND_BTN = 6, OUT_BTN = 7, ENC_BTN = 8, GAME_MENU_BTN = 9;

    private GuiButton fpsButton, subButton, blendButton, outButton, encButton;
    /** Any video fps typed in by hand; the button beside it still steps through the presets. */
    private GuiTextField fpsField;
    private int boxX, boxY;
    private static final int BOX_W = 48, BOX_H = 18;

    @Override
    public void initGui() {
        buttonList.clear();
        int x = width / 2 - 100;
        int y = height / 4;
        buttonList.add(new GuiButton(RESUME, x, y, 200, 20, "Back to replay"));
        buttonList.add(new GuiButton(OPTIONS, x, y + 28, 200, 20, "Options (video, resource packs, shaders)..."));
        buttonList.add(new GuiButton(GAME_MENU_BTN, x, y + 52, 200, 20, "Game menu (other mods' settings)..."));
        fpsButton = new GuiButton(FPS_BTN, x, y + 90, 146, 20, "");
        Keyboard.enableRepeatEvents(true);
        fpsField = new GuiTextField(10, fontRendererObj, boxX = x + 151, boxY = y + 91, BOX_W, BOX_H);
        fpsField.setMaxStringLength(4);
        fpsField.setText(Integer.toString(SubFrameMod.outputFps));
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

    /** Red while the typed text is not a usable fps. */
    private void colourTypedFps() {
        fpsField.setTextColor(parseFps(fpsField.getText()) > 0 ? 0xE0E0E0 : 0xFF5555);
    }

    /** Takes the typed fps (Enter, a click elsewhere, leaving the menu); anything invalid goes back to the fps in use. */
    private void commitTypedFps() {
        int fps = parseFps(fpsField.getText());
        if (fps > 0) {
            SubFrameMod.outputFps = fps;
            fpsButton.displayString = "Video fps: " + fps;
        }
        resetField();
    }

    /** The fps box's text (self-test). */
    String fpsText() {
        return fpsField.getText();
    }

    private static int parseFps(String text) {
        try {
            int fps = Integer.parseInt(text.trim());
            return fps >= MIN_FPS && fps <= MAX_FPS ? fps : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Puts the box back to the fps in use (after a preset click, or when it is left with something invalid in it). */
    private void resetField() {
        fpsField.setText(Integer.toString(SubFrameMod.outputFps));
        fpsField.setTextColor(0xE0E0E0);
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
                resetField();
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
        if (fpsField.isFocused() && keyCode != Keyboard.KEY_ESCAPE) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                commitTypedFps();
                fpsField.setFocused(false);
                return;
            }
            // some clients deliver the key without its character: digits only need the key's name
            String name = Keyboard.getKeyName(keyCode);
            char c = typedChar == 0 && name != null && name.length() == 1 && Character.isDigit(name.charAt(0)) ? name.charAt(0) : typedChar;
            if (Character.isDigit(c) || c < ' ') {
                fpsField.textboxKeyTyped(c, keyCode);
                colourTypedFps();
            }
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (fpsField.isFocused()) {
                commitTypedFps();
            }
            Replay r = Replay.active;
            if (r != null) {
                mc.displayGuiScreen(r.screen());
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        boolean inBox = mouseX >= boxX && mouseX < boxX + BOX_W && mouseY >= boxY && mouseY < boxY + BOX_H;
        if (fpsField.isFocused() && !inBox) {
            // before the buttons see the click, so Render (or the preset button) works from the typed fps
            commitTypedFps();
        }
        super.mouseClicked(mouseX, mouseY, button);
        boolean was = fpsField.isFocused();
        fpsField.mouseClicked(mouseX, mouseY, button);
        if (!was && fpsField.isFocused()) {
            // select the whole value, so typing replaces it instead of landing beside the digit that was clicked
            fpsField.setCursorPositionEnd();
            fpsField.setSelectionPos(0);
        }
    }

    @Override
    public void updateScreen() {
        fpsField.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
        if (fpsField.isFocused()) {
            commitTypedFps();
        }
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "SubFrame replay", width / 2, height / 4 - 20, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
        fpsField.drawTextBox();
        if (mouseX >= boxX && mouseX < boxX + BOX_W && mouseY >= boxY && mouseY < boxY + BOX_H || fpsField.isFocused()) {
            drawCenteredString(fontRendererObj, "Type any fps (" + MIN_FPS + "-" + MAX_FPS + "), Enter to set", width / 2, boxY - 13, 0xA0A0A0);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
