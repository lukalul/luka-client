package dev.hcfopt;

import net.fabricmc.api.ClientModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Client entrypoint and fail-open boundary for every injected hook. */
public final class HcfOptMod implements ClientModInitializer {
    public static final String MOD_ID = "hcfopt";
    static final Logger LOG = LogManager.getLogger("HCFOpt");
    private static final Set<String> REPORTED = Collections.synchronizedSet(new HashSet<String>());
    private static volatile HcfOptConfig CONFIG = new HcfOptConfig();

    @Override
    public void onInitializeClient() {
        try {
            CONFIG = HcfOptConfig.load();
            BenchmarkController.loadIfPresent();
            LOG.info("HCF Optimizer initialized (masterEnabled={})", CONFIG.masterEnabled);
        } catch (Throwable t) {
            problemOnce("initialize", t);
        }
    }

    public static HcfOptConfig config() {
        return CONFIG;
    }

    public static void problemOnce(String hook, Throwable t) {
        if (REPORTED.add(hook)) LOG.error("HCFOpt hook '{}' failed; vanilla behavior will be used", hook, t);
    }
}
