package dev.envoy.mixins;

import dev.envoy.Envoy;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {
    @Inject(method = "renderWorldPass", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand(FI)V", shift = At.Shift.BEFORE), require = 0)
    private void envoy$dispatchWorldOverlay(int pass, float frameDelta, long deadline, CallbackInfo callback) {
        switch (pass) {
            case 1:
            case 2:
                Envoy.onRenderWorld(frameDelta);
                break;
            default:
        }
    }
}
