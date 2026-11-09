package dev.envoy.net;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.protobuf.Any;

import dev.envoy.Envoy;
import dev.envoy.LunarHost;
import dev.envoy.module.chat.EnvoyChat;
import dev.envoy.module.combat.CombatModule;
import dev.envoy.module.combat.CombatState;
import dev.envoy.module.events.EnvoyEvents;
import dev.envoy.module.glow.GlowModule;
import dev.envoy.module.lightning.EnvoyLightning;
import dev.envoy.module.vignette.VignetteModule;
import io.netty.buffer.Unpooled;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;

public final class ApolloNetwork {
    private static final ApolloNetwork INSTANCE = new ApolloNetwork();
    private static final String CHANNEL_SEPARATOR = "\0";
    private static final Set<String> REPORTED_INCOMING = concurrentSet();

    private final Map<String, ApolloPacketHandler> routes = new ConcurrentHashMap<>();
    private final Set<String> lunarPassThrough = concurrentSet();
    private volatile NetworkManager registrationTarget;
    private boolean logAllIncoming = true;

    private ApolloNetwork() {
    }

    public static ApolloNetwork getInstance() {
        return INSTANCE;
    }

    public void register(String messageType, ApolloPacketHandler handler) {
        routes.put(messageType, handler);
        Envoy.LOGGER.info("[Apollo] handler registered for {}", messageType);
    }

    public void setLogAllIncoming(boolean logAllIncoming) {
        this.logAllIncoming = logAllIncoming;
    }

    public void onConnect(NetworkManager manager) {
        registrationTarget = manager;
    }

    public void onJoinGame(NetworkManager manager) {
        dev.envoy.rules.ServerRules.reset();
        if (registrationTarget != manager) return;
        registrationTarget = null;
        announceChannels(manager);
    }

    public void onDisconnect() {
        dev.envoy.rules.ServerRules.reset();
        CombatState.clearServer();
        dev.envoy.module.staff.StaffState.clear();
        Envoy.LOGGER.info("[Apollo] disconnected from the server");
    }

    public boolean handleIncomingIfOwned(byte[] payload) {
        if (!LunarHost.isLunar()) {
            handleIncoming(payload);
            return true;
        }
        Any envelope = parseSilently(payload);
        if (envelope == null) return false;

        String type = ApolloProtocol.messageType(envelope.getTypeUrl());
        if (!isEnvoyOwned(type)) {
            if (lunarPassThrough.add(type)) Envoy.LOGGER.info("[Apollo] left to Lunar Client: {}", type);
            return false;
        }
        dispatch(envelope, type, payload.length);
        return true;
    }

    public void handleIncoming(byte[] payload) {
        final Any envelope;
        try {
            envelope = Any.parseFrom(payload);
        } catch (Exception failure) {
            Envoy.LOGGER.warn("[Apollo] unreadable payload ({} bytes): {}", payload.length, failure.toString());
            return;
        }
        dispatch(envelope, ApolloProtocol.messageType(envelope.getTypeUrl()), payload.length);
    }

    private void announceChannels(NetworkManager manager) {
        if (manager == null) return;
        String channels = String.join(CHANNEL_SEPARATOR, advertisedChannels());
        PacketBuffer data = new PacketBuffer(Unpooled.buffer());
        data.writeBytes(channels.getBytes(StandardCharsets.UTF_8));
        manager.sendPacket(new C17PacketCustomPayload(ApolloProtocol.REGISTER_CHANNEL, data));
        Envoy.LOGGER.info("[Apollo] announced channels to the server: {}", channels.replace(CHANNEL_SEPARATOR, ", "));
    }

    private String[] advertisedChannels() {
        if (LunarHost.isLunar()) return new String[] { EnvoyChat.CHANNEL, EnvoyLightning.CHANNEL };
        return new String[] { ApolloProtocol.CHANNEL, EnvoyChat.CHANNEL, EnvoyLightning.CHANNEL, EnvoyEvents.CHANNEL };
    }

    private void dispatch(Any envelope, String type, int byteCount) {
        if (logAllIncoming && REPORTED_INCOMING.add(type)) {
            Envoy.LOGGER.info("[Apollo] received {} ({} bytes), further messages of this type are not logged", type, byteCount);
        }
        ApolloPacketHandler destination = routes.get(type);
        if (destination == null) return;
        try {
            destination.handle(envelope);
        } catch (Exception failure) {
            Envoy.LOGGER.error("[Apollo] error in the handler for {}", type, failure);
        }
    }

    private static Any parseSilently(byte[] payload) {
        try {
            return Any.parseFrom(payload);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isEnvoyOwned(String type) {
        return type.startsWith(GlowModule.MESSAGE_PREFIX)
            || type.startsWith(VignetteModule.MESSAGE_PREFIX)
            || CombatModule.OVERRIDE.equals(type)
            || CombatModule.RESET.equals(type);
    }

    private static Set<String> concurrentSet() {
        return Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    }
}
