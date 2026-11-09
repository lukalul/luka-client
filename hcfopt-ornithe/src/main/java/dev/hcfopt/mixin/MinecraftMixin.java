package dev.hcfopt.mixin;

import dev.hcfopt.HcfOptHooks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Unique private long hcfopt$tickStart;
    @Unique private long hcfopt$frameStart;

    @Inject(method = "runTick", at = @At("HEAD"), require = 0)
    private void hcfopt$tickStart(CallbackInfo ci) {
        hcfopt$tickStart = System.nanoTime();
        HcfOptHooks.onTickStart();
    }

    @Inject(method = "runTick", at = @At("RETURN"), require = 0)
    private void hcfopt$tickEnd(CallbackInfo ci) {
        HcfOptHooks.onTickEnd(System.nanoTime() - hcfopt$tickStart);
    }

    @Inject(method = "runGameLoop", at = @At("HEAD"), require = 0)
    private void hcfopt$frameStart(CallbackInfo ci) {
        hcfopt$frameStart = System.nanoTime();
        HcfOptHooks.onFrameStart();
    }

    @Inject(method = "runGameLoop", at = @At("RETURN"), require = 0)
    private void hcfopt$frameEnd(CallbackInfo ci) {
        HcfOptHooks.onFrameEnd(System.nanoTime() - hcfopt$frameStart);
    }
}
