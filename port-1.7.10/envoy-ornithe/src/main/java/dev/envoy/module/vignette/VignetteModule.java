package dev.envoy.module.vignette;

import com.google.protobuf.Any;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import dev.envoy.Envoy;
import dev.envoy.net.ApolloNetwork;
import net.minecraft.util.ResourceLocation;

public final class VignetteModule {
    public static final String MESSAGE_PREFIX = "lunarclient.apollo.vignette.v1.";
    private static final String DISPLAY = "lunarclient.apollo.vignette.v1.DisplayVignetteMessage";
    private static final String RESET = "lunarclient.apollo.vignette.v1.ResetVignetteMessage";

    private VignetteModule() { }

    public static void register(ApolloNetwork network) {
        network.register(DISPLAY, message -> show(decode(message)));
        network.register(RESET, message -> hide());
    }

    private static VignetteOrder decode(Any message) throws Exception {
        VignetteOrder order = new VignetteOrder();
        CodedInputStream input = message.getValue().newCodedInput();
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            int field = WireFormat.getTagFieldNumber(tag);
            if (field == 1) order.resource = input.readString();
            else if (field == 2) order.opacity = input.readFloat();
            else input.skipField(tag);
        }
        return order;
    }

    private static void show(VignetteOrder order) {
        if (order.resource == null || order.resource.isEmpty()) {
            VignetteState.reset();
            return;
        }
        VignetteState.display(new ResourceLocation(order.resource), order.opacity);
        Envoy.LOGGER.info("[Vignette] display '{}' opacity={}", order.resource, order.opacity);
    }

    private static void hide() {
        VignetteState.reset();
        Envoy.LOGGER.info("[Vignette] reset");
    }

    private static final class VignetteOrder { private String resource; private float opacity; }
}
