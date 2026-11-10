package dev.envoy.module.limb;

import java.util.UUID;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import dev.envoy.Envoy;
import dev.envoy.net.ApolloNetwork;
import dev.envoy.net.ApolloProtos;

public final class LimbModule {
    private static final String HIDE = "lunarclient.apollo.limb.v1.HideBodyPartMessage";
    private static final String RESET = "lunarclient.apollo.limb.v1.ResetBodyPartMessage";
    private static final String HIDE_ARMOR = "lunarclient.apollo.limb.v1.HideArmorPiecesMessage";
    private static final String RESET_ARMOR = "lunarclient.apollo.limb.v1.ResetArmorPiecesMessage";
    private LimbModule() { }

    public static void register(ApolloNetwork network) {
        network.register(HIDE, message -> apply(message, Target.BODY, Change.HIDE));
        network.register(RESET, message -> apply(message, Target.BODY, Change.SHOW));
        network.register(HIDE_ARMOR, message -> apply(message, Target.ARMOR, Change.HIDE));
        network.register(RESET_ARMOR, message -> apply(message, Target.ARMOR, Change.SHOW));
    }

    private static void apply(Any envelope, Target target, Change change) throws Exception {
        Directive directive = Directive.read(envelope.getValue().newCodedInput());
        if (directive.player == null) return;
        if (target == Target.BODY) {
            if (change == Change.HIDE) LimbState.hide(directive.player, directive.mask);
            else LimbState.reset(directive.player, directive.mask);
        } else {
            if (change == Change.HIDE) LimbState.hideArmor(directive.player, directive.mask);
            else LimbState.resetArmor(directive.player, directive.mask);
        }
        Envoy.LOGGER.info("[Limb] {}{} {} mask={}", change.logName, target.logSuffix,
            directive.player, Integer.toBinaryString(directive.mask));
    }

    private enum Target { BODY(""), ARMOR(" armor"); final String logSuffix; Target(String value) { logSuffix = value; } }
    private enum Change { HIDE("hide"), SHOW("reset"); final String logName; Change(String value) { logName = value; } }

    private static final class Directive {
        UUID player;
        int mask;
        static Directive read(CodedInputStream wire) throws Exception {
            Directive result = new Directive();
            for (int tag = wire.readTag(); tag != 0; tag = wire.readTag()) {
                int field = WireFormat.getTagFieldNumber(tag);
                if (field == 1) result.player = ApolloProtos.parseUuid(wire.readBytes());
                else if (field == 2) result.mask |= readParts(wire, tag);
                else wire.skipField(tag);
            }
            return result;
        }

        private static int readParts(CodedInputStream wire, int tag) throws Exception {
            if (WireFormat.getTagWireType(tag) != WireFormat.WIRETYPE_LENGTH_DELIMITED)
                return LimbState.bit(wire.readInt32());
            ByteString bytes = wire.readBytes();
            CodedInputStream packed = bytes.newCodedInput();
            int bits = 0;
            while (!packed.isAtEnd()) bits |= LimbState.bit(packed.readInt32());
            return bits;
        }
    }
}
