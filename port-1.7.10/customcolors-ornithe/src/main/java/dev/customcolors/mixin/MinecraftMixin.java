package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "startGame", at = @At("RETURN"))
    private void customcolors$register(CallbackInfo ci) {
        CustomColors.register();
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void customcolors$animate(CallbackInfo ci) {
        dev.customcolors.Animations.tick();
    }
}
