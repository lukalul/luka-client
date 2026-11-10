package dev.motionblur.mixin;

import dev.motionblur.MotionBlurSettings;
import dev.motionblur.Smoothing;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Sprint, speed and bow FOV changes: vanilla moves the value halfway to its target each tick and draws a straight line
 * between ticks; this draws the real curve through the same points. The partial-tick argument of getFOVModifier is only
 * used for that interpolation (and, on 1.7.10, the death animation and the camera-in-water check). Idea and formula from framejob by Z1xus (MIT licence).
 */
@Mixin(EntityRenderer.class)
public abstract class SmoothFovMixin {
    @ModifyVariable(method = "getFOVModifier", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private float framejob$smoothFov(float partialTicks) {
        return MotionBlurSettings.smoothFov ? Smoothing.exponentialTickDelta(partialTicks, 0.5F) : partialTicks;
    }
}
