package dev.envoy.module.staff;

import com.google.protobuf.Any;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import dev.envoy.Envoy;
import dev.envoy.net.ApolloNetwork;
import dev.envoy.net.ApolloPacketHandler;
import java.util.ArrayList;
import java.util.List;

public final class StaffModule {
    private static final String ENABLE = "lunarclient.apollo.staffmod.v1.EnableStaffModsMessage";
    private static final String DISABLE = "lunarclient.apollo.staffmod.v1.DisableStaffModsMessage";
    private StaffModule() { }

    public static void register(ApolloNetwork network) {
        network.register(ENABLE, receiver(true));
        network.register(DISABLE, receiver(false));
    }

    private static ApolloPacketHandler receiver(final boolean grant) {
        return new ApolloPacketHandler() {
            public void handle(Any message) throws Exception {
                List<String> permissions = decode(message);
                Envoy.LOGGER.info("[Staff] {} {}", grant ? "enable" : "disable", permissions);
                if (grant) StaffState.apolloEnable(permissions);
                else StaffState.apolloDisable(permissions);
            }
        };
    }

    private static List<String> decode(Any message) throws Exception {
        List<String> permissions = new ArrayList<String>();
        CodedInputStream wire = message.getValue().newCodedInput();
        for (int tag; (tag = wire.readTag()) != 0;) {
            if (WireFormat.getTagFieldNumber(tag) != 1) {
                wire.skipField(tag);
            } else if (WireFormat.getTagWireType(tag) == WireFormat.WIRETYPE_VARINT) {
                permissions.add(permissionName(wire.readEnum()));
            } else if (WireFormat.getTagWireType(tag) == WireFormat.WIRETYPE_LENGTH_DELIMITED) {
                readPacked(wire.readBytes().newCodedInput(), permissions);
            } else {
                wire.skipField(tag);
            }
        }
        if (permissions.isEmpty()) permissions.add("STAFF_MODS");
        return permissions;
    }

    private static void readPacked(CodedInputStream wire, List<String> output) throws Exception {
        while (!wire.isAtEnd()) output.add(permissionName(wire.readEnum()));
    }

    private static String permissionName(int value) {
        switch (value) {
            case 1: return "XRAY";
            case 2: return "BHOP";
            case 3: return "NAMETAGS";
            default: return "MOD_" + value;
        }
    }
}
