package dev.envoy.mixins;

import dev.envoy.Envoy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {
    /** {@code renderWorld} draws both anaglyph eyes in one loop: overlays go once per frame, on the final eye. */
    @Inject(method = "renderWorld(FJ)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand(FI)V"), require = 0)
    private void envoy$dispatchWorldOverlay(float frameDelta, long deadline, CallbackInfo callback) {
        if (Minecraft.getMinecraft().gameSettings.anaglyph && EntityRenderer.anaglyphField != 1) return;
        Envoy.onRenderWorld(frameDelta);
    }
}
