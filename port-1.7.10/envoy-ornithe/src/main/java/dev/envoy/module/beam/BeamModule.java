package dev.envoy.module.beam;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import dev.envoy.Envoy;
import dev.envoy.net.ApolloNetwork;
import dev.envoy.net.ApolloProtos;

public final class BeamModule {
    private static final String DISPLAY = "lunarclient.apollo.beam.v1.DisplayBeaconBeamMessage";
    private static final String REMOVE = "lunarclient.apollo.beam.v1.RemoveBeaconBeamMessage";
    private static final String RESET = "lunarclient.apollo.beam.v1.ResetBeaconBeamsMessage";
    private static final int DEFAULT_COLOR = 0xFFFFFFFF;

    private BeamModule() {
    }

    public static void register(ApolloNetwork network) {
        network.register(DISPLAY, message -> install(readDisplay(message)));
        network.register(REMOVE, message -> discard(readIdentifier(message)));
        network.register(RESET, message -> clear());
    }

    private static DisplayOrder readDisplay(Any message) throws Exception {
        CodedInputStream input = message.getValue().newCodedInput();
        DisplayOrder order = new DisplayOrder();
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            int field = WireFormat.getTagFieldNumber(tag);
            if (field == 1) {
                order.name = input.readString();
            } else if (field == 2) {
                order.color = ApolloProtos.parseColorArgb(input.readBytes());
            } else if (field == 3) {
                order.position = readPosition(input.readBytes());
            } else {
                input.skipField(tag);
            }
        }
        return order;
    }

    private static String readIdentifier(Any message) throws Exception {
        CodedInputStream input = message.getValue().newCodedInput();
        String result = null;
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            if (WireFormat.getTagFieldNumber(tag) == 1) {
                result = input.readString();
            } else {
                input.skipField(tag);
            }
        }
        return result;
    }

    private static Position readPosition(ByteString bytes) throws Exception {
        CodedInputStream input = bytes.newCodedInput();
        Position result = new Position();
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            int field = WireFormat.getTagFieldNumber(tag);
            if (field >= 2 && field <= 4) {
                result.coordinates[field - 2] = input.readSInt32();
            } else {
                input.skipField(tag);
            }
        }
        return result;
    }

    private static void install(DisplayOrder order) {
        if (order.name == null || order.position == null) {
            return;
        }
        int[] p = order.position.coordinates;
        BeamState.display(order.name, new BeamState.Beam(p[0], p[1], p[2], order.color));
        Envoy.LOGGER.info("[Beam] display '{}' ({}, {}, {}) color={}", order.name, p[0], p[1], p[2],
            String.format("#%06X", order.color & 0xFFFFFF));
    }

    private static void discard(String id) {
        if (id != null) {
            BeamState.remove(id);
            Envoy.LOGGER.info("[Beam] remove '{}'", id);
        }
    }

    private static void clear() {
        BeamState.resetAll();
        Envoy.LOGGER.info("[Beam] reset");
    }

    private static final class DisplayOrder {
        private String name;
        private int color = DEFAULT_COLOR;
        private Position position;
    }

    private static final class Position {
        private final int[] coordinates = new int[3];
    }
}
