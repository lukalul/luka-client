package dev.fastpacks;

import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class FastPacks implements ClientModInitializer {
    public static final Logger LOG = LogManager.getLogger("FastPacks");
    private static final AtomicBoolean REPORTED = new AtomicBoolean();
    private static final AtomicBoolean VANILLA_ONCE = new AtomicBoolean();

    @Override public void onInitializeClient() { LOG.info("FastPacks initialized"); }

    public static void reportOnce(String where, Throwable error) {
        if (REPORTED.compareAndSet(false, true)) LOG.error("FastPacks failed in {}; falling back safely", where, error);
    }

    public static boolean consumeVanillaFallback() { return VANILLA_ONCE.compareAndSet(true, false); }
    public static void openVanilla(Minecraft mc, GuiScreen parent, String where, Throwable error) {
        reportOnce(where, error); VANILLA_ONCE.set(true); mc.displayGuiScreen(new GuiScreenResourcePacks(parent));
    }
}
