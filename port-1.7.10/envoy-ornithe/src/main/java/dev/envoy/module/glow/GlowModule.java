package dev.envoy.module.glow;

import java.util.UUID;

import com.google.protobuf.Any;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import dev.envoy.Envoy;
import dev.envoy.net.ApolloNetwork;
import dev.envoy.net.ApolloProtos;

public final class GlowModule {
    public static final String MESSAGE_PREFIX = "lunarclient.apollo.glow.v1.";
    private static final String OVERRIDE = "lunarclient.apollo.glow.v1.OverrideGlowEffectMessage";
    private static final String RESET = "lunarclient.apollo.glow.v1.ResetGlowEffectMessage";
    private static final String RESET_ALL = "lunarclient.apollo.glow.v1.ResetGlowEffectsMessage";

    private GlowModule() {
    }

    public static void register(ApolloNetwork network) {
        network.register(OVERRIDE, message -> applyOverride(new GlowUpdate(message)));
        network.register(RESET, message -> removeOne(readPlayer(message)));
        network.register(RESET_ALL, message -> clear());
    }

    private static void applyOverride(GlowUpdate update) {
        if (update.player == null) return;
        GlowState.override(update.player, update.color);
        Envoy.LOGGER.info("[Glow] override {} color={}", update.player,
            update.color == null ? "default" : String.format("#%06X", update.color.intValue() & 0xFFFFFF));
    }

    private static UUID readPlayer(Any envelope) throws Exception {
        CodedInputStream input = envelope.getValue().newCodedInput();
        UUID result = null;
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            if (WireFormat.getTagFieldNumber(tag) == 1) result = ApolloProtos.parseUuid(input.readBytes());
            else input.skipField(tag);
        }
        return result;
    }

    private static void removeOne(UUID player) {
        if (player == null) return;
        GlowState.reset(player);
        Envoy.LOGGER.info("[Glow] reset {}", player);
    }

    private static void clear() {
        GlowState.resetAll();
        Envoy.LOGGER.info("[Glow] reset all");
    }

    private static final class GlowUpdate {
        private UUID player;
        private Integer color;

        private GlowUpdate(Any envelope) throws Exception {
            CodedInputStream input = envelope.getValue().newCodedInput();
            for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
                int field = WireFormat.getTagFieldNumber(tag);
                if (field == 1) this.player = ApolloProtos.parseUuid(input.readBytes());
                else if (field == 2) this.color = ApolloProtos.parseColorArgb(input.readBytes());
                else input.skipField(tag);
            }
        }
    }
}
