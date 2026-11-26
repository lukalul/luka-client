package dev.subframe.mixin;

import dev.subframe.SubFrameMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Shadow private Timer timer;

    @Inject(method = "runTick", at = @At("HEAD"))
    private void subframe$tickStart(CallbackInfo ci) {
        SubFrameMod.onClientTickStart();
    }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void subframe$tickEnd(CallbackInfo ci) {
        SubFrameMod.onClientTickEnd();
    }

    // the window is hidden for a render as soon as it exists (the loading screen comes seconds later)
    @Inject(method = "createDisplay", at = @At("RETURN"), require = 0)
    private void subframe$displayCreated(CallbackInfo ci) {
        SubFrameMod.onSplashScreen();
    }

    @Inject(method = "drawSplashScreen", at = @At("HEAD"), require = 0)
    private void subframe$splash(CallbackInfo ci) {
        SubFrameMod.onSplashScreen();
    }

    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void subframe$renderStart(CallbackInfo ci) {
        SubFrameMod.onRenderStart();
    }

    @Inject(method = "runGameLoop", at = @At("RETURN"))
    private void subframe$renderEnd(CallbackInfo ci) {
        SubFrameMod.onRenderEnd(timer.renderPartialTicks);
    }

    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;)V", at = @At("HEAD"))
    private void subframe$worldLoaded(WorldClient world, CallbackInfo ci) {
        if (world != null) {
            SubFrameMod.onWorldLoaded(world);
        }
    }
}
