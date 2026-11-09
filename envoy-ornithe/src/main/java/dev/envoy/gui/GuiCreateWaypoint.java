package dev.envoy.gui;

import dev.envoy.EnvoySettings;
import dev.envoy.module.waypoint.LocalWaypoints;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

public final class GuiCreateWaypoint extends GuiScreen {
    private static final int SAVE = 1, CANCEL = 2, SWATCH_BASE = 10;
    private static final int[] SWATCHES = {0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55, 0xFF55FF55, 0xFF55FFFF, 0xFF5555FF, 0xFFFF55FF};

    private final GuiScreen parent;
    private final LocalWaypoints.Waypoint draft;
    private GuiTextField[] inputs;
    private String validationMessage = "";

    public GuiCreateWaypoint(GuiScreen parent, LocalWaypoints.Waypoint editing) {
        this.parent = parent;
        this.draft = editing == null ? null : editing.copy();
    }

    @Override
    public void initGui() {
        int left = width / 2 - 100;
        int top = height / 2 - 92;
        int[] origin = initialPosition();
        inputs = new GuiTextField[] {
            makeField(0, left, top + 18, 200, draft == null ? "" : draft.name),
            makeField(1, left, top + 50, 62, number(draft == null ? origin[0] : draft.x)),
            makeField(2, left + 69, top + 50, 62, number(draft == null ? origin[1] : draft.y)),
            makeField(3, left + 138, top + 50, 62, number(draft == null ? origin[2] : draft.z)),
            makeField(4, left, top + 82, 200, formatColor(draft == null ? EnvoySettings.defaultWaypointColor : draft.colorArgb))
        };
        buttonList.clear();
        for (int index = 0; index < SWATCHES.length; index++) {
            buttonList.add(new GuiButton(SWATCH_BASE + index, left + index * 28, top + 106, 24, 20, "\u25A0"));
        }
        buttonList.add(new GuiButton(SAVE, left, top + 134, 98, 20, "Save"));
        buttonList.add(new GuiButton(CANCEL, left + 102, top + 134, 98, 20, "Cancel"));
        inputs[0].setFocused(true);
    }

    private int[] initialPosition() {
        if (mc.thePlayer == null) return new int[] {0, 64, 0};
        return new int[] {
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.posY),
            (int) Math.floor(mc.thePlayer.posZ)
        };
    }

    private GuiTextField makeField(int id, int x, int y, int width, String value) {
        GuiTextField result = new GuiTextField(id, fontRendererObj, x, y, width, 20);
        result.setText(value);
        return result;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = width / 2 - 100, top = height / 2 - 92;
        drawCenteredString(fontRendererObj, draft == null ? "Create a Waypoint" : "Edit Waypoint", width / 2, top - 2, 0xFFFFFF);
        fontRendererObj.drawString("Name", left, top + 8, 0xAAAAAA);
        fontRendererObj.drawString("X", left, top + 40, 0xAAAAAA);
        fontRendererObj.drawString("Y", left + 69, top + 40, 0xAAAAAA);
        fontRendererObj.drawString("Z", left + 138, top + 40, 0xAAAAAA);
        fontRendererObj.drawString("ARGB / RGB hex", left, top + 72, 0xAAAAAA);
        for (GuiTextField input : inputs) input.drawTextBox();
        if (!validationMessage.isEmpty()) drawCenteredString(fontRendererObj, validationMessage, width / 2, top + 158, 0xFF5555);
        super.drawScreen(mouseX, mouseY, partialTicks);
        for (int index = 0; index < SWATCHES.length; index++) {
            drawRect(left + index * 28 + 6, top + 112, left + index * 28 + 18, top + 124, SWATCHES[index]);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == CANCEL) close();
        else if (button.id == SAVE) commit();
        else if (button.id >= SWATCH_BASE && button.id < SWATCH_BASE + SWATCHES.length) {
            inputs[4].setText(formatColor(SWATCHES[button.id - SWATCH_BASE]));
        }
    }

    private void commit() {
        try {
            String name = requiredName(inputs[0].getText());
            int x = coordinate(1), y = coordinate(2), z = coordinate(3);
            int color = parseColor(inputs[4].getText());
            if (draft == null) LocalWaypoints.add(name, x, y, z, color);
            else {
                draft.name = name;
                draft.x = x; draft.y = y; draft.z = z;
                draft.colorArgb = color;
                LocalWaypoints.update(draft);
            }
            close();
        } catch (RuntimeException invalid) {
            validationMessage = invalid.getMessage() == null ? "Invalid values" : invalid.getMessage();
        }
    }

    private int coordinate(int index) {
        return Integer.parseInt(inputs[index].getText().trim());
    }

    private static String requiredName(String raw) {
        String value = raw.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Enter a name");
        return value;
    }

    public static int parseColor(String value) {
        String cleaned = value.trim();
        if (cleaned.startsWith("#")) cleaned = cleaned.substring(1);
        if (cleaned.length() == 6) cleaned = "FF" + cleaned;
        if (cleaned.length() != 8) throw new IllegalArgumentException("Use RRGGBB or AARRGGBB");
        return (int) Long.parseLong(cleaned, 16);
    }

    private static String formatColor(int color) { return String.format("%08X", color); }
    private static String number(int value) { return Integer.toString(value); }
    private void close() { mc.displayGuiScreen(parent); }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) { close(); return; }
        if (keyCode == 28) { commit(); return; }
        for (GuiTextField input : inputs) input.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        for (GuiTextField input : inputs) input.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        for (GuiTextField input : inputs) input.updateCursorCounter();
    }
}
