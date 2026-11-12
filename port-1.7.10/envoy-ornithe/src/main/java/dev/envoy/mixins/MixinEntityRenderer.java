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
    /**
     * {@code renderWorld} draws both anaglyph eyes in one loop: overlays go once per frame, on the final eye. At the start of
     * the "hand" section, before the game clears the depth buffer for the hand: drawn after that clear, nothing in the
     * world (server name tags included) could be hidden by blocks.
     */
    @Inject(method = "renderWorld(FJ)V", at = @At(value = "INVOKE_STRING",
        target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", args = "ldc=hand", shift = At.Shift.AFTER), require = 0)
    private void envoy$dispatchWorldOverlay(float frameDelta, long deadline, CallbackInfo callback) {
        if (Minecraft.getMinecraft().gameSettings.anaglyph && EntityRenderer.anaglyphField != 1) return;
        Envoy.onRenderWorld(frameDelta);
    }
}
