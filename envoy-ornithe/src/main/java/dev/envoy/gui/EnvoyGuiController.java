package dev.envoy.gui;

import dev.envoy.Envoy;
import dev.envoy.platform.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/** Defers screen changes until chat/command processing has finished. */
public final class EnvoyGuiController {
    private enum Destination { NONE, CONFIG, WAYPOINTS, CREATE_WAYPOINT, POINTS }
    private static int pendingMask;

    public static void requestOpenConfig() { enqueue(1); }
    public static void requestOpenWaypointManager() { enqueue(2); }
    public static void requestCreateWaypoint() { enqueue(4); }
    public static void requestOpenPoints() { enqueue(8); }

    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Destination destination = takeRequest();
        if (destination == Destination.NONE) return;
        if (destination == Destination.CONFIG) {
            Envoy.LOGGER.info("[Config] opening the Envoy configuration");
            if (!OneConfigBridge.open()) show(new GuiEnvoyMenu());
            return;
        }
        if (destination == Destination.WAYPOINTS) show(new GuiWaypointManager(null));
        else if (destination == Destination.CREATE_WAYPOINT) show(new GuiCreateWaypoint(null, null));
        else if (destination == Destination.POINTS) show(new GuiPointList(null));
    }

    private static synchronized Destination takeRequest() {
        if ((pendingMask & 8) != 0) { pendingMask &= ~8; return Destination.POINTS; }
        if ((pendingMask & 4) != 0) { pendingMask &= ~4; return Destination.CREATE_WAYPOINT; }
        if ((pendingMask & 2) != 0) { pendingMask &= ~2; return Destination.WAYPOINTS; }
        if ((pendingMask & 1) != 0) { pendingMask &= ~1; return Destination.CONFIG; }
        return Destination.NONE;
    }

    private static synchronized void enqueue(int flag) {
        pendingMask |= flag;
    }

    private static void show(GuiScreen screen) {
        Minecraft.getMinecraft().displayGuiScreen(screen);
    }
}
