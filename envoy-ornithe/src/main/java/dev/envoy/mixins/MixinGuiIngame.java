package dev.envoy.mixins;

import dev.envoy.Envoy;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngame.class)
public abstract class MixinGuiIngame {
    @Inject(method = "renderGameOverlay", at = @At("RETURN"), require = 0)
    private void envoy$finishHud(float frameDelta, CallbackInfo callback) {
        Envoy.onOverlay();
    }

    @Inject(method = "renderBossHealth", at = @At("HEAD"), cancellable = true, require = 0)
    private void envoy$chooseBossBar(CallbackInfo callback) {
        boolean handled = Envoy.onBossOverlay();
        if (handled) callback.cancel();
    }
}
