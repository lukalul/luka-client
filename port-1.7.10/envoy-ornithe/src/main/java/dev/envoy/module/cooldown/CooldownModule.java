package dev.envoy.module.cooldown;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import dev.envoy.Envoy;
import dev.envoy.net.ApolloNetwork;
import dev.envoy.net.ApolloProtos;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class CooldownModule {
    private static final String DISPLAY = "lunarclient.apollo.cooldown.v1.DisplayCooldownMessage";
    private static final String REMOVE = "lunarclient.apollo.cooldown.v1.RemoveCooldownMessage";
    private static final String RESET_ALL = "lunarclient.apollo.cooldown.v1.ResetCooldownsMessage";
    private static final int DEFAULT_CIRCLE_START = 0x99595959;
    private static final int DEFAULT_CIRCLE_END = 0xFFE5E5E5;
    private static final int DEFAULT_EDGE = 0xFF000000;
    private static final int DEFAULT_TEXT = 0xFFFFFFFF;
    private CooldownModule() { }

    public static void register(ApolloNetwork network) {
        network.register(DISPLAY, CooldownModule::display);
        network.register(REMOVE, CooldownModule::remove);
        network.register(RESET_ALL, message -> reset());
    }

    private static void display(Any envelope) throws Exception {
        DisplayDraft draft = DisplayDraft.read(envelope.getValue().newCodedInput());
        if (!draft.valid()) return;
        CooldownState.display(draft.name, draft.millis, draft.icon, draft.style[0], draft.style[1], draft.style[2], draft.style[3]);
        Envoy.LOGGER.info("[Cooldown] display '{}' {}ms icon={}", draft.name, draft.millis,
            draft.icon == null ? "none" : draft.icon.getItem());
    }

    private static void remove(Any envelope) throws Exception {
        CodedInputStream wire = envelope.getValue().newCodedInput();
        String key = null;
        for (int tag = wire.readTag(); tag != 0; tag = wire.readTag()) {
            if (WireFormat.getTagFieldNumber(tag) == 1) key = wire.readString(); else wire.skipField(tag);
        }
        if (key != null) { CooldownState.remove(key); Envoy.LOGGER.info("[Cooldown] remove '{}'", key); }
    }

    private static void reset() { CooldownState.resetAll(); Envoy.LOGGER.info("[Cooldown] reset all"); }

    private static final class DisplayDraft {
        String name;
        long millis;
        ItemStack icon;
        int[] style = {DEFAULT_CIRCLE_START, DEFAULT_CIRCLE_END, DEFAULT_EDGE, DEFAULT_TEXT};

        static DisplayDraft read(CodedInputStream wire) throws Exception {
            DisplayDraft draft = new DisplayDraft();
            for (int tag = wire.readTag(); tag != 0; tag = wire.readTag()) {
                switch (WireFormat.getTagFieldNumber(tag)) {
                    case 1: draft.name = wire.readString(); break;
                    case 2: draft.millis = ApolloProtos.parseDurationMillis(wire.readBytes()); break;
                    case 3: draft.icon = decodeIcon(wire.readBytes()); break;
                    case 4: draft.style = decodeStyle(wire.readBytes()); break;
                    default: wire.skipField(tag);
                }
            }
            return draft;
        }
        boolean valid() { return name != null && millis > 0; }
    }

    private static ItemStack decodeIcon(ByteString bytes) throws Exception {
        CodedInputStream wire = bytes.newCodedInput();
        ItemStack result = null;
        for (int tag = wire.readTag(); tag != 0; tag = wire.readTag()) {
            if (WireFormat.getTagFieldNumber(tag) == 1) result = decodeItem(wire.readBytes()); else wire.skipField(tag);
        }
        return result;
    }

    private static ItemStack decodeItem(ByteString bytes) throws Exception {
        CodedInputStream wire = bytes.newCodedInput();
        int numericId = -1;
        String textualId = null;
        for (int tag = wire.readTag(); tag != 0; tag = wire.readTag()) {
            int field = WireFormat.getTagFieldNumber(tag);
            if (field == 1) numericId = wire.readInt32();
            else if (field == 2) textualId = wire.readString();
            else wire.skipField(tag);
        }
        Item item = textualId != null && !textualId.isEmpty() ? dev.luka.compat.Mc17.itemByNameOrId(textualId)
            : numericId >= 0 ? Item.getItemById(numericId) : null;
        return item == null ? null : new ItemStack(item);
    }

    private static int[] decodeStyle(ByteString bytes) throws Exception {
        int[] result = {DEFAULT_CIRCLE_START, DEFAULT_CIRCLE_END, DEFAULT_EDGE, DEFAULT_TEXT};
        CodedInputStream wire = bytes.newCodedInput();
        for (int tag = wire.readTag(); tag != 0; tag = wire.readTag()) {
            int field = WireFormat.getTagFieldNumber(tag);
            if (field >= 1 && field <= result.length) result[field - 1] = ApolloProtos.parseColorArgb(wire.readBytes());
            else wire.skipField(tag);
        }
        return result;
    }
}
