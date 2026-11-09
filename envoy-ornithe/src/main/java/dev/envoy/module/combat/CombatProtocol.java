package dev.envoy.module.combat;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.Value;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Decodes the two configurable-message layouts used by Apollo servers. */
public final class CombatProtocol {
    private static final int OVERRIDE_ENTRY = 10;

    private CombatProtocol() { }

    public static List<Configurable> parseOverride(byte[] payload) throws IOException {
        CodedInputStream source = CodedInputStream.newInstance(payload);
        List<Configurable> modules = new ArrayList<Configurable>();
        while (!source.isAtEnd()) {
            int wireTag = source.readTag();
            if (wireTag == 0) break;
            if (wireTag == OVERRIDE_ENTRY) {
                modules.add(decodeModule(source.readByteArray()));
            } else if (!source.skipField(wireTag)) {
                break;
            }
        }
        return modules;
    }

    private static Configurable decodeModule(byte[] encoded) throws IOException {
        Layout layout = identifyLayout(encoded);
        CodedInputStream fields = CodedInputStream.newInstance(encoded);
        String name = "";
        boolean active = false;
        Map<String, Value> options = new LinkedHashMap<String, Value>();
        while (!fields.isAtEnd()) {
            int wireTag = fields.readTag();
            if (wireTag == 0) break;
            if (wireTag == layout.nameTag) name = fields.readStringRequireUtf8();
            else if (wireTag == layout.enabledTag) active = fields.readBool();
            else if (wireTag == layout.propertyTag) {
                Map.Entry<String, Value> option = decodeProperty(fields.readByteArray());
                if (!option.getKey().isEmpty()) options.put(option.getKey(), option.getValue());
            } else if (!fields.skipField(wireTag)) break;
        }
        return new Configurable(name, active, options);
    }

    private static Layout identifyLayout(byte[] encoded) throws IOException {
        CodedInputStream header = CodedInputStream.newInstance(encoded);
        return header.readTag() == Layout.CURRENT.nameTag ? Layout.CURRENT : Layout.LEGACY;
    }

    private static Map.Entry<String, Value> decodeProperty(byte[] encoded) throws IOException {
        CodedInputStream fields = CodedInputStream.newInstance(encoded);
        String name = "";
        Value setting = Value.getDefaultInstance();
        while (!fields.isAtEnd()) {
            int wireTag = fields.readTag();
            if (wireTag == 0) break;
            if (wireTag == 10) name = fields.readStringRequireUtf8();
            else if (wireTag == 18) setting = Value.parseFrom(fields.readByteArray());
            else if (!fields.skipField(wireTag)) break;
        }
        return new AbstractMap.SimpleImmutableEntry<String, Value>(name, setting);
    }

    private enum Layout {
        LEGACY(10, 16, 26), CURRENT(18, 24, 34);

        final int nameTag;
        final int enabledTag;
        final int propertyTag;

        Layout(int nameTag, int enabledTag, int propertyTag) {
            this.nameTag = nameTag;
            this.enabledTag = enabledTag;
            this.propertyTag = propertyTag;
        }
    }

    public static final class Configurable {
        public final String module;
        public final boolean enable;
        public final Map<String, Value> properties;

        public Configurable(String module, boolean enable, Map<String, Value> properties) {
            this.module = module;
            this.enable = enable;
            this.properties = Collections.unmodifiableMap(properties);
        }
    }
}
