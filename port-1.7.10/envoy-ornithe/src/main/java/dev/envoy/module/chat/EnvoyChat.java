package dev.envoy.module.chat;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import dev.envoy.Envoy;
import dev.envoy.module.nametag.AdventureText;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

/** Receives Envoy rich-chat commands and applies them on Minecraft's client thread. */
public final class EnvoyChat {
    public static final String CHANNEL = "envoy:chat";
    private static final Set<Integer> TRACKED_LINES = ConcurrentHashMap.newKeySet();

    private EnvoyChat() { }

    public static void handleIncoming(byte[] payload) {
        try (DataInputStream stream = new DataInputStream(new ByteArrayInputStream(payload))) {
            dispatch(stream.readUnsignedByte(), stream);
        } catch (Exception failure) {
            Envoy.LOGGER.warn("[Chat] unreadable payload: {}", failure.toString());
        }
    }

    private static void dispatch(int command, DataInputStream stream) throws IOException {
        if (command == 0) {
            queueDisplay(stream.readInt(), encodeRuns(AdventureText.parse(stream.readUTF())));
        } else if (command == 1) {
            queueRemoval(stream.readInt());
        } else if (command == 2) {
            queueClear();
        }
    }

    private static void queueDisplay(int id, String text) {
        TRACKED_LINES.add(id);
        Minecraft client = Minecraft.getMinecraft();
        client.addScheduledTask(() -> {
            if (id != 0) client.ingameGUI.getChatGUI().deleteChatLine(id);
            client.ingameGUI.getChatGUI()
                .printChatMessageWithOptionalDeletion(new ChatComponentText(text), id);
        });
    }

    private static void queueRemoval(int id) {
        TRACKED_LINES.remove(id);
        Minecraft client = Minecraft.getMinecraft();
        client.addScheduledTask(() -> client.ingameGUI.getChatGUI().deleteChatLine(id));
    }

    private static void queueClear() {
        Integer[] snapshot = TRACKED_LINES.toArray(new Integer[TRACKED_LINES.size()]);
        TRACKED_LINES.clear();
        Minecraft client = Minecraft.getMinecraft();
        client.addScheduledTask(() -> {
            for (Integer id : snapshot) client.ingameGUI.getChatGUI().deleteChatLine(id);
        });
    }

    private static String encodeRuns(List<AdventureText.Run> runs) {
        StringBuilder encoded = new StringBuilder();
        for (AdventureText.Run run : runs) {
            encoded.append(HexTextRenderer.hexPrefix(run.rgb));
            encoded.append(run.display);
        }
        return encoded.toString();
    }
}
