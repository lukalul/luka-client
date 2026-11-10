package dev.envoy.net;

import com.google.protobuf.Any;

/** Receives one decoded Apollo envelope. */
@FunctionalInterface
public interface ApolloPacketHandler {
    void handle(Any envelope) throws Exception;
}
