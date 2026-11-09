package dev.envoy.mixins;

import dev.envoy.Envoy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Shadow private Timer timer;
    @Inject(method = "runTick", at = @At("RETURN"), require = 0)
    private void envoy$afterClientTick(CallbackInfo callback) {
        Envoy.onClientTickEnd();
    }

    @Inject(method = "runGameLoop", at = @At("HEAD"), require = 0)
    private void envoy$beforeFrame(CallbackInfo callback) {
        float interpolation = this.timer.renderPartialTicks;
        Envoy.onRenderStart(interpolation);
    }

    @Inject(method = "displayGuiScreen", at = @At("HEAD"), require = 0)
    private void envoy$notifyScreenChange(GuiScreen nextScreen, CallbackInfo callback) {
        Envoy.onGuiOpen(nextScreen);
    }
}
