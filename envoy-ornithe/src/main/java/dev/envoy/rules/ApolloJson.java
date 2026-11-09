package dev.envoy.rules;

import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import dev.envoy.module.combat.CombatProtocol;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tolerant reader for Apollo's JSON configurable transport and wrapper/list shapes. */
public final class ApolloJson {
    private ApolloJson() { }

    public static List<CombatProtocol.Configurable> parse(byte[] payload) {
        Object root = SimpleJson.parse(new String(payload, StandardCharsets.UTF_8));
        List<CombatProtocol.Configurable> out = new ArrayList<CombatProtocol.Configurable>();
        collect(root, out);
        return out;
    }

    @SuppressWarnings("unchecked") private static void collect(Object value, List<CombatProtocol.Configurable> out) {
        if (value instanceof List) { for (Object item : (List<Object>)value) collect(item, out); return; }
        if (!(value instanceof Map)) return;
        Map<String,Object> map = (Map<String,Object>)value;
        Object module = map.get("apollo_module");
        if (module instanceof String) {
            Map<String,Value> properties = new LinkedHashMap<String,Value>();
            Object raw = map.get("properties");
            if (raw instanceof Map) for (Map.Entry<String,Object> e : ((Map<String,Object>)raw).entrySet()) properties.put(e.getKey(), value(e.getValue()));
            out.add(new CombatProtocol.Configurable((String)module, !Boolean.FALSE.equals(map.get("enable")), properties));
            return;
        }
        for (Object nested : map.values()) collect(nested, out);
    }

    @SuppressWarnings("unchecked") private static Value value(Object object) {
        Value.Builder b = Value.newBuilder();
        if (object == null) return b.setNullValue(com.google.protobuf.NullValue.NULL_VALUE).build();
        if (object instanceof Boolean) return b.setBoolValue((Boolean)object).build();
        if (object instanceof Number) return b.setNumberValue(((Number)object).doubleValue()).build();
        if (object instanceof String) return b.setStringValue((String)object).build();
        if (object instanceof List) { ListValue.Builder list = ListValue.newBuilder(); for (Object x : (List<Object>)object) list.addValues(value(x)); return b.setListValue(list).build(); }
        Struct.Builder struct = Struct.newBuilder(); for (Map.Entry<String,Object> e : ((Map<String,Object>)object).entrySet()) struct.putFields(e.getKey(), value(e.getValue()));
        return b.setStructValue(struct).build();
    }
}
