package dev.envoy.module.title;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;

import dev.envoy.Envoy;
import dev.envoy.EnvoySettings;
import dev.envoy.module.nametag.AdventureText;
import dev.envoy.module.nametag.AdventureText.Run;
import dev.envoy.net.ApolloNetwork;
import dev.envoy.net.ApolloProtos;
import dev.envoy.render.TitleRenderer;
import net.minecraft.client.Minecraft;

public final class TitleModule {
    private static final String DISPLAY = "lunarclient.apollo.title.v1.DisplayTitleMessage";
    private static final String RESET = "lunarclient.apollo.title.v1.ResetTitlesMessage";
    private static final int TYPE_SUBTITLE = 2;
    private static final int DEFAULT_FADE_IN = 10;
    private static final int DEFAULT_DISPLAY = 70;
    private static final int DEFAULT_FADE_OUT = 20;

    private TitleModule() {
    }

    public static void register(ApolloNetwork network) {
        network.register(DISPLAY, message -> show(TitlePacket.decode(message)));
        network.register(RESET, message -> scheduleClear());
    }

    private static void show(TitlePacket packet) {
        if (!EnvoySettings.titles) return;
        String text = packet.preferredText();
        if (text == null || text.isEmpty()) return;

        final int fadeInTicks = durationTicks(packet.fadeInMillis, DEFAULT_FADE_IN);
        final int stayTicks = durationTicks(packet.stayMillis, DEFAULT_DISPLAY);
        final int fadeOutTicks = durationTicks(packet.fadeOutMillis, DEFAULT_FADE_OUT);
        final boolean subtitle = packet.kind == TYPE_SUBTITLE;
        scheduleOverlay(text, subtitle, fadeInTicks, stayTicks, fadeOutTicks);
        Envoy.LOGGER.info("[Title] {} '{}' ({}/{}/{} ticks)",
            subtitle ? "subtitle" : "title", text, fadeInTicks, stayTicks, fadeOutTicks);
    }

    private static void scheduleOverlay(final String text, final boolean subtitle,
                                        final int fadeIn, final int stay, final int fadeOut) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            Minecraft client = Minecraft.getMinecraft();
            if (client.ingameGUI == null) return;
            TitleRenderer.setTimes(fadeIn, stay, fadeOut);
            if (subtitle) TitleRenderer.showSubtitle(text);
            else TitleRenderer.showTitle(text);
        });
    }

    private static void scheduleClear() {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            TitleRenderer.clear();
        });
        Envoy.LOGGER.info("[Title] reset");
    }

    private static int durationTicks(long millis, int fallback) {
        if (millis <= 0L) return fallback;
        long rounded = (millis + 25L) / 50L;
        return (int) Math.max(1L, rounded);
    }

    private static String coloredRuns(List<Run> runs) {
        StringBuilder result = new StringBuilder();
        for (Run run : runs) appendColored(result, run.rgb, run.display);
        return result.toString();
    }

    private static void appendColored(StringBuilder target, int rgb, String text) {
        String digits = String.format("%06X", rgb & 0xFFFFFF);
        target.append('\u00A7').append('x');
        for (int index = 0; index < digits.length(); index++) {
            target.append('\u00A7').append(digits.charAt(index));
        }
        target.append(text);
    }

    private static String flattenComponent(ByteString root) throws Exception {
        StringBuilder text = new StringBuilder();
        Deque<ComponentNode> pending = new ArrayDeque<>();
        pending.push(new ComponentNode(root, 0xFFFFFF));
        while (!pending.isEmpty()) {
            ComponentNode current = pending.pop();
            ParsedComponent component = ParsedComponent.read(current.data, current.inheritedColor);
            if (component.content != null && !component.content.isEmpty()) {
                appendColored(text, component.color, component.content);
            }
            for (int index = component.children.size() - 1; index >= 0; index--) {
                pending.push(new ComponentNode(component.children.get(index), component.color));
            }
        }
        return text.toString();
    }

    private static final class TitlePacket {
        private int kind = 1;
        private String componentText;
        private String adventureJson;
        private long fadeInMillis;
        private long stayMillis;
        private long fadeOutMillis;

        private String preferredText() {
            if (adventureJson != null && !adventureJson.isEmpty()) {
                String rich = coloredRuns(AdventureText.parse(adventureJson));
                if (!rich.isEmpty()) return rich;
            }
            return componentText;
        }

        private static TitlePacket decode(Any message) throws Exception {
            TitlePacket packet = new TitlePacket();
            CodedInputStream input = message.getValue().newCodedInput();
            for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
                int field = WireFormat.getTagFieldNumber(tag);
                if (field == 1) packet.kind = input.readEnum();
                else if (field == 2) packet.componentText = flattenComponent(input.readBytes());
                else if (field == 4) packet.fadeInMillis = ApolloProtos.parseDurationMillis(input.readBytes());
                else if (field == 5) packet.stayMillis = ApolloProtos.parseDurationMillis(input.readBytes());
                else if (field == 6) packet.fadeOutMillis = ApolloProtos.parseDurationMillis(input.readBytes());
                else if (field == 7) packet.adventureJson = input.readString();
                else input.skipField(tag);
            }
            return packet;
        }
    }

    private static final class ComponentNode {
        private final ByteString data;
        private final int inheritedColor;

        private ComponentNode(ByteString data, int inheritedColor) {
            this.data = data;
            this.inheritedColor = inheritedColor;
        }
    }

    private static final class ParsedComponent {
        private String content;
        private int color;
        private final java.util.ArrayList<ByteString> children = new java.util.ArrayList<>();

        private static ParsedComponent read(ByteString data, int inheritedColor) throws Exception {
            ParsedComponent result = new ParsedComponent();
            result.color = inheritedColor;
            CodedInputStream input = data.newCodedInput();
            for (int tag = input.readTag(); tag != 0; tag = input.readTag()) {
                int field = WireFormat.getTagFieldNumber(tag);
                if (field == 1) result.content = input.readString();
                else if (field == 2) result.color = ApolloProtos.parseColorArgb(input.readBytes()) & 0xFFFFFF;
                else if (field == 4) result.children.add(input.readBytes());
                else input.skipField(tag);
            }
            return result;
        }
    }
}
