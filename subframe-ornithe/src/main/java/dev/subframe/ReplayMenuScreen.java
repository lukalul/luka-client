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
    private static final int MIN_FPS = 1, MAX_FPS = 20000;
    private static final int[] SUBFRAMES = {1, 4, 8, 16, 32, 64, 128};

    private static final int RESUME = 0, OPTIONS = 1, FPS_BTN = 2, SUB_BTN = 3, RENDER = 4, QUIT = 5, BLEND_BTN = 6, OUT_BTN = 7, ENC_BTN = 8, GAME_MENU_BTN = 9,
            RES_BTN = 11;
    /** Render size presets (0 x 0 = the window's size); any other size is typed into the box beside the button. */
    private static final int[][] SIZES = {{0, 0}, {1280, 720}, {1920, 1080}, {2560, 1440}, {3840, 2160}};
    /** Same bounds as renderWidth/renderHeight in subframe.properties. */
    static final int MIN_SIZE = 16, MAX_W = 7680, MAX_H = 4320;

    private GuiButton fpsButton, subButton, blendButton, outButton, encButton, resButton;
    /** Render size typed in by hand ("2560x1080"). */
    private GuiTextField resField;
    private int resX, resY;
    private static final int RES_W = 66;
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
        fpsField.setMaxStringLength(5);
        fpsField.setText(Integer.toString(SubFrameMod.outputFps));
        blendButton = new GuiButton(BLEND_BTN, x, y + 114, 200, 20, "");
        subButton = new GuiButton(SUB_BTN, x, y + 138, 200, 20, "");
        outButton = new GuiButton(OUT_BTN, x, y + 162, 98, 20, "");
        buttonList.add(fpsButton);
        buttonList.add(blendButton);
        buttonList.add(subButton);
        buttonList.add(outButton);
        encButton = new GuiButton(ENC_BTN, x + 102, y + 162, 98, 20, "");
        buttonList.add(encButton);
        resButton = new GuiButton(RES_BTN, x, y + 186, 128, 20, "");
        buttonList.add(resButton);
        resField = newField(resX = x + 134, resY = y + 187, RES_W, BOX_H);
        resField.setMaxStringLength(9);
        resetResField();
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
        encButton.displayString = "Enc: " + Encoders.label();
        outButton.displayString = "Output: " + (SubFrameMod.outputPng ? "PNG" : "video");
        resButton.displayString = "Size: " + (SubFrameMod.renderWidth > 0 ? SubFrameMod.renderWidth + "x" + SubFrameMod.renderHeight
                : "window " + mc.displayWidth + "x" + mc.displayHeight);
    }

    /** The size a render will have: the one chosen here, or the game window's. */
    static int renderWidth(net.minecraft.client.Minecraft mc) {
        return SubFrameMod.renderWidth > 0 ? SubFrameMod.renderWidth : mc.displayWidth;
    }

    static int renderHeight(net.minecraft.client.Minecraft mc) {
        return SubFrameMod.renderHeight > 0 ? SubFrameMod.renderHeight : mc.displayHeight;
    }

    /** "2560x1080" (also "2560 x 1080" or "2560*1080") as {width, height}, both even and in range; null when it is not a size. */
    static int[] parseSize(String text) {
        String[] parts = text.trim().toLowerCase(java.util.Locale.ROOT).split("\\s*[x*]\\s*");
        if (parts.length != 2) {
            return null;
        }
        try {
            int w = Integer.parseInt(parts[0].trim()) / 2 * 2, h = Integer.parseInt(parts[1].trim()) / 2 * 2;
            return w >= MIN_SIZE && h >= MIN_SIZE && w <= MAX_W && h <= MAX_H ? new int[]{w, h} : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void commitTypedSize() {
        int[] size = parseSize(resField.getText());
        if (size != null) {
            SubFrameMod.renderWidth = size[0];
            SubFrameMod.renderHeight = size[1];
            refreshLabels();
        }
        resetResField();
    }

    private void resetResField() {
        resField.setText(renderWidth(mc) + "x" + renderHeight(mc));
        resField.setTextColor(0xE0E0E0);
    }

    /** The size box's text (self-test). */
    String sizeText() {
        return resField.getText();
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

    private GuiTextField newField(int x, int y, int w, int h) {
        return new GuiTextField(10, fontRendererObj, x, y, w, h);
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

    /** Rough time to draw one subframe and hand it to the encoder at 1080p, from render logs (about 7.6 ms). */
    private static final double MS_PER_SUBFRAME_1080P = 8.0;

    /**
     * What a render with the current settings will produce, when it is big enough to be worth a look first (over 1,000
     * fps, over 5 GB, or more than half the free space): the lines of the check screen, else null. The size is a rough
     * estimate: how well frames compress depends on the scene and on how alike neighbouring frames are (very alike at
     * high frame rates).
     */
    static String[] bigRender(Replay r, int w, int h) {
        double seconds = (r.rangeActive() ? r.outNs() - r.inNs() : r.totalNs()) / 1e9;
        if (SubFrameMod.maxSeconds > 0) {
            seconds = Math.min(seconds, SubFrameMod.maxSeconds);
        }
        int fps = SubFrameMod.outputFps;
        long frames = (long) Math.ceil(seconds * fps);
        int subframes = SubFrameMod.blend ? Math.max(1, SubFrameMod.subframes) : 1;
        double bytesPerPixel;
        String kind;
        if (SubFrameMod.outputPng) {
            bytesPerPixel = 0.6; // measured 0.3 on a flat test world; busier scenes compress worse
            kind = "PNG images";
        } else if (Encoders.gpuMaxFps("h264_nvenc", w, h) < fps && SubFrameMod.hevcLossless) {
            bytesPerPixel = Math.min(0.4, 0.016 * Math.sqrt(5000.0 / fps)); // measured 0.016 at 5,000 fps, 1080p, lossless
            kind = "lossless HEVC video";
        } else {
            bytesPerPixel = 0.065 * Math.sqrt(60.0 / Math.max(60, fps));
            kind = "video";
        }
        long bytes = (long) (frames * (double) w * h * bytesPerPixel);
        java.io.File dir = new java.io.File(net.minecraft.client.Minecraft.getMinecraft().mcDataDir, "subframe");
        long free = (dir.isDirectory() ? dir : dir.getParentFile()).getUsableSpace();
        boolean big = fps > 1000 || bytes > (5L << 30) || (free > 0 && bytes > free / 2);
        if (!big) {
            return null;
        }
        double minutes = frames * subframes * MS_PER_SUBFRAME_1080P * ((double) w * h / (1920.0 * 1080.0)) / 60000.0;
        return new String[]{
                String.format("%,d frames: %.1f s at %,d fps, %d x %d", frames, seconds, fps, w, h),
                "Estimated size: about " + size(bytes) + " of " + kind + " (could be half or twice that)",
                "Free space on that drive: " + size(free) + (bytes > free ? "  - probably not enough" : ""),
                "Render time: about " + (minutes < 1 ? "a minute" : String.format("%.0f minutes", minutes)) + (subframes > 1 ? " (" + subframes + " subframes per frame)" : ""),
        };
    }

    private static String size(long bytes) {
        if (bytes >= (1L << 30)) {
            return String.format("%.1f GB", bytes / (double) (1L << 30));
        }
        return String.format("%.0f MB", Math.max(1, bytes / (double) (1L << 20)));
    }

    /** "Large render" check before a render the player may not expect to be this big or this slow. */
    private static final class BigRenderScreen extends GuiScreen {
        private final GuiScreen back;
        private final String[] lines;

        BigRenderScreen(GuiScreen back, String[] lines) {
            this.back = back;
            this.lines = lines;
        }

        @Override
        public void initGui() {
            buttonList.clear();
            int y = height / 2 + 30;
            buttonList.add(new GuiButton(0, width / 2 - 102, y, 100, 20, "Render"));
            buttonList.add(new GuiButton(1, width / 2 + 2, y, 100, 20, "Back"));
        }

        @Override
        protected void actionPerformed(GuiButton b) {
            Replay r = Replay.active;
            if (b.id == 0 && r != null) {
                mc.displayGuiScreen(r.screen());
                r.startRender();
            } else {
                mc.displayGuiScreen(back);
            }
        }

        @Override
        protected void keyTyped(char typedChar, int keyCode) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                mc.displayGuiScreen(back);
            }
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            int y = height / 2 - 60;
            drawCenteredString(fontRendererObj, "Large render", width / 2, y, 0xFFFFFF);
            for (int i = 0; i < lines.length; i++) {
                drawCenteredString(fontRendererObj, lines[i], width / 2, y + 20 + i * 12, i == 2 && lines[i].contains("not enough") ? 0xFF5555 : 0xC0C0C0);
            }
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
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
            case RES_BTN: {
                int i = 0;
                while (i < SIZES.length && !(SIZES[i][0] == SubFrameMod.renderWidth && SIZES[i][1] == SubFrameMod.renderHeight)) {
                    i++;
                }
                int[] nextSize = SIZES[i < SIZES.length ? (i + 1) % SIZES.length : 0];
                SubFrameMod.renderWidth = nextSize[0];
                SubFrameMod.renderHeight = nextSize[1];
                resetResField();
                refreshLabels();
                break;
            }
            case RENDER: {
                String[] big = bigRender(r, renderWidth(mc), renderHeight(mc));
                if (big != null) {
                    mc.displayGuiScreen(new BigRenderScreen(this, big));
                    break;
                }
                mc.displayGuiScreen(r.screen());
                r.startRender();
                break;
            }
            case QUIT:
                r.quit();
                break;
            default:
                break;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (resField.isFocused() && keyCode != Keyboard.KEY_ESCAPE) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                commitTypedSize();
                resField.setFocused(false);
                return;
            }
            String name = Keyboard.getKeyName(keyCode);
            char c = typedChar == 0 && name != null && name.length() == 1 ? Character.toLowerCase(name.charAt(0)) : typedChar;
            if (Character.isDigit(c) || c == 'x' || c == 'X' || c == '*' || c < ' ') {
                resField.textboxKeyTyped(c, keyCode);
                resField.setTextColor(parseSize(resField.getText()) != null ? 0xE0E0E0 : 0xFF5555);
            }
            return;
        }
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
            if (resField.isFocused()) {
                commitTypedSize();
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
        boolean inRes = mouseX >= resX && mouseX < resX + RES_W && mouseY >= resY && mouseY < resY + BOX_H;
        if (resField.isFocused() && !inRes) {
            commitTypedSize();
        }
        super.mouseClicked(mouseX, mouseY, button);
        boolean wasRes = resField.isFocused();
        resField.mouseClicked(mouseX, mouseY, button);
        if (!wasRes && resField.isFocused()) {
            resField.setCursorPositionEnd();
            resField.setSelectionPos(0);
        }
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
        resField.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
        if (fpsField.isFocused()) {
            commitTypedFps();
        }
        if (resField.isFocused()) {
            commitTypedSize();
        }
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "SubFrame replay", width / 2, height / 4 - 20, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
        fpsField.drawTextBox();
        resField.drawTextBox();
        if (mouseX >= resX && mouseX < resX + RES_W && mouseY >= resY && mouseY < resY + BOX_H || resField.isFocused()) {
            drawCenteredString(fontRendererObj, "Type any size, like 3840x2160 (up to " + MAX_W + "x" + MAX_H + "), Enter to set", width / 2, resY + 22, 0xA0A0A0);
        }
        if (mouseX >= boxX && mouseX < boxX + BOX_W && mouseY >= boxY && mouseY < boxY + BOX_H || fpsField.isFocused()) {
            drawCenteredString(fontRendererObj, "Type any fps (" + MIN_FPS + "-" + MAX_FPS + "), Enter to set", width / 2, boxY - 13, 0xA0A0A0);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
