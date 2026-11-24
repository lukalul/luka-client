package dev.envoy.net;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.protobuf.Any;

import dev.envoy.Envoy;
import dev.envoy.LunarHost;
import dev.envoy.module.combat.CombatModule;
import dev.envoy.module.combat.CombatState;
import dev.envoy.module.events.EnvoyEvents;
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
    private volatile NetworkManager playConnection;
    private static final long PROXY_MOVE_WINDOW_MILLIS = 5000L;
    private volatile long respawnAt;
    private volatile boolean loginNameSeen;
    private volatile boolean nameIsRepeated;
    private volatile long joinedAt;
    private volatile long lastRespawnAt;
    private volatile long forgotAt;
    /** The player's dimension as the server last set it (join or respawn), and whether the last respawn changed it. */
    private volatile int dimension;
    private volatile boolean lastRespawnChangedWorld;
    /** BungeeCord and Velocity move a 1.8 player to another server with two respawns straight after each other. */
    private static final long DOUBLE_RESPAWN_MILLIS = 1500L;
    /** A server may send its name more than once while the player logs in; only later repeats mean an animated name. */
    private static final long LOGIN_SETTLE_MILLIS = 15000L;
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
        playConnection = manager;
        dev.envoy.rules.ServerRules.reset();
        forgetServer(null);
        respawnAt = 0L;
        lastRespawnAt = 0L;
        joinedAt = System.currentTimeMillis();
        // the join already forgot the last server; respawns a server sends while the player logs in are not a move
        forgotAt = joinedAt;
        loginNameSeen = false;
        nameIsRepeated = false;
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

    /** Every connection ends here, server list pings included; only the end of the one being played on forgets the server. */
    public void onClosed(NetworkManager manager) {
        if (manager != playConnection) return;
        playConnection = null;
        dev.envoy.rules.ServerRules.reset();
        CombatState.clearServer();
        forgetServer("the connection ended");
    }

    /** Dying and changing dimension respawn the player too, so a respawn alone says nothing; see {@link #onServerName()}. */
    public void onRespawn(int newDimension) {
        long now = System.currentTimeMillis();
        respawnAt = now;
        boolean changedWorld = newDimension != dimension;
        dimension = newDimension;
        // A proxy moves the player with two respawns straight after each other, the first into another dimension so the
        // client drops the old world. Some servers send two respawns on death as well, in the same dimension: that is no
        // move, and treating it as one threw away the nametags the server had just sent for the new life.
        if (now - lastRespawnAt < DOUBLE_RESPAWN_MILLIS && (changedWorld || lastRespawnChangedWorld)) {
            forgetMove("a proxy moved the player to another server (two respawns)");
        }
        lastRespawnAt = now;
        lastRespawnChangedWorld = changedWorld;
    }

    /** The dimension the server put the player in when they joined it. */
    public void onJoinDimension(int joinedDimension) {
        dimension = joinedDimension;
        lastRespawnChangedWorld = false;
    }

    /**
     * A server sends its name once, when a player logs in to it. Getting a name right after a respawn therefore means
     * a proxy moved the player to another server: BungeeCord sends no new join for that, only a respawn. A server
     * that also sends its name at other times (an animated name) is not judged this way.
     */
    public void onServerName() {
        if (!loginNameSeen) {
            loginNameSeen = true;
            return;
        }
        boolean afterRespawn = System.currentTimeMillis() - respawnAt < PROXY_MOVE_WINDOW_MILLIS;
        respawnAt = 0L;
        if (!afterRespawn) {
            if (System.currentTimeMillis() - joinedAt > LOGIN_SETTLE_MILLIS) nameIsRepeated = true;
        } else if (!nameIsRepeated) forgetMove("a proxy moved the player to another server");
    }

    /**
     * Drops what the last server showed: name tags, staff mode, waypoints, team members and the rest. None of it is
     * true on the next server, and that server only sends what it wants to change, so nothing else would remove it.
     * The rules about allowed mods are not touched here: a proxy may have sent those once for the whole network, and
     * dropping them on a move between its servers would turn disallowed mods back on.
     */
    /**
     * One move to another server can show both signs (two respawns, then the new server's name): it is forgotten once, so
     * what the new server sends in between is not thrown away again.
     */
    private void forgetMove(String reason) {
        long now = System.currentTimeMillis();
        if (now - forgotAt < 5000L) return;
        forgotAt = now;
        forgetServer(reason);
    }

    private static void forgetServer(String reason) {
        dev.envoy.module.staff.StaffState.clear();
        dev.envoy.module.nametag.NametagState.resetAll();
        dev.envoy.module.team.TeamState.reset();
        dev.envoy.module.waypoint.WaypointState.resetAll();
        dev.envoy.module.beam.BeamState.resetAll();
        dev.envoy.module.border.BorderState.resetAll();
        dev.envoy.module.cooldown.CooldownState.resetAll();
        dev.envoy.module.hologram.HologramState.resetAll();
        dev.envoy.module.notification.NotificationState.resetAll();
        dev.envoy.module.tntcountdown.TntCountdownState.clear();
        dev.envoy.module.coloredfire.ColoredFireState.resetAll();
        if (reason != null) Envoy.LOGGER.info("[Apollo] forgot the last server's state: {}", reason);
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
        String[] advertised = advertisedChannels();
        if (manager == null || advertised.length == 0) return;
        String channels = String.join(CHANNEL_SEPARATOR, advertised);
        PacketBuffer data = new PacketBuffer(Unpooled.buffer());
        data.writeBytes(channels.getBytes(StandardCharsets.UTF_8));
        manager.sendPacket(new C17PacketCustomPayload(ApolloProtocol.REGISTER_CHANNEL, data));
        Envoy.LOGGER.info("[Apollo] announced channels to the server: {}", channels.replace(CHANNEL_SEPARATOR, ", "));
    }

    private String[] advertisedChannels() {
        if (LunarHost.isLunar()) return new String[0];
        return new String[] { ApolloProtocol.CHANNEL, EnvoyEvents.CHANNEL };
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
        return CombatModule.OVERRIDE.equals(type)
            || CombatModule.RESET.equals(type);
    }

    private static Set<String> concurrentSet() {
        return Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    }
}
