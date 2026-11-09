package dev.envoy.module.marker;

import java.util.UUID;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import dev.envoy.Envoy;
import dev.envoy.net.ApolloNetwork;
import dev.envoy.net.ApolloProtos;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Decodes Apollo marker messages into immutable records for the render side. */
public final class MarkerModule {
    private static final String DISPLAY = "lunarclient.apollo.marker.v1.DisplayMarkerMessage";
    private static final String REMOVE = "lunarclient.apollo.marker.v1.RemoveMarkerMessage";
    private static final String RESET = "lunarclient.apollo.marker.v1.ResetMarkersMessage";
    private static final int WHITE = 0xFFFFFFFF;

    private MarkerModule() { }

    public static void register(ApolloNetwork network) {
        network.register(DISPLAY, MarkerModule::acceptDisplay);
        network.register(REMOVE, MarkerModule::acceptRemoval);
        network.register(RESET, ignored -> clearMarkers());
    }

    private static void acceptDisplay(Any envelope) throws Exception {
        MarkerDraft draft = new MarkerDraft();
        CodedInputStream input = envelope.getValue().newCodedInput();
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1: draft.id = input.readString(); break;
                case 2: draft.position = readPosition(input.readBytes()); break;
                case 4: draft.owner = input.readString(); break;
                case 5: readFlag(input.readBytes(), draft); break;
                case 6: draft.target = readTarget(input.readBytes()); break;
                case 11: draft.style = readStyle(input.readBytes()); break;
                default: input.skipField(tag);
            }
        }
        if (!draft.complete()) return;
        MarkerState.Marker marker = draft.freeze();
        MarkerState.display(marker);
        Envoy.LOGGER.info("[Marker] display '{}' type={} target={} ({}, {}, {})",
            marker.id, marker.type, marker.target.type, marker.x, marker.y, marker.z);
    }

    private static void acceptRemoval(Any envelope) throws Exception {
        String id = readStringField(envelope.getValue(), 1);
        if (id == null) return;
        MarkerState.remove(id);
        Envoy.LOGGER.info("[Marker] remove '{}'", id);
    }

    private static void clearMarkers() {
        MarkerState.resetAll();
        Envoy.LOGGER.info("[Marker] reset");
    }

    private static String readStringField(ByteString bytes, int wanted) throws Exception {
        CodedInputStream input = bytes.newCodedInput();
        String value = null;
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            if (WireFormat.getTagFieldNumber(tag) == wanted) value = input.readString();
            else input.skipField(tag);
        }
        return value;
    }

    private static void readFlag(ByteString bytes, MarkerDraft draft) throws Exception {
        CodedInputStream input = bytes.newCodedInput();
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            int variant = WireFormat.getTagFieldNumber(tag);
            if (variant >= MarkerState.TYPE_NORMAL && variant <= MarkerState.TYPE_INTEREST) {
                draft.kind = variant;
                draft.argb = readColorWrapper(input.readBytes());
            } else input.skipField(tag);
        }
    }

    private static int readColorWrapper(ByteString bytes) throws Exception {
        CodedInputStream input = bytes.newCodedInput();
        int color = WHITE;
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            if (WireFormat.getTagFieldNumber(tag) == 1) color = ApolloProtos.parseColorArgb(input.readBytes());
            else input.skipField(tag);
        }
        return color;
    }

    private static MarkerState.Target readTarget(ByteString bytes) throws Exception {
        CodedInputStream input = bytes.newCodedInput();
        MarkerState.Target target = MarkerState.Target.none();
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            int variant = WireFormat.getTagFieldNumber(tag);
            if (variant == 1 || variant == 2) {
                target = new MarkerState.Target(variant == 1 ? MarkerState.TARGET_ITEM : MarkerState.TARGET_BLOCK,
                    readTargetIcon(input.readBytes()), null, null, null);
            } else if (variant == 3) {
                target = new MarkerState.Target(MarkerState.TARGET_ENTITY, null,
                    readStringField(input.readBytes(), 1), null, null);
            } else if (variant == 4) target = readPlayer(input.readBytes());
            else input.skipField(tag);
        }
        return target;
    }

    private static ItemStack readTargetIcon(ByteString bytes) throws Exception {
        CodedInputStream input = bytes.newCodedInput();
        ItemStack icon = null;
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            if (WireFormat.getTagFieldNumber(tag) == 1) icon = readItem(input.readBytes());
            else input.skipField(tag);
        }
        return icon;
    }

    private static ItemStack readItem(ByteString bytes) throws Exception {
        CodedInputStream input = bytes.newCodedInput();
        int numericId = -1;
        String resourceId = null;
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            int field = WireFormat.getTagFieldNumber(tag);
            if (field == 1) numericId = input.readInt32();
            else if (field == 2) resourceId = input.readString();
            else input.skipField(tag);
        }
        Item item = resourceId != null && !resourceId.isEmpty() ? Item.getByNameOrId(resourceId)
            : numericId >= 0 ? Item.getItemById(numericId) : null;
        return item == null ? null : new ItemStack(item);
    }

    private static MarkerState.Target readPlayer(ByteString bytes) throws Exception {
        CodedInputStream input = bytes.newCodedInput();
        UUID id = null;
        String name = null;
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            int field = WireFormat.getTagFieldNumber(tag);
            if (field == 1) id = ApolloProtos.parseUuid(input.readBytes());
            else if (field == 2) name = input.readString();
            else input.skipField(tag);
        }
        return new MarkerState.Target(MarkerState.TARGET_PLAYER, null, null, id, name);
    }

    private static Style readStyle(ByteString bytes) throws Exception {
        Style style = new Style();
        CodedInputStream input = bytes.newCodedInput();
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1: style.scale = input.readFloat(); break;
                case 4: style.shadow = input.readBool(); break;
                case 6: style.ownerDisplay = input.readEnum(); break;
                case 7: style.owner = input.readEnum(); break;
                case 8: style.coordinates = input.readEnum(); break;
                case 9: style.distance = input.readEnum(); break;
                case 10: style.description = input.readEnum(); break;
                default: input.skipField(tag);
            }
        }
        return style;
    }

    private static Position readPosition(ByteString bytes) throws Exception {
        Position p = new Position();
        CodedInputStream input = bytes.newCodedInput();
        for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 2: p.x = input.readDouble(); break;
                case 3: p.y = input.readDouble(); break;
                case 4: p.z = input.readDouble(); break;
                default: input.skipField(tag);
            }
        }
        return p;
    }

    private static final class Position { double x, y, z; }
    private static final class Style {
        int owner, coordinates, distance, description;
        int ownerDisplay = MarkerState.OWNER_NAME;
        boolean shadow = true;
        float scale = 1.0F;
    }
    private static final class MarkerDraft {
        String id, owner = "";
        Position position;
        int kind = MarkerState.TYPE_NORMAL, argb = WHITE;
        MarkerState.Target target = MarkerState.Target.none();
        Style style = new Style();
        boolean complete() { return id != null && position != null; }
        MarkerState.Marker freeze() {
            return new MarkerState.Marker(id, owner, position.x, position.y, position.z, kind, argb, target,
                style.owner, style.coordinates, style.distance, style.description, style.ownerDisplay,
                style.shadow, style.scale);
        }
    }
}
