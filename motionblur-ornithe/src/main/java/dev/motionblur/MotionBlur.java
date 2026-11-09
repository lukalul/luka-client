package dev.motionblur;

import java.io.File;
import java.util.Arrays;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

/** Client entrypoint, persistence, and the unbound-by-default toggle key. */
public final class MotionBlur implements ClientModInitializer {
    public static final Logger LOG = LogManager.getLogger("MotionBlur");
    private static int lastKey;
    private static final KeyBinding TOGGLE = new KeyBinding("Toggle Motion Blur", Keyboard.KEY_NONE, "Motion Blur");
    private static File configFile;
    private static boolean keyRegistered;
    private static boolean reported;

    @Override
    public void onInitializeClient() {
        try {
            configFile = FabricLoader.getInstance().getConfigDir().resolve("motionblur.json").toFile();
            MotionBlurSettings.apply(MotionBlurConfig.load(configFile));
            TOGGLE.setKeyCode(MotionBlurSettings.toggleKey);
            LOG.info("MotionBlur initialized ({})", MotionBlurSettings.enabled ? "enabled" : "disabled");
        } catch (Throwable t) {
            report("initialization", t);
        }
    }

    public static void tick(Minecraft mc) {
        try {
            if (mc.gameSettings == null) return;
            if (!keyRegistered) registerKey(mc);
            int code = TOGGLE.getKeyCode();
            if (code != lastKey) {
                // rebound in Options > Controls: that choice is the new setting (it used to be overwritten with the old one every tick)
                lastKey = code;
                if (MotionBlurSettings.toggleKey != code) {
                    MotionBlurSettings.toggleKey = code;
                    save();
                }
            } else if (MotionBlurSettings.toggleKey != lastKey) {
                // changed in this mod's own settings
                lastKey = MotionBlurSettings.toggleKey;
                TOGGLE.setKeyCode(lastKey);
                KeyBinding.resetKeyBindingArrayAndHash();
                mc.gameSettings.saveOptions();
            }
            while (TOGGLE.isPressed()) {
                setEnabled(!MotionBlurSettings.enabled);
                save();
                if (mc.thePlayer != null) {
                    mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(
                        "\u00a77Motion blur: " + (MotionBlurSettings.enabled ? "\u00a7aon" : "\u00a7coff")));
                }
            }
        } catch (Throwable t) {
            report("tick", t);
        }
    }

    public static void setEnabled(boolean enabled) {
        if (MotionBlurSettings.enabled == enabled) return;
        MotionBlurSettings.enabled = enabled;
        try { BlurRenderer.enabledChanged(enabled); }
        catch (Throwable t) { report("toggle", t); }
    }

    public static void settingsChanged() {
        try {
            MotionBlurSettings.level = AlphaMath.clamp(MotionBlurSettings.level, 0, 10);
            MotionBlurSettings.frameSkip = AlphaMath.clamp(MotionBlurSettings.frameSkip, 1, 10);
            save();
        } catch (Throwable t) {
            report("settings", t);
        }
    }

    public static void save() {
        try {
            if (configFile != null) MotionBlurConfig.save(configFile, MotionBlurSettings.snapshot());
        } catch (Throwable t) {
            report("config save", t);
        }
    }

    private static void registerKey(Minecraft mc) {
        KeyBinding[] old = mc.gameSettings.keyBindings;
        KeyBinding[] all = Arrays.copyOf(old, old.length + 1);
        all[old.length] = TOGGLE;
        mc.gameSettings.keyBindings = all;
        TOGGLE.setKeyCode(MotionBlurSettings.toggleKey);
        lastKey = TOGGLE.getKeyCode();
        KeyBinding.resetKeyBindingArrayAndHash();
        keyRegistered = true;
    }

    public static void report(String where, Throwable t) {
        if (!reported) {
            reported = true;
            LOG.error("MotionBlur failed in {}; disabling the effect and carrying on", where, t);
        }
        MotionBlurSettings.enabled = false;
        try { BlurRenderer.release(); } catch (Throwable ignored) { }
    }
}
