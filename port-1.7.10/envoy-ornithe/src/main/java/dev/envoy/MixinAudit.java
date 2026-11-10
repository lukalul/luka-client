package dev.envoy;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Startup diagnostic for optional, fail-open mixin hooks. */
public final class MixinAudit {
    private static final String HANDLER_TOKEN = "envoy$";
    private static final String[] TARGETS = {
        "net.minecraft.network.NetworkManager",
        "net.minecraft.entity.Entity",
        "net.minecraft.client.renderer.RenderGlobal",
        "net.minecraft.client.renderer.entity.RendererLivingEntity",
        "net.minecraft.client.renderer.entity.RenderItem",
        "net.minecraft.client.renderer.entity.Render",
        "net.minecraft.client.renderer.ItemRenderer",
        "net.minecraft.client.renderer.entity.RenderPlayer",
        "net.minecraft.client.renderer.entity.RenderBiped",
        "net.minecraft.client.gui.inventory.GuiContainer",
        "net.minecraft.client.gui.GuiScreen",
        "net.minecraft.client.gui.FontRenderer",
        "net.minecraft.network.play.server.S3EPacketTeams"
    };

    private MixinAudit() { }

    public static void report() {
        ClassLoader loader = MixinAudit.class.getClassLoader();
        for (String target : TARGETS) audit(target, loader);
    }

    private static void audit(String target, ClassLoader loader) {
        try {
            Class<?> type = Class.forName(target, false, loader);
            List<String> applied = handlerNames(type.getDeclaredMethods());
            Envoy.LOGGER.info("[Mixin] {} -> {}", type.getSimpleName(),
                applied.isEmpty() ? "NO HANDLER APPLIED" : applied);
        } catch (Throwable failure) {
            Envoy.LOGGER.warn("[Mixin] {} -> not loadable: {}", target, failure.toString());
        }
    }

    private static List<String> handlerNames(Method[] methods) {
        List<String> result = new ArrayList<String>();
        for (Method method : methods) {
            String name = method.getName();
            int marker = name.indexOf(HANDLER_TOKEN);
            if (marker >= 0) result.add(name.substring(marker + HANDLER_TOKEN.length()));
        }
        return result;
    }
}
