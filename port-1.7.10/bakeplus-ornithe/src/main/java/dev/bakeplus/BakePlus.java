package dev.bakeplus;

import net.fabricmc.api.ClientModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BakePlus implements ClientModInitializer {
    public static final Logger LOG = LogManager.getLogger("BakePlus");
    private static final ConcurrentMap<String, Boolean> REPORTED_FAILURES = new ConcurrentHashMap<>();

    @Override
    public void onInitializeClient() {
        LOG.info("BakePlus initialized");
    }

    public static void report(String renderer, Throwable failure) {
        if (REPORTED_FAILURES.putIfAbsent(renderer, Boolean.TRUE) == null) {
            LOG.error("BakePlus {} replacement failed; using vanilla rendering", renderer, failure);
        }
    }
}
