package dev.envoy.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.envoy.module.icon.IconTextRenderer;
import net.minecraft.client.gui.GuiNewChat;

/**
 * Chat icons are queued while drawChat runs and painted after the line backgrounds and text (see
 * IconTextRenderer). Priority 2000 puts these callbacks after other mods' callbacks at the same instructions, so
 * the icons come after Argentum's batched backgrounds and text.
 */
@Mixin(value = GuiNewChat.class, priority = 2000)
public class MixinGuiNewChat {

    @Inject(method = "drawChat", at = @At("HEAD"), require = 0)
    private void envoy$queueChatIcons(int updateCounter, CallbackInfo ci) {
        IconTextRenderer.beginChat();
    }

    /** Before the scroll bar's translate (it would shift the icons) and before the chat's popMatrix. */
    @Inject(method = "drawChat", at = {
        @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V", ordinal = 1),
        @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;popMatrix()V")
    }, require = 0)
    private void envoy$paintChatIcons(int updateCounter, CallbackInfo ci) {
        IconTextRenderer.paintQueued();
    }

    @Inject(method = "drawChat", at = @At("RETURN"), require = 0)
    private void envoy$endChatIcons(int updateCounter, CallbackInfo ci) {
        IconTextRenderer.endChat();
    }
}
