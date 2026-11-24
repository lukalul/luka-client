package dev.envoy.gui;

import dev.envoy.module.waypoint.WaypointState;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class GuiPointList extends GuiScreen {
    private static final int TOP = 38, BOTTOM_GAP = 34, ROW = 24, SIDE = 20;
    private static final int REMOVE_W = 54, REMOVE_H = 16;
    private final GuiScreen parent;
    private final List<PointRow> rows = new ArrayList<PointRow>();
    private int scrollOffset;

    private static final class PointRow {
        final String key, title, subtitle;
        final int color;
        final boolean hidden;

        PointRow(String key, String title, String subtitle, int color, boolean hidden) {
            this.key = key;
            this.title = title;
            this.subtitle = subtitle;
            this.color = color;
            this.hidden = hidden;
        }
    }

    public GuiPointList(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(0, width / 2 - 100, height - 26, 200, 20, I18n.format("envoy.list.back")));
        rebuildRows();
    }

    @Override
    public void updateScreen() { rebuildRows(); }

    private void rebuildRows() {
        List<PointRow> waypoints = new ArrayList<PointRow>();
        EntityPlayer player = mc.thePlayer;
        for (WaypointState.Waypoint point : WaypointState.all()) {
            waypoints.add(new PointRow(point.name, point.name,
                locationText(player, point.x + 0.5D, point.y + 0.5D, point.z + 0.5D),
                point.colorArgb, point.hidden));
        }
        Comparator<PointRow> order = new Comparator<PointRow>() {
            @Override public int compare(PointRow first, PointRow second) {
                return first.title.compareToIgnoreCase(second.title);
            }
        };
        Collections.sort(waypoints, order);
        rows.clear();
        rows.addAll(waypoints);
        scrollOffset = validScroll(scrollOffset);
    }

    private static String locationText(EntityPlayer player, double x, double y, double z) {
        String text = Math.round(x) + ", " + Math.round(y) + ", " + Math.round(z);
        return player == null ? text : text + "  -  " + Math.round(player.getDistance(x, y, z)) + "m";
    }

    private int bottom() { return height - BOTTOM_GAP; }

    private int validScroll(int proposed) {
        int maximum = Math.max(0, rows.size() * ROW - (bottom() - TOP));
        if (proposed < 0) return 0;
        return proposed > maximum ? maximum : proposed;
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int delta = Mouse.getEventDWheel();
        if (delta != 0) scrollOffset = validScroll(scrollOffset - Integer.signum(delta) * ROW);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("envoy.list.title"), width / 2, 12, 0xFFFFFF);
        if (rows.isEmpty()) {
            drawCenteredString(fontRendererObj, I18n.format("envoy.list.empty"), width / 2, height / 2 - 4, 0xFFAAAAAA);
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }
        drawCenteredString(fontRendererObj, I18n.format("envoy.list.hint"), width / 2, 24, 0xFFAAAAAA);
        beginClip();
        for (int index = 0; index < rows.size(); index++) {
            int y = rowY(index);
            if (y <= bottom() && y + ROW >= TOP) paintRow(rows.get(index), y, mouseX, mouseY);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void beginClip() {
        int scale = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, (height - bottom()) * scale, width * scale, (bottom() - TOP) * scale);
    }

    private int rowY(int index) { return TOP + index * ROW - scrollOffset; }

    private void paintRow(PointRow row, int y, int mouseX, int mouseY) {
        int right = width - SIDE;
        drawRect(SIDE, y, right, y + ROW - 2, 0x40FFFFFF);
        drawRect(SIDE, y, SIDE + 3, y + ROW - 2, 0xFF000000 | row.color);
        String title = row.hidden ? row.title + " " + I18n.format("envoy.list.hidden") : row.title;
        String kind = I18n.format("envoy.list.waypoint");
        fontRendererObj.drawStringWithShadow(title, SIDE + 8, y + 3, 0xFFFFFFFF);
        fontRendererObj.drawStringWithShadow(kind + "  " + row.subtitle, SIDE + 8, y + 13, 0xFFAAAAAA);
        int buttonX = right - REMOVE_W - 4, buttonY = removeY(y);
        drawRect(buttonX, buttonY, buttonX + REMOVE_W, buttonY + REMOVE_H,
            overRemove(mouseX, mouseY, buttonX, buttonY) ? 0xFFB03030 : 0xFF702020);
        String text = I18n.format("envoy.list.remove");
        fontRendererObj.drawStringWithShadow(text, buttonX + (REMOVE_W - fontRendererObj.getStringWidth(text)) / 2,
            buttonY + 4, 0xFFFFFFFF);
    }

    private static int removeY(int rowY) { return rowY + (ROW - 2 - REMOVE_H) / 2; }

    private boolean overRemove(int mouseX, int mouseY, int x, int y) {
        return mouseX >= x && mouseX <= x + REMOVE_W && mouseY >= y && mouseY <= y + REMOVE_H
            && mouseY >= TOP && mouseY <= bottom();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            int buttonX = width - SIDE - REMOVE_W - 4;
            for (int index = 0; index < rows.size(); index++) {
                if (!overRemove(mouseX, mouseY, buttonX, removeY(rowY(index)))) continue;
                remove(rows.get(index));
                rebuildRows();
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void remove(PointRow row) {
        WaypointState.remove(row.key);
        mc.getSoundHandler().playSound(PositionedSoundRecord.createPositionedSoundRecord(new ResourceLocation("gui.button.press"), 1.0F));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) mc.displayGuiScreen(parent);
    }
}
