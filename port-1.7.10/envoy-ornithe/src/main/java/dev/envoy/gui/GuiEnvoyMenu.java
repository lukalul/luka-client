package dev.envoy.gui;

import dev.envoy.module.marker.MarkerState;
import dev.envoy.module.waypoint.WaypointState;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

public final class GuiEnvoyMenu extends GuiScreen {
    private static final int HUD = 0, POINTS = 1, CLOSE = 2, WAYPOINTS = 3;
    private GuiButton points;
    private int pointCount;

    @Override
    public void initGui() {
        buttonList.clear();
        int left = width / 2 - 100;
        int top = height / 4 + 24;
        pointCount = availablePoints();
        add(HUD, left, top, I18n.format("envoy.menu.hud"));
        points = add(POINTS, left, top + 24, pointsText());
        add(WAYPOINTS, left, top + 48, "Waypoint manager");
        add(CLOSE, left, height - 28, I18n.format("envoy.config.done"));
    }

    private GuiButton add(int id, int x, int y, String text) {
        GuiButton result = new GuiButton(id, x, y, 200, 20, text);
        buttonList.add(result);
        return result;
    }

    @Override
    public void updateScreen() {
        int latest = availablePoints();
        if (latest == pointCount) return;
        pointCount = latest;
        points.displayString = pointsText();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("envoy.menu.title"), width / 2, height / 4, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        GuiScreen next = null;
        if (button.id == HUD) next = new GuiHudConfig(this);
        else if (button.id == POINTS) next = new GuiPointList(this);
        else if (button.id == WAYPOINTS) next = new GuiWaypointManager(this);
        else if (button.id != CLOSE) return;
        mc.displayGuiScreen(next);
    }

    private String pointsText() {
        return I18n.format("envoy.menu.points", pointCount);
    }

    private static int availablePoints() {
        return WaypointState.all().size() + MarkerState.all().size();
    }
}
