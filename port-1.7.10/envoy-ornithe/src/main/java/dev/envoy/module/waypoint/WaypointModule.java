package dev.envoy.module.waypoint;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import dev.envoy.Envoy;
import dev.envoy.net.ApolloNetwork;
import dev.envoy.net.ApolloProtos;

/** Receives Apollo waypoint messages and publishes render-ready state. */
public final class WaypointModule {
    private static final String DISPLAY = "lunarclient.apollo.waypoint.v1.DisplayWaypointMessage";
    private static final String REMOVE = "lunarclient.apollo.waypoint.v1.RemoveWaypointMessage";
    private static final String RESET = "lunarclient.apollo.waypoint.v1.ResetWaypointsMessage";
    private static final String SHOW = "lunarclient.apollo.waypoint.v1.ShowWaypointMessage";
    private static final String HIDE = "lunarclient.apollo.waypoint.v1.HideWaypointMessage";
    private static final String PLAYER_WORLD = "lunarclient.apollo.player.v1.UpdatePlayerWorldMessage";

    private WaypointModule() { }

    public static void register(ApolloNetwork network) {
        network.register(DISPLAY, WaypointModule::acceptDisplay);
        network.register(REMOVE, message -> changeNamed(message, NameAction.REMOVE));
        network.register(RESET, message -> reset());
        network.register(SHOW, message -> changeNamed(message, NameAction.SHOW));
        network.register(HIDE, message -> changeNamed(message, NameAction.HIDE));
        network.register(PLAYER_WORLD, WaypointModule::acceptWorld);
    }

    private static void acceptDisplay(Any envelope) throws Exception {
        DisplayPacket packet = decodeDisplay(envelope.getValue().newCodedInput());
        if (packet.name == null || packet.location == null) return;
        WaypointState.Waypoint waypoint = new WaypointState.Waypoint(packet.name,
            packet.location.x, packet.location.y, packet.location.z, packet.color,
            packet.beam, packet.highlight, packet.lineWidth, packet.style.text,
            packet.style.distance, packet.hidden, packet.preventRemoval);
        waypoint.world = packet.location.world;
        WaypointState.display(waypoint);
        Envoy.LOGGER.info("[Waypoint] display '{}' ({}, {}, {}) world={} beam={}", packet.name,
            packet.location.x, packet.location.y, packet.location.z, packet.location.world, packet.beam);
    }

    private static DisplayPacket decodeDisplay(CodedInputStream input) throws Exception {
        DisplayPacket packet = new DisplayPacket();
        int tag;
        while ((tag = input.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1: packet.name = input.readString(); break;
                case 2: packet.location = decodeLocation(input.readBytes()); break;
                case 3: packet.color = ApolloProtos.parseColorArgb(input.readBytes()); break;
                case 4: packet.preventRemoval = input.readBool(); break;
                case 5: packet.hidden = input.readBool(); break;
                case 6: packet.beam = input.readBool(); break;
                case 7: packet.highlight = input.readBool(); break;
                case 8: packet.lineWidth = input.readFloat(); break;
                case 9: packet.style = decodeStyle(input.readBytes()); break;
                default: input.skipField(tag);
            }
        }
        return packet;
    }

    private static Location decodeLocation(ByteString bytes) throws Exception {
        Location location = new Location();
        CodedInputStream input = bytes.newCodedInput();
        int tag;
        while ((tag = input.readTag()) != 0) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1: location.world = input.readString(); break;
                case 2: location.x = input.readSInt32(); break;
                case 3: location.y = input.readSInt32(); break;
                case 4: location.z = input.readSInt32(); break;
                default: input.skipField(tag);
            }
        }
        return location;
    }

    private static Style decodeStyle(ByteString bytes) throws Exception {
        Style style = new Style();
        CodedInputStream input = bytes.newCodedInput();
        int tag;
        while ((tag = input.readTag()) != 0) {
            int field = WireFormat.getTagFieldNumber(tag);
            if (field == 1) style.text = input.readBool();
            else if (field == 9) style.distance = input.readBool();
            else input.skipField(tag);
        }
        return style;
    }

    private static void acceptWorld(Any envelope) throws Exception {
        String world = readFirstString(envelope);
        if (world == null) return;
        WaypointState.currentWorld = world;
        Envoy.LOGGER.info("[Waypoint] player world is '{}'", world);
    }

    private static void changeNamed(Any envelope, NameAction action) throws Exception {
        String name = readFirstString(envelope);
        if (name == null) return;
        if (action == NameAction.REMOVE) {
            WaypointState.remove(name);
            Envoy.LOGGER.info("[Waypoint] remove '{}'", name);
        } else {
            WaypointState.setHidden(name, action == NameAction.HIDE);
        }
    }

    private static String readFirstString(Any envelope) throws Exception {
        CodedInputStream input = envelope.getValue().newCodedInput();
        String result = null;
        int tag;
        while ((tag = input.readTag()) != 0) {
            if (WireFormat.getTagFieldNumber(tag) == 1) result = input.readString();
            else input.skipField(tag);
        }
        return result;
    }

    private static void reset() {
        WaypointState.resetAll();
        Envoy.LOGGER.info("[Waypoint] reset");
    }

    private enum NameAction { REMOVE, SHOW, HIDE }

    private static final class DisplayPacket {
        String name;
        Location location;
        int color = 0xFFFFFFFF;
        boolean preventRemoval;
        boolean hidden;
        boolean beam;
        boolean highlight;
        float lineWidth;
        Style style = new Style();
    }

    private static final class Location {
        String world;
        int x;
        int y;
        int z;
    }

    private static final class Style {
        boolean text = true;
        boolean distance = true;
    }
}
