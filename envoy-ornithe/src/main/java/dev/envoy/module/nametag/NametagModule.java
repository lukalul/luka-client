package dev.envoy.module.nametag;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import dev.envoy.Envoy;
import dev.envoy.module.staff.StaffState;
import dev.envoy.net.ApolloNetwork;
import dev.envoy.net.ApolloProtos;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;

public final class NametagModule {
    private static final String OVERRIDE = "lunarclient.apollo.nametag.v1.OverrideNametagMessage";
    private static final String RESET = "lunarclient.apollo.nametag.v1.ResetNametagMessage";
    private static final String RESET_ALL = "lunarclient.apollo.nametag.v1.ResetNametagsMessage";
    private NametagModule() { }

    public static void register(ApolloNetwork network) {
        network.register(OVERRIDE, NametagModule::receiveOverride);
        network.register(RESET, NametagModule::receiveReset);
        network.register(RESET_ALL, message -> resetEverything());
    }

    private static void receiveOverride(Any message) throws Exception {
        OverrideData data = readOverride(message.getValue().newCodedInput());
        if (data.player == null) return;
        List<List<AdventureText.Run>> lines = data.lines();
        if (lines.isEmpty()) {
            clearPlayer(data.player);
            return;
        }
        NametagState.override(data.player, lines);
        if (isLocalPlayer(data.player)) StaffState.ownTag(lines);
        Envoy.LOGGER.info("[Nametag] override {} ({} line(s))", data.player, lines.size());
    }

    private static OverrideData readOverride(CodedInputStream wire) throws Exception {
        OverrideData data = new OverrideData();
        for (int tag; (tag = wire.readTag()) != 0;) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1: data.player = ApolloProtos.parseUuid(wire.readBytes()); break;
                case 2: data.legacy.add(wire.readBytes()); break;
                case 3: data.json.add(wire.readString()); break;
                default: wire.skipField(tag); break;
            }
        }
        return data;
    }

    private static void receiveReset(Any message) throws Exception {
        CodedInputStream wire = message.getValue().newCodedInput();
        UUID player = null;
        for (int tag; (tag = wire.readTag()) != 0;) {
            if (WireFormat.getTagFieldNumber(tag) == 1) player = ApolloProtos.parseUuid(wire.readBytes());
            else wire.skipField(tag);
        }
        if (player != null) {
            clearPlayer(player);
            Envoy.LOGGER.info("[Nametag] reset {}", player);
        }
    }

    private static void clearPlayer(UUID player) {
        NametagState.reset(player);
        if (isLocalPlayer(player)) StaffState.ownTag(null);
    }

    private static void resetEverything() {
        NametagState.resetAll();
        StaffState.ownTag(null);
        Envoy.LOGGER.info("[Nametag] reset all");
    }

    private static boolean isLocalPlayer(UUID player) {
        try { return player.equals(Minecraft.getMinecraft().getSession().getProfile().getId()); }
        catch (Throwable unavailable) { return false; }
    }

    private static List<AdventureText.Run> decodeLegacy(ByteString component) throws Exception {
        List<AdventureText.Run> result = new ArrayList<AdventureText.Run>();
        appendComponent(component, -1, "", result);
        return result;
    }

    private static void appendComponent(ByteString bytes, int inheritedColor, String inheritedStyles,
                                        List<AdventureText.Run> result) throws Exception {
        CodedInputStream wire = bytes.newCodedInput();
        String text = "";
        int color = inheritedColor;
        StringBuilder styles = new StringBuilder(inheritedStyles);
        List<ByteString> children = new ArrayList<ByteString>();
        for (int tag; (tag = wire.readTag()) != 0;) {
            switch (WireFormat.getTagFieldNumber(tag)) {
                case 1: text = wire.readString(); break;
                case 2: color = ApolloProtos.parseColorArgb(wire.readBytes()) & 0xFFFFFF; break;
                case 3: readDecorations(wire, tag, styles); break;
                case 4: children.add(wire.readBytes()); break;
                default: wire.skipField(tag); break;
            }
        }
        if (!text.isEmpty()) result.add(new AdventureText.Run(styles.toString() + text, color < 0 ? 0xFFFFFF : color));
        for (ByteString child : children) appendComponent(child, color, styles.toString(), result);
    }

    private static void readDecorations(CodedInputStream wire, int tag, StringBuilder styles) throws Exception {
        if (WireFormat.getTagWireType(tag) == WireFormat.WIRETYPE_LENGTH_DELIMITED) {
            CodedInputStream packed = wire.readBytes().newCodedInput();
            while (!packed.isAtEnd()) addDecoration(packed.readInt32(), styles);
        } else addDecoration(wire.readInt32(), styles);
    }

    private static void addDecoration(int value, StringBuilder styles) {
        char code;
        switch (value) {
            case 1: code = 'k'; break;
            case 2: code = 'l'; break;
            case 3: code = 'm'; break;
            case 4: code = 'n'; break;
            case 5: code = 'o'; break;
            default: return;
        }
        styles.append('\u00A7').append(code);
    }

    private static final class OverrideData {
        UUID player;
        final List<String> json = new ArrayList<String>();
        final List<ByteString> legacy = new ArrayList<ByteString>();
        List<List<AdventureText.Run>> lines() throws Exception {
            List<List<AdventureText.Run>> result = new ArrayList<List<AdventureText.Run>>();
            if (!json.isEmpty()) {
                for (String line : json) result.add(AdventureText.parse(line));
            } else {
                for (ByteString line : legacy) result.add(decodeLegacy(line));
            }
            return result;
        }
    }
}
