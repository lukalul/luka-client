package dev.envoy.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import dev.envoy.Envoy;
import dev.envoy.gui.EnvoyGuiController;
import dev.envoy.EnvoySettings;
import dev.envoy.module.waypoint.LocalWaypoints;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

/**
 * Client command {@code /envoy}: opens the Envoy configuration screen.
 */
public final class EnvoyCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "envoy";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/envoy - opens the configuration screen";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        execute(args);
    }

    @Override
    public int compareTo(Object other) {
        return other instanceof ICommand ? getCommandName().compareTo(((ICommand) other).getCommandName()) : 0;
    }

    public static void execute(String[] args) {
        try {
            if (args.length == 0) { EnvoyGuiController.requestOpenConfig(); return; }
            if ("waypoints".equalsIgnoreCase(args[0])) { EnvoyGuiController.requestOpenWaypointManager(); return; }
            if (!"waypoint".equalsIgnoreCase(args[0]) || args.length < 2) { feedback("Usage: /envoy waypoint add|remove|list|toggle ..."); return; }
            String action = args[1];
            if ("list".equalsIgnoreCase(action)) {
                if (LocalWaypoints.current().isEmpty()) feedback("No local waypoints in this dimension.");
                for (LocalWaypoints.Waypoint waypoint : LocalWaypoints.current()) feedback((waypoint.visible ? "§a" : "§7") + waypoint.name + " §f" + waypoint.x + ", " + waypoint.y + ", " + waypoint.z);
                return;
            }
            if (args.length < 3) { feedback("A waypoint name is required."); return; }
            if ("add".equalsIgnoreCase(action)) {
                Minecraft mc = Minecraft.getMinecraft(); int x, y, z; String name;
                if (args.length >= 6) { name = args[2]; x = Integer.parseInt(args[3]); y = Integer.parseInt(args[4]); z = Integer.parseInt(args[5]); }
                else { name = join(args, 2); x = (int) Math.floor(mc.thePlayer.posX); y = (int) Math.floor(mc.thePlayer.posY); z = (int) Math.floor(mc.thePlayer.posZ); }
                LocalWaypoints.add(name, x, y, z, EnvoySettings.defaultWaypointColor); feedback("§aAdded waypoint " + name); return;
            }
            String name = join(args, 2);
            if ("remove".equalsIgnoreCase(action)) feedback(LocalWaypoints.removeByName(name) ? "§aRemoved " + name : "§cWaypoint not found: " + name);
            else if ("toggle".equalsIgnoreCase(action)) { boolean shown = LocalWaypoints.toggleByName(name); feedback((shown ? "§aShown " : "§7Hidden ") + name); }
            else feedback("Unknown waypoint action: " + action);
        } catch (Throwable t) { Envoy.problem("waypoint command", t); feedback("§cWaypoint command failed: " + t.getMessage()); }
    }

    private static String join(String[] args, int start) { StringBuilder b = new StringBuilder(); for (int i = start; i < args.length; i++) { if (b.length() > 0) b.append(' '); b.append(args[i]); } return b.toString(); }
    private static void feedback(String message) { if (Minecraft.getMinecraft().thePlayer != null) Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText("§b[Envoy] §r" + message)); }
}
