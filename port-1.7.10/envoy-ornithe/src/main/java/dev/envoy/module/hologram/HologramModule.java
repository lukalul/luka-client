package dev.envoy.module.hologram;

import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import dev.envoy.Envoy;
import dev.envoy.module.nametag.AdventureText;
import dev.envoy.module.nametag.AdventureText.Run;
import dev.envoy.net.ApolloNetwork;

public final class HologramModule {
    private static final String DISPLAY = "lunarclient.apollo.hologram.v1.DisplayHologramMessage";
    private static final String REMOVE = "lunarclient.apollo.hologram.v1.RemoveHologramMessage";
    private static final String RESET = "lunarclient.apollo.hologram.v1.ResetHologramsMessage";

    private HologramModule() {
    }

    public static void register(ApolloNetwork network) {
        network.register(DISPLAY, message -> publish(HologramPacket.decode(message)));
        network.register(REMOVE, message -> discard(readIdentifier(message)));
        network.register(RESET, message -> discardEverything());
    }

    private static void publish(HologramPacket packet) {
        if (!packet.complete()) return;
        HologramState.display(packet.id, new HologramState.Hologram(packet.x, packet.y, packet.z,
            packet.lines, packet.throughWalls, packet.shadow, packet.background));
        Envoy.LOGGER.info("[Hologram] display '{}' ({}, {}, {}) lines={}",
            packet.id, packet.x, packet.y, packet.z, packet.lines.size());
    }

    private static String readIdentifier(Any message) throws Exception {
        CodedInputStream input = message.getValue().newCodedInput();
        String id = null;
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            if (WireFormat.getTagFieldNumber(tag) == 1) id = input.readString();
            else input.skipField(tag);
        }
        return id;
    }

    private static void discard(String id) {
        if (id == null) return;
        HologramState.remove(id);
        Envoy.LOGGER.info("[Hologram] remove '{}'", id);
    }

    private static void discardEverything() {
        HologramState.resetAll();
        Envoy.LOGGER.info("[Hologram] reset");
    }

    private static final class HologramPacket {
        private String id;
        private double x;
        private double y;
        private double z;
        private boolean hasPosition;
        private boolean throughWalls;
        private boolean shadow;
        private boolean background;
        private final List<List<Run>> lines = new ArrayList<>();

        private boolean complete() {
            return id != null && hasPosition && !lines.isEmpty();
        }

        private static HologramPacket decode(Any message) throws Exception {
            HologramPacket packet = new HologramPacket();
            CodedInputStream input = message.getValue().newCodedInput();
            for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
                int field = WireFormat.getTagFieldNumber(tag);
                if (field == 1) packet.id = input.readString();
                else if (field == 2) packet.readPosition(input.readBytes());
                else if (field == 4) packet.throughWalls = input.readBool();
                else if (field == 5) packet.shadow = input.readBool();
                else if (field == 6) packet.background = input.readBool();
                else if (field == 7) packet.addLine(input.readString());
                else input.skipField(tag);
            }
            return packet;
        }

        private void addLine(String json) {
            List<Run> parsed = AdventureText.parse(json);
            if (!parsed.isEmpty()) lines.add(parsed);
        }

        private void readPosition(ByteString bytes) throws Exception {
            CodedInputStream input = bytes.newCodedInput();
            for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
                int field = WireFormat.getTagFieldNumber(tag);
                if (field == 2) x = input.readDouble();
                else if (field == 3) y = input.readDouble();
                else if (field == 4) z = input.readDouble();
                else input.skipField(tag);
            }
            hasPosition = true;
        }
    }
}
