package dev.fullbright;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Properties;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

/**
 * Fullbright: every block and entity is drawn at full light, with normal colours and no shadows from the light level. The game's
 * light map (a small texture the renderer samples for every light level) is filled with white instead of being computed, so unlike a
 * huge brightness value it does not wash out or tint anything. Switched on and off from the Fullbright card in the luka menu (there is no key for it); the choice is
 * remembered in config/fullbright.properties.
 */
public final class Fullbright implements ClientModInitializer {
    public static final Logger LOG = LogManager.getLogger("Fullbright");

    /** Read every frame by the light map hook. */
    public static volatile boolean enabled = true;

    private static boolean registered;
    private static File file;

    @Override
    public void onInitializeClient() {
        LOG.info("Fullbright initialized");
    }

    /** Called at the end of every client tick. */
    public static void tick(Minecraft mc) {
        try {
            if (mc.gameSettings == null) return;
            if (!registered) register(mc);
        } catch (Throwable t) {
            report("tick", t);
        }
    }

    private static void register(Minecraft mc) {
        registered = true;
        file = new File(new File(mc.mcDataDir, "config"), "fullbright.properties");
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            p.load(in);
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
        } catch (Exception ignored) {
            // first run: stays on
        }
        LightmapRefresh.request = true;
    }

    private static void save(Minecraft mc) {
        try {
            if (file == null) return;
            file.getParentFile().mkdirs();
            Properties p = new Properties();
            p.setProperty("enabled", Boolean.toString(enabled));
            try (FileOutputStream out = new FileOutputStream(file)) {
                p.store(out, "Fullbright (switch: the Fullbright card in the luka menu)");
            }
        } catch (Exception e) {
            report("save", e);
        }
    }

    private static volatile boolean reported;

    public static void report(String where, Throwable t) {
        if (!reported) {
            reported = true;
            LOG.error("Fullbright failed in " + where + "; carrying on", t);
        }
    }

    /** Set when the light map must be rebuilt at once (after a toggle). */
    public static final class LightmapRefresh {
        public static volatile boolean request;
    }
}
