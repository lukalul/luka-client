package dev.envoy.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.envoy.Envoy;
import dev.envoy.net.ApolloNetwork;
import dev.envoy.net.ApolloProtocol;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import dev.envoy.command.EnvoyCommand;
import dev.envoy.EnvoySettings;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Intercepts incoming packets at the Netty level to grab the Apollo traffic on the {@code lunar:apollo}
 * channel before the vanilla client handles it.
 */
@Mixin(NetworkManager.class)
public class MixinNetworkManager {
    private static final Set<String> OBSERVED_CHANNELS =
        Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    @Inject(method = "channelInactive", at = @At("HEAD"), require = 0)
    private void envoy$clearConnection(ChannelHandlerContext context, CallbackInfo callback) {
        Envoy.onDisconnect();
        try {
            ApolloNetwork.getInstance().onClosed((NetworkManager) (Object) this);
        } catch (Throwable failure) {
            Envoy.problem("connection end", failure);
        }
    }

    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void envoy$filterOutgoingPacket(Packet<?> packet, CallbackInfo callback) {
        try {
            if (isBlockedHandshake(packet) || executeLocalCommand(packet)) callback.cancel();
        } catch (Throwable failure) {
            Envoy.problem("client command", failure);
        }
    }

    private static boolean isBlockedHandshake(Packet<?> packet) {
        if (!EnvoySettings.blockFreelookHandshake || !(packet instanceof C17PacketCustomPayload)) return false;
        return ((C17PacketCustomPayload) packet).getChannelName().startsWith("freelook:");
    }

    private static boolean executeLocalCommand(Packet<?> packet) {
        if (!(packet instanceof C01PacketChatMessage)) return false;
        String message = ((C01PacketChatMessage) packet).getMessage().trim();
        if (!message.equalsIgnoreCase("/envoy") && !message.toLowerCase().startsWith("/envoy ")) return false;
        String arguments = message.length() <= "/envoy".length() ? "" : message.substring("/envoy".length()).trim();
        EnvoyCommand.execute(arguments.isEmpty() ? new String[0] : arguments.split("\\s+"));
        return true;
    }

    @Inject(method = "channelRead0", at = @At("HEAD"), cancellable = true, require = 0)
    private void envoy$routeIncomingPacket(ChannelHandlerContext context, Packet<?> packet, CallbackInfo callback) {
        try {
            if (packet instanceof net.minecraft.network.play.server.S01PacketJoinGame) {
                Envoy.onJoinGame((NetworkManager) (Object) this);
                ApolloNetwork.getInstance().onJoinDimension(
                    ((net.minecraft.network.play.server.S01PacketJoinGame) packet).getDimension());
            }
            if (packet instanceof net.minecraft.network.play.server.S07PacketRespawn) {
                ApolloNetwork.getInstance().onRespawn(
                    ((net.minecraft.network.play.server.S07PacketRespawn) packet).getDimensionID());
            }
            if (!(packet instanceof S3FPacketCustomPayload)) return;
            S3FPacketCustomPayload payload = (S3FPacketCustomPayload) packet;
            String channel = payload.getChannelName();
            if (channel.equals("MC|Brand")) ApolloNetwork.getInstance().onServerName();
            logFirstPayload(channel, payload.getBufferData());
            PayloadKind kind = classify(channel);
            if (kind == PayloadKind.OTHER) return;
            boolean consumed = dispatch(kind, copyBytes(payload.getBufferData()));
            if (consumed) callback.cancel();
        } catch (Throwable failure) {
            Envoy.problem("packet receive", failure);
        }
    }

    private static PayloadKind classify(String channel) {
        if (ApolloProtocol.CHANNEL.equals(channel)) return PayloadKind.APOLLO;
        if (channel.equalsIgnoreCase("badlion:mods") || channel.equalsIgnoreCase("badlion:modapi")) return PayloadKind.BADLION;
        if (channel.equalsIgnoreCase("apollo:json")) return PayloadKind.APOLLO_JSON;
        return PayloadKind.OTHER;
    }

    private static boolean dispatch(PayloadKind kind, byte[] bytes) {
        switch (kind) {
            case BADLION:
                dev.envoy.rules.BadlionRules.parse(bytes);
                return false;
            case APOLLO_JSON:
                for (dev.envoy.module.combat.CombatProtocol.Configurable setting : dev.envoy.rules.ApolloJson.parse(bytes)) {
                    dev.envoy.module.combat.CombatModule.apply(setting);
                }
                return false;
            case APOLLO:
                return ApolloNetwork.getInstance().handleIncomingIfOwned(bytes);
            default:
                return false;
        }
    }

    private static byte[] copyBytes(PacketBuffer buffer) {
        byte[] copy = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), copy);
        return copy;
    }

    private static void logFirstPayload(String channel, PacketBuffer buffer) {
        if (!OBSERVED_CHANNELS.add(channel)) return;
        int size = buffer.readableBytes();
        String detail;
        if (channel.equalsIgnoreCase("REGISTER") || channel.equalsIgnoreCase("minecraft:register")) {
            detail = " channels: " + preview(buffer, 512);
        } else if (channel.equalsIgnoreCase("MC|Brand") || channel.equalsIgnoreCase("minecraft:brand")) {
            detail = " brand: " + preview(buffer, 128);
        } else if (size > 0 && !ApolloProtocol.CHANNEL.equals(channel)) {
            detail = " preview: " + preview(buffer, 1200);
        } else {
            detail = "";
        }
        Envoy.LOGGER.info("[Channels] server sent plugin channel {} ({} bytes){}", channel, size, detail);
    }

    private static String preview(PacketBuffer buffer, int limit) {
        byte[] sample = new byte[Math.min(buffer.readableBytes(), limit)];
        buffer.getBytes(buffer.readerIndex(), sample);
        String text = new String(sample, StandardCharsets.UTF_8);
        StringBuilder safe = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            safe.append(character >= 32 && character < 127 ? character : ' ');
        }
        return safe.toString().trim();
    }

    private enum PayloadKind {
        APOLLO, BADLION, APOLLO_JSON, OTHER
    }
}
