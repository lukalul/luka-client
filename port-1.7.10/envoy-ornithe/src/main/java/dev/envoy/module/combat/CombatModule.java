package dev.envoy.module.combat;

import com.google.protobuf.Value;
import dev.envoy.Envoy;
import dev.envoy.net.ApolloNetwork;
import dev.envoy.rules.ServerRules;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatModule {
    public static final String OVERRIDE = "lunarclient.apollo.configurable.v1.OverrideConfigurableSettingsMessage";
    public static final String RESET = "lunarclient.apollo.configurable.v1.ResetConfigurableSettingsMessage";
    private static final Set<String> REPORTED_MODULES = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static boolean payloadReported;
    private CombatModule() { }

    public static void register(ApolloNetwork network) {
        network.register(OVERRIDE, message -> handleOverride(message.getValue().toByteArray()));
        network.register(RESET, message -> resetServerSettings());
    }

    public static void handleOverride(byte[] bytes) throws Exception {
        reportPayloadOnce(bytes);
        for (CombatProtocol.Configurable settings : CombatProtocol.parseOverride(bytes)) apply(settings);
    }

    public static void apply(CombatProtocol.Configurable settings) {
        ServerRules.updateApollo(settings.module, settings.enable, settings.properties);
        if (!"combat".equals(settings.module)) {
            if (REPORTED_MODULES.add(settings.module)) Envoy.LOGGER.info("[Combat] ignoring configurable module {}", settings.module);
            return;
        }
        if (!settings.enable) {
            Envoy.LOGGER.info("[Combat] combat module disabled by server");
            CombatState.clearServer();
            return;
        }
        for (Map.Entry<String, Value> property : settings.properties.entrySet()) applyProperty(property.getKey(), property.getValue());
    }

    private static void resetServerSettings() {
        CombatState.clearServer();
        ServerRules.resetApollo();
    }

    /** -Dluka.serverCombat17=false lets servers' Lunar combat settings apply on 1.7.10 again (not recommended). */
    private static final String IGNORE_SERVER_COMBAT_ON_1_7 = System.getProperty("luka.serverCombat17", "ignore");

    private static void applyProperty(String key, Value value) {
        Envoy.LOGGER.info("[Combat] received {}={}", key, value);
        if (value.getKindCase() != Value.KindCase.BOOL_VALUE) return;
        // Lunar's combat settings are meant for Lunar's 1.8+ clients; Lunar has no 1.7.10 client. A 1.7.10 player who clicks
        // past the vanilla miss penalty looks like an autoclicker to server anti-cheats (Minemen's AntiGamingChair banned a
        // player for it), so on 1.7.10 the server's request is noted and not applied. The player's own switches still work.
        if (!IGNORE_SERVER_COMBAT_ON_1_7.equals("false")) {
            Envoy.LOGGER.info("[Combat] {}={} ignored on 1.7.10 (Lunar combat settings are for 1.8+)", key, value.getBoolValue());
            return;
        }
        boolean enabled = value.getBoolValue();
        switch (key) {
            case "disable-miss-penalty": CombatState.setServerDisableMissPenalty(enabled); break;
            case "disable-block-miss-penalty": CombatState.setServerDisableBlockMissPenalty(enabled); break;
            case "allow-dig-and-use": CombatState.setServerAllowDigAndUse(enabled); break;
            default: break;
        }
    }

    private static synchronized void reportPayloadOnce(byte[] bytes) {
        if (payloadReported) return;
        payloadReported = true;
        Envoy.LOGGER.info("[Combat] configurable settings, readable parts ({} bytes): {}", bytes.length, readable(bytes, 2400));
        Envoy.LOGGER.info("[Combat] configurable settings, hex: {}", hex(bytes, 4096));
    }

    private static String readable(byte[] bytes, int limit) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < bytes.length && result.length() < limit; i++) {
            int value = bytes[i] & 255;
            result.append(value >= 32 && value < 127 ? (char) value : ' ');
        }
        return result.toString().replaceAll(" {2,}", " | ").trim();
    }

    private static String hex(byte[] bytes, int limit) {
        StringBuilder result = new StringBuilder(Math.min(bytes.length, limit) * 2);
        for (int i = 0; i < bytes.length && i < limit; i++) {
            int value = bytes[i] & 255;
            if (value < 16) result.append('0');
            result.append(Integer.toHexString(value));
        }
        return result.toString();
    }
}
