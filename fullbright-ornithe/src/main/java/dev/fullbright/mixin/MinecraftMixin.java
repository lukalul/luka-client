package dev.fullbright.mixin;

import dev.fullbright.Fullbright;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "runTick", at = @At("RETURN"), require = 0)
    private void fullbright$tick(CallbackInfo ci) {
        Fullbright.tick(Minecraft.getMinecraft());
    }
}
