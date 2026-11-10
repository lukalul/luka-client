package dev.envoy.gui;

import dev.envoy.module.waypoint.LocalWaypoints;
import dev.envoy.module.waypoint.WaypointState;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

public final class GuiWaypointManager extends GuiScreen implements GuiYesNoCallback {
    private static final int TOP = 38, ROW_HEIGHT = 28, ACTION_BASE = 100;
    private static final int EDIT = 0, VISIBILITY = 1, DELETE = 2;
    private final GuiScreen parent;
    private final List<ManagedPoint> points = new ArrayList<ManagedPoint>();
    private int scrollOffset;

    private static final class ManagedPoint {
        final LocalWaypoints.Waypoint local;
        final WaypointState.Waypoint server;

        ManagedPoint(LocalWaypoints.Waypoint local, WaypointState.Waypoint server) {
            this.local = local;
            this.server = server;
        }
        boolean isServer() { return server != null; }
        String name() { return isServer() ? server.name : local.name; }
        int x() { return isServer() ? server.x : local.x; }
        int y() { return isServer() ? server.y : local.y; }
        int z() { return isServer() ? server.z : local.z; }
        int color() { return isServer() ? server.colorArgb : local.colorArgb; }
        boolean visible() { return isServer() ? !server.hidden : local.visible; }
    }

    public GuiWaypointManager(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() { reload(); }

    private void reload() {
        points.clear();
        for (LocalWaypoints.Waypoint local : LocalWaypoints.current()) points.add(new ManagedPoint(local, null));
        for (WaypointState.Waypoint server : WaypointState.all()) points.add(new ManagedPoint(null, server));
        scrollOffset = constrainScroll(scrollOffset);
        layoutButtons();
    }

    private void layoutButtons() {
        buttonList.clear();
        buttonList.add(new GuiButton(1, width / 2 - 102, height - 26, 100, 20, "New waypoint"));
        buttonList.add(new GuiButton(2, width / 2 + 2, height - 26, 100, 20, "Back"));
        for (int index = 0; index < points.size(); index++) {
            int y = rowY(index);
            if (y < TOP || y > height - 52) continue;
            ManagedPoint point = points.get(index);
            int id = ACTION_BASE + index * 3;
            buttonList.add(actionButton(id + EDIT, width - 166, y, 48, "Edit", !point.isServer()));
            buttonList.add(actionButton(id + VISIBILITY, width - 114, y, 54, point.visible() ? "Hide" : "Show", true));
            buttonList.add(actionButton(id + DELETE, width - 56, y, 50, "Delete", !point.isServer()));
        }
    }

    private static GuiButton actionButton(int id, int x, int rowY, int width, String text, boolean enabled) {
        GuiButton button = new GuiButton(id, x, rowY + 4, width, 20, text);
        button.enabled = enabled;
        return button;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Waypoints", width / 2, 14, 0xFFFFFF);
        for (int index = 0; index < points.size(); index++) {
            int y = rowY(index);
            if (y >= TOP && y <= height - 52) paint(points.get(index), y);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void paint(ManagedPoint point, int y) {
        drawRect(6, y, width - 6, y + 26, 0x50000000);
        drawRect(8, y + 4, 12, y + 22, point.color());
        double distance = mc.thePlayer == null ? 0.0D
            : mc.thePlayer.getDistance(point.x() + 0.5D, point.y(), point.z() + 0.5D);
        String suffix = point.isServer() ? " [Server]" : "";
        fontRendererObj.drawStringWithShadow(point.name() + suffix, 17, y + 4, point.visible() ? 0xFFFFFF : 0x888888);
        fontRendererObj.drawString(point.x() + ", " + point.y() + ", " + point.z() + "  " + Math.round(distance) + "m",
            17, y + 15, 0xAAAAAA);
    }

    private int rowY(int index) { return TOP + index * ROW_HEIGHT - scrollOffset; }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        scrollOffset = constrainScroll(scrollOffset + (wheel < 0 ? ROW_HEIGHT : -ROW_HEIGHT));
        layoutButtons();
    }

    private int constrainScroll(int value) {
        int maximum = Math.max(0, points.size() * ROW_HEIGHT - (height - 90));
        return Math.max(0, Math.min(maximum, value));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1) { mc.displayGuiScreen(new GuiCreateWaypoint(this, null)); return; }
        if (button.id == 2) { mc.displayGuiScreen(parent); return; }
        int encoded = button.id - ACTION_BASE;
        int index = encoded / 3;
        if (encoded < 0 || index >= points.size()) return;
        perform(points.get(index), encoded % 3, index);
    }

    private void perform(ManagedPoint point, int action, int index) {
        if (action == EDIT && !point.isServer()) {
            mc.displayGuiScreen(new GuiCreateWaypoint(this, point.local));
        } else if (action == VISIBILITY) {
            if (point.isServer()) WaypointState.setHidden(point.server.name, !point.server.hidden);
            else LocalWaypoints.setVisible(point.local.id, !point.local.visible);
            reload();
        } else if (action == DELETE && !point.isServer()) {
            mc.displayGuiScreen(new GuiYesNo(this, "Delete waypoint?", point.name(), index));
        }
    }

    @Override
    public void confirmClicked(boolean accepted, int id) {
        if (accepted && id >= 0 && id < points.size()) {
            ManagedPoint point = points.get(id);
            if (!point.isServer()) LocalWaypoints.remove(point.local.id);
        }
        mc.displayGuiScreen(this);
        reload();
    }
}
