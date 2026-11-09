package dev.envoy.module.combat;

import dev.envoy.Envoy;
import dev.envoy.EnvoySettings;

/** Combines server combat policy with the user's local preferences. */
public final class CombatState {
    private static volatile ServerPolicy serverPolicy = ServerPolicy.NONE;
    private static int reportedValues;

    private CombatState() { }

    public static boolean disableMissPenalty() {
        return serverPolicy.missPenalty || EnvoySettings.disableMissPenalty;
    }

    public static boolean disableBlockMissPenalty() {
        return serverPolicy.blockMissPenalty || EnvoySettings.disableBlockMissPenalty;
    }

    public static boolean allowDigAndUse() {
        return serverPolicy.digAndUse || EnvoySettings.allowDigAndUse;
    }

    static void setServerDisableMissPenalty(boolean enabled) {
        replacePolicy(serverPolicy.withMissPenalty(enabled));
    }

    static void setServerDisableBlockMissPenalty(boolean enabled) {
        replacePolicy(serverPolicy.withBlockMissPenalty(enabled));
    }

    static void setServerAllowDigAndUse(boolean enabled) {
        replacePolicy(serverPolicy.withDigAndUse(enabled));
    }

    public static void clearServer() {
        replacePolicy(ServerPolicy.NONE);
    }

    public static void localSettingsChanged() {
        reportEffectiveChanges();
    }

    private static void replacePolicy(ServerPolicy replacement) {
        serverPolicy = replacement;
        reportEffectiveChanges();
    }

    private static synchronized void reportEffectiveChanges() {
        ServerPolicy policy = serverPolicy;
        int effective = mask(disableMissPenalty(), disableBlockMissPenalty(), allowDigAndUse());
        reportBit(1, "disable-miss-penalty", effective, policy.missPenalty);
        reportBit(2, "disable-block-miss-penalty", effective, policy.blockMissPenalty);
        reportBit(4, "allow-dig-and-use", effective, policy.digAndUse);
        reportedValues = effective;
    }

    private static int mask(boolean miss, boolean block, boolean digAndUse) {
        return (miss ? 1 : 0) | (block ? 2 : 0) | (digAndUse ? 4 : 0);
    }

    private static void reportBit(int bit, String key, int effective, boolean suppliedByServer) {
        if ((reportedValues & bit) == (effective & bit)) return;
        boolean enabled = (effective & bit) != 0;
        String origin = suppliedByServer ? "server" : (enabled ? "local" : "default");
        Envoy.LOGGER.info("[Combat] {}={} ({})", key, enabled, origin);
    }

    private static final class ServerPolicy {
        static final ServerPolicy NONE = new ServerPolicy(false, false, false);

        final boolean missPenalty;
        final boolean blockMissPenalty;
        final boolean digAndUse;

        ServerPolicy(boolean missPenalty, boolean blockMissPenalty, boolean digAndUse) {
            this.missPenalty = missPenalty;
            this.blockMissPenalty = blockMissPenalty;
            this.digAndUse = digAndUse;
        }

        ServerPolicy withMissPenalty(boolean enabled) {
            return new ServerPolicy(enabled, blockMissPenalty, digAndUse);
        }

        ServerPolicy withBlockMissPenalty(boolean enabled) {
            return new ServerPolicy(missPenalty, enabled, digAndUse);
        }

        ServerPolicy withDigAndUse(boolean enabled) {
            return new ServerPolicy(missPenalty, blockMissPenalty, enabled);
        }
    }
}
