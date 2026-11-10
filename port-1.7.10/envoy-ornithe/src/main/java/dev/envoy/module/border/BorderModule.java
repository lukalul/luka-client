package dev.envoy.module.border;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import dev.envoy.Envoy;
import dev.envoy.net.ApolloNetwork;
import dev.envoy.net.ApolloProtos;

public final class BorderModule {
    private static final String DISPLAY = "lunarclient.apollo.border.v1.DisplayBorderMessage";
    private static final String REMOVE = "lunarclient.apollo.border.v1.RemoveBorderMessage";
    private static final String RESET = "lunarclient.apollo.border.v1.ResetBordersMessage";

    private BorderModule() {
    }

    public static void register(ApolloNetwork network) {
        network.register(DISPLAY, message -> apply(decode(message)));
        network.register(REMOVE, message -> remove(readName(message)));
        network.register(RESET, message -> reset());
    }

    private static BorderOrder decode(Any message) throws Exception {
        BorderOrder order = new BorderOrder();
        CodedInputStream input = message.getValue().newCodedInput();
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1: order.id = input.readString(); break;
                case 5: order.animated = input.readBool(); break;
                case 6: order.argb = ApolloProtos.parseColorArgb(input.readBytes()); break;
                case 7: order.rectangle = readRectangle(input.readBytes()); break;
                case 8: order.ticks = input.readInt32(); break;
                default: input.skipField(tag);
            }
        }
        return order;
    }

    private static double[] readRectangle(ByteString data) throws Exception {
        double[] values = new double[4];
        CodedInputStream input = data.newCodedInput();
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            int field = WireFormat.getTagFieldNumber(tag);
            if (field >= 1 && field <= 4) {
                values[field - 1] = input.readDouble();
            } else {
                input.skipField(tag);
            }
        }
        return values;
    }

    private static String readName(Any message) throws Exception {
        String id = null;
        CodedInputStream input = message.getValue().newCodedInput();
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            if (WireFormat.getTagFieldNumber(tag) == 1) id = input.readString(); else input.skipField(tag);
        }
        return id;
    }

    private static void apply(BorderOrder order) {
        if (order.id == null || order.rectangle == null) return;
        double[] b = order.rectangle;
        BorderState.display(order.id, b[0], b[1], b[2], b[3], order.argb, order.animated, order.ticks);
        Envoy.LOGGER.info("[Border] display '{}' ({}, {}) -> ({}, {}) color={} anim={}", order.id,
            b[0], b[1], b[2], b[3], String.format("#%06X", order.argb & 0xFFFFFF), order.animated && order.ticks > 0);
    }

    private static void remove(String id) {
        if (id != null) {
            BorderState.remove(id);
            Envoy.LOGGER.info("[Border] remove '{}'", id);
        }
    }

    private static void reset() {
        BorderState.resetAll();
        Envoy.LOGGER.info("[Border] reset");
    }

    private static final class BorderOrder {
        private String id;
        private double[] rectangle;
        private int argb = 0xFFFFFFFF;
        private boolean animated;
        private int ticks;
    }
}
