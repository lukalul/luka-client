package dev.envoy.module.notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import dev.envoy.Envoy;
import dev.envoy.module.nametag.AdventureText;
import dev.envoy.module.nametag.AdventureText.Run;
import dev.envoy.net.ApolloNetwork;
import dev.envoy.net.ApolloProtos;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public final class NotificationModule {
    private static final String DISPLAY = "lunarclient.apollo.notification.v1.DisplayNotificationMessage";
    private static final String RESET = "lunarclient.apollo.notification.v1.ResetNotificationsMessage";
    private static final long DEFAULT_DISPLAY_MILLIS = 5000L;

    private NotificationModule() {
    }

    public static void register(ApolloNetwork network) {
        network.register(DISPLAY, message -> enqueue(NotificationPacket.read(message)));
        network.register(RESET, message -> clearQueue());
    }

    private static void enqueue(NotificationPacket packet) {
        List<List<Run>> headings = packet.text(packet.richTitles, packet.plainTitle, 0xFFFFFF);
        List<List<Run>> body = packet.text(packet.richDescriptions, packet.plainDescription, 0xAAAAAA);
        if (headings.isEmpty() && body.isEmpty()) return;

        ResourceLocation texture = packet.item == null && packet.textureName != null
            && !packet.textureName.isEmpty() ? new ResourceLocation(packet.textureName) : null;
        long duration = packet.duration > 0L ? packet.duration : DEFAULT_DISPLAY_MILLIS;
        NotificationState.add(new NotificationState.Notification(
            headings, body, packet.item, texture, System.currentTimeMillis(), duration));
        String loggedTitle = packet.plainTitle != null ? packet.plainTitle
            : packet.richTitles.isEmpty() ? "" : packet.richTitles.get(0);
        Envoy.LOGGER.info("[Notification] display title='{}' ({}ms)", loggedTitle, duration);
    }

    private static void clearQueue() {
        NotificationState.resetAll();
        Envoy.LOGGER.info("[Notification] reset");
    }

    private static final class NotificationPacket {
        private String plainTitle;
        private String plainDescription;
        private String textureName;
        private long duration;
        private final List<String> richTitles = new ArrayList<>();
        private final List<String> richDescriptions = new ArrayList<>();
        private ItemStack item;

        private static NotificationPacket read(Any message) throws Exception {
            NotificationPacket packet = new NotificationPacket();
            CodedInputStream input = message.getValue().newCodedInput();
            for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
                int field = WireFormat.getTagFieldNumber(tag);
                if (field == 1) packet.plainTitle = input.readString();
                else if (field == 2) packet.plainDescription = input.readString();
                else if (field == 3) packet.textureName = input.readString();
                else if (field == 4) packet.duration = ApolloProtos.parseDurationMillis(input.readBytes());
                else if (field == 5) packet.richTitles.add(input.readString());
                else if (field == 6) packet.richDescriptions.add(input.readString());
                else if (field == 7) packet.item = decodeIcon(input.readBytes());
                else input.skipField(tag);
            }
            return packet;
        }

        private List<List<Run>> text(List<String> rich, String plain, int fallbackColor) {
            if (!rich.isEmpty()) {
                List<List<Run>> result = new ArrayList<>();
                for (String json : rich) {
                    List<Run> line = AdventureText.parse(json);
                    if (!line.isEmpty()) result.add(line);
                }
                return result;
            }
            if (plain == null || plain.isEmpty()) return Collections.emptyList();
            return Collections.singletonList(Collections.singletonList(new Run(plain, fallbackColor)));
        }

        private static ItemStack decodeIcon(ByteString wrapper) throws Exception {
            CodedInputStream input = wrapper.newCodedInput();
            ItemStack result = null;
            for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
                if (WireFormat.getTagFieldNumber(tag) == 1) result = decodeStack(input.readBytes());
                else input.skipField(tag);
            }
            return result;
        }

        private static ItemStack decodeStack(ByteString bytes) throws Exception {
            CodedInputStream input = bytes.newCodedInput();
            Integer numericId = null;
            String namedId = null;
            for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
                int field = WireFormat.getTagFieldNumber(tag);
                if (field == 1) numericId = Integer.valueOf(input.readInt32());
                else if (field == 2) namedId = input.readString();
                else input.skipField(tag);
            }
            Item resolved = namedId != null && !namedId.isEmpty()
                ? dev.luka.compat.Mc17.itemByNameOrId(namedId)
                : numericId != null && numericId.intValue() >= 0 ? Item.getItemById(numericId.intValue()) : null;
            return resolved == null ? null : new ItemStack(resolved);
        }
    }
}
