package dev.hcfopt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/** JSON configuration. Public fields intentionally make future UI binding trivial. */
public final class HcfOptConfig {
    public boolean masterEnabled = true;

    public boolean entityTickThrottling = true;
    public double itemTickNearDistance = 32.0;
    public double orbTickNearDistance = 32.0;
    public double mobTickNearDistance = 64.0;
    public double attackSafetyDistance = 6.0;

    public boolean tileTickOptimization = true;
    public double chestTickFullDistance = 48.0;
    public int distantChestTickInterval = 4;

    public boolean tileRenderDistance = true;
    public double signRenderDistance = 24.0;
    public double chestRenderDistance = 48.0;
    public double skullRenderDistance = 48.0;
    public double bannerRenderDistance = 48.0;

    public boolean itemRenderCap = true;
    public int maxRenderedItems = 256;
    public double itemRenderNearExemptionDistance = 16.0;

    public boolean particleCaps = true;
    public int maxFarParticles = 8000;
    public int maxFarParticleSpawnsPerTick = 512;
    public double particleNearExemptionDistance = 32.0;
    public double particleMaxDistance = 96.0;

    /** Fancy clouds drawn from a cached mesh instead of being tessellated every frame. */
    public boolean cloudMeshCache = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static HcfOptConfig load() {
        File file = FabricLoader.getInstance().getConfigDir().resolve("hcfopt.json").toFile();
        HcfOptConfig config = new HcfOptConfig();
        if (file.isFile()) {
            try (FileReader reader = new FileReader(file)) {
                HcfOptConfig loaded = GSON.fromJson(reader, HcfOptConfig.class);
                if (loaded != null) config = loaded;
            } catch (Throwable t) {
                HcfOptMod.problemOnce("config-read", t);
            }
        }
        config.sanitize();
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(config, writer);
            }
        } catch (Throwable t) {
            HcfOptMod.problemOnce("config-write", t);
        }
        return config;
    }

    void sanitize() {
        itemTickNearDistance = distance(itemTickNearDistance, 32.0);
        orbTickNearDistance = distance(orbTickNearDistance, 32.0);
        mobTickNearDistance = distance(mobTickNearDistance, 64.0);
        attackSafetyDistance = distance(attackSafetyDistance, 6.0);
        chestTickFullDistance = distance(chestTickFullDistance, 48.0);
        distantChestTickInterval = clamp(distantChestTickInterval, 1, 20);
        signRenderDistance = distance(signRenderDistance, 24.0);
        chestRenderDistance = distance(chestRenderDistance, 48.0);
        skullRenderDistance = distance(skullRenderDistance, 48.0);
        bannerRenderDistance = distance(bannerRenderDistance, 48.0);
        maxRenderedItems = clamp(maxRenderedItems, 0, 100000);
        itemRenderNearExemptionDistance = distance(itemRenderNearExemptionDistance, 16.0);
        maxFarParticles = clamp(maxFarParticles, 0, 32000);
        maxFarParticleSpawnsPerTick = clamp(maxFarParticleSpawnsPerTick, 0, 32000);
        particleNearExemptionDistance = distance(particleNearExemptionDistance, 32.0);
        particleMaxDistance = Math.max(particleNearExemptionDistance, distance(particleMaxDistance, 96.0));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double distance(double value, double fallback) {
        return Double.isNaN(value) || Double.isInfinite(value) || value < 0.0 ? fallback : Math.min(value, 4096.0);
    }
}
