package dev.hcfopt;

/** Small toggle API intended for a future OneConfig/UI adapter and for benchmark ablation. */
public final class HcfOptApi {
    private HcfOptApi() { }

    public static boolean isMasterEnabled() {
        return HcfOptMod.config().masterEnabled;
    }

    public static void setMasterEnabled(boolean enabled) {
        HcfOptMod.config().masterEnabled = enabled;
    }

    public static boolean isEnabled(String key) {
        HcfOptConfig c = HcfOptMod.config();
        if ("entityTicks".equals(key)) return c.entityTickThrottling;
        if ("tileTicks".equals(key)) return c.tileTickOptimization;
        if ("tileRenderDistance".equals(key)) return c.tileRenderDistance;
        if ("itemRenderCap".equals(key)) return c.itemRenderCap;
        if ("particleCaps".equals(key)) return c.particleCaps;
        if ("cloudMeshCache".equals(key)) return c.cloudMeshCache;
        throw new IllegalArgumentException("Unknown HCFOpt toggle: " + key);
    }

    public static void setEnabled(String key, boolean enabled) {
        HcfOptConfig c = HcfOptMod.config();
        if ("entityTicks".equals(key)) c.entityTickThrottling = enabled;
        else if ("tileTicks".equals(key)) c.tileTickOptimization = enabled;
        else if ("tileRenderDistance".equals(key)) c.tileRenderDistance = enabled;
        else if ("itemRenderCap".equals(key)) c.itemRenderCap = enabled;
        else if ("particleCaps".equals(key)) c.particleCaps = enabled;
        else if ("cloudMeshCache".equals(key)) c.cloudMeshCache = enabled;
        else throw new IllegalArgumentException("Unknown HCFOpt toggle: " + key);
    }

    public static void setAllOptimizations(boolean enabled) {
        HcfOptConfig c = HcfOptMod.config();
        c.entityTickThrottling = enabled;
        c.tileTickOptimization = enabled;
        c.tileRenderDistance = enabled;
        c.itemRenderCap = enabled;
        c.particleCaps = enabled;
        c.cloudMeshCache = enabled;
    }
}
