package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.Cbui;
import java.util.IdentityHashMap;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Per-frame chat cost: drawChat calls getFormattedText() for every visible line every frame, which
 * deep-copies and re-iterates the component tree. Trimmed chat line components are created once per
 * line and never mutated, so the result is cached by instance identity. A cheap fingerprint
 * (sibling count + style identity) guards against in-place edits.
 */
@Mixin(GuiNewChat.class)
public abstract class GuiNewChatMixin {
    private static final int CBUI_CAP = 512;
    private static final IdentityHashMap<IChatComponent, Object[]> CBUI_CACHE = new IdentityHashMap<IChatComponent, Object[]>();
    private static boolean cbui$failed;

    @WrapOperation(method = "drawChat",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/IChatComponent;getFormattedText()Ljava/lang/String;"),
            require = 0)
    private String cbui$cachedFormattedText(IChatComponent component, Operation<String> original) {
        if (cbui$failed || component == null) return original.call(component);
        try {
            int siblings = component.getSiblings().size();
            Object style = component.getChatStyle();
            Object[] hit = CBUI_CACHE.get(component);
            if (hit != null && ((Integer) hit[0]).intValue() == siblings && hit[1] == style) return (String) hit[2];
            String text = original.call(component);
            if (CBUI_CACHE.size() >= CBUI_CAP) CBUI_CACHE.clear();
            CBUI_CACHE.put(component, new Object[] { Integer.valueOf(siblings), style, text });
            return text;
        } catch (Throwable error) {
            cbui$failed = true;
            Cbui.reportOnce("chat formatted-text cache", error);
            return original.call(component);
        }
    }
}
