package dev.envoy.module.lightning;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import dev.envoy.Envoy;

public final class EnvoyLightning {
    public static final String CHANNEL = "envoy:lightning";
    private static final int PAYLOAD_BYTES = 3 * 8 + 2 * 4;

    private EnvoyLightning() { }

    public static void handleIncoming(byte[] payload) {
        if (payload == null || payload.length < PAYLOAD_BYTES) {
            Envoy.LOGGER.warn("[Lightning] unreadable payload: expected at least {} bytes", PAYLOAD_BYTES);
            return;
        }
        try {
            ByteBuffer fields = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
            LightningState.add(fields.getDouble(), fields.getDouble(), fields.getDouble(), fields.getInt(), fields.getInt());
        } catch (RuntimeException error) {
            Envoy.LOGGER.warn("[Lightning] unreadable payload: {}", error.toString());
        }
    }
}
