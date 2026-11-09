package dev.motionblur.mixin;

import dev.motionblur.MotionBlur;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "runTick", at = @At("RETURN"), require = 0)
    private void motionblur$tick(CallbackInfo ci) {
        MotionBlur.tick(Minecraft.getMinecraft());
    }
}
