package dev.envoy.module.combat;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Value;
import java.io.ByteArrayOutputStream;
import java.util.List;

/** Plain-Java executable fixture check; no game classes or test framework required. */
public final class CombatProtocolCheck {
    public static void main(String[] args) throws Exception {
        byte[] combat = configurable("combat", new String[] {"disable-miss-penalty", "disable-block-miss-penalty", "allow-dig-and-use"}, new boolean[] {true, false, true});
        byte[] other = configurable("waypoint", new String[] {"unknown"}, new boolean[] {true});
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CodedOutputStream out = CodedOutputStream.newInstance(bytes);
        out.writeByteArray(1, combat);
        out.writeByteArray(1, other);
        out.writeUInt32(99, 12345); // unknown outer field
        out.flush();

        List<CombatProtocol.Configurable> parsed = CombatProtocol.parseOverride(bytes.toByteArray());
        check(parsed.size() == 2, "two module entries");
        CombatProtocol.Configurable result = parsed.get(0);
        check("combat".equals(result.module) && result.enable, "enabled combat module");
        check(result.properties.get("disable-miss-penalty").getBoolValue(), "miss=true");
        check(!result.properties.get("disable-block-miss-penalty").getBoolValue(), "block=false");
        check(result.properties.get("allow-dig-and-use").getBoolValue(), "dig/use=true");
        check("waypoint".equals(parsed.get(1).module), "unknown module retained");
        System.out.println("Combat protobuf check: modules=2, miss=true, block=false, dig/use=true, unknown fields skipped");
    }

    private static byte[] configurable(String module, String[] keys, boolean[] values) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CodedOutputStream out = CodedOutputStream.newInstance(bytes);
        out.writeString(1, module);
        out.writeBool(2, true);
        for (int i = 0; i < keys.length; i++) out.writeByteArray(3, property(keys[i], values[i]));
        out.writeFixed32(77, 0x12345678); // unknown configurable field
        out.flush();
        return bytes.toByteArray();
    }

    private static byte[] property(String key, boolean enabled) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CodedOutputStream out = CodedOutputStream.newInstance(bytes);
        out.writeString(1, key);
        out.writeByteArray(2, Value.newBuilder().setBoolValue(enabled).build().toByteArray());
        out.writeUInt64(50, 9); // unknown map-entry field
        out.flush();
        return bytes.toByteArray();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
