package dev.envoy.module.tntcountdown;

import java.util.UUID;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import dev.envoy.Envoy;
import dev.envoy.net.ApolloNetwork;
import dev.envoy.net.ApolloProtos;

public final class TntCountdownModule {
    private static final String SET = "lunarclient.apollo.tntcountdown.v1.SetTntCountdownMessage";

    private TntCountdownModule() {
    }

    public static void register(ApolloNetwork network) {
        network.register(SET, message -> install(CountdownPacket.from(message)));
    }

    private static void install(CountdownPacket packet) {
        if (!packet.usable()) return;
        TntCountdownState.set(packet.networkId, packet.uuid, packet.ticks);
        Object label = packet.networkId > 0 ? Integer.valueOf(packet.networkId) : packet.uuid;
        Envoy.LOGGER.info("[TntCountdown] entity {} for {} ticks", label, packet.ticks);
    }

    private static final class CountdownPacket {
        private int networkId;
        private UUID uuid;
        private int ticks;

        private boolean usable() {
            return ticks > 0 && (networkId > 0 || uuid != null);
        }

        private static CountdownPacket from(Any message) throws Exception {
            CountdownPacket packet = new CountdownPacket();
            CodedInputStream input = message.getValue().newCodedInput();
            for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
                int field = WireFormat.getTagFieldNumber(tag);
                if (field == 1) packet.readIdentity(input.readBytes());
                else if (field == 2) packet.ticks = input.readInt32();
                else input.skipField(tag);
            }
            return packet;
        }

        private void readIdentity(ByteString bytes) throws Exception {
            CodedInputStream input = bytes.newCodedInput();
            for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
                int field = WireFormat.getTagFieldNumber(tag);
                if (field == 1) networkId = input.readInt32();
                else if (field == 2) uuid = ApolloProtos.parseUuid(input.readBytes());
                else input.skipField(tag);
            }
        }
    }
}
