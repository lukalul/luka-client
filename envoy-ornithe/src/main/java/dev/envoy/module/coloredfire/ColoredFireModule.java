package dev.envoy.module.coloredfire;

import java.util.UUID;
import com.google.protobuf.Any;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import dev.envoy.Envoy;
import dev.envoy.net.ApolloNetwork;
import dev.envoy.net.ApolloProtos;

public final class ColoredFireModule {
    private static final String OVERRIDE = "lunarclient.apollo.coloredfire.v1.OverrideColoredFireMessage";
    private static final String RESET = "lunarclient.apollo.coloredfire.v1.ResetColoredFireMessage";
    private static final String RESET_ALL = "lunarclient.apollo.coloredfire.v1.ResetColoredFiresMessage";

    private ColoredFireModule() { }

    public static void register(ApolloNetwork network) {
        network.register(OVERRIDE, message -> set(read(message, true)));
        network.register(RESET, message -> unset(read(message, false).player));
        network.register(RESET_ALL, message -> clear());
    }

    private static FireOrder read(Any message, boolean includeColor) throws Exception {
        FireOrder order = new FireOrder();
        CodedInputStream input = message.getValue().newCodedInput();
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            int field = WireFormat.getTagFieldNumber(tag);
            if (field == 1) order.player = ApolloProtos.parseUuid(input.readBytes());
            else if (includeColor && field == 2) order.color = ApolloProtos.parseColorArgb(input.readBytes());
            else input.skipField(tag);
        }
        return order;
    }

    private static void set(FireOrder order) {
        if (order.player == null || order.color == null) return;
        ColoredFireState.override(order.player, order.color.intValue());
        Envoy.LOGGER.info("[ColoredFire] override {} #{}", order.player,
            String.format("%06X", order.color.intValue() & 0xFFFFFF));
    }

    private static void unset(UUID player) {
        if (player != null) {
            ColoredFireState.reset(player);
            Envoy.LOGGER.info("[ColoredFire] reset {}", player);
        }
    }

    private static void clear() {
        ColoredFireState.resetAll();
        Envoy.LOGGER.info("[ColoredFire] reset all");
    }

    private static final class FireOrder { private UUID player; private Integer color; }
}
