package dev.motionblur.mixin;

import dev.motionblur.HandBlur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Hand only" motion blur: the first-person hand is drawn into its own layer (see HandBlur). */
@Mixin(EntityRenderer.class)
public abstract class HandBlurMixin {
    @Inject(method = "renderHand", at = @At("HEAD"), require = 0)
    private void motionblur$handLayer(float partialTicks, int pass, CallbackInfo ci) {
        if (!HandBlur.wanted()) return;
        Minecraft mc = Minecraft.getMinecraft();
        HandBlur.beginHand(mc.displayWidth, mc.displayHeight);
    }

    @Inject(method = "renderHand", at = @At("RETURN"), require = 0)
    private void motionblur$handLayerDone(float partialTicks, int pass, CallbackInfo ci) {
        HandBlur.endHand();
    }
}
