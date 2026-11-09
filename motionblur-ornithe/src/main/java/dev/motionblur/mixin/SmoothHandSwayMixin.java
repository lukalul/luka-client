package dev.motionblur.mixin;

import dev.motionblur.MotionBlurSettings;
import dev.motionblur.Smoothing;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Held-item sway when turning: the same between-tick curve as the FOV, applied to the arm pitch/yaw interpolation in
 * rotateWithPlayerRotations (its partial-tick argument is only used for those two interpolations). Adapted from framejob
 * by Z1xus (MIT licence).
 */
@Mixin(ItemRenderer.class)
public abstract class SmoothHandSwayMixin {
    @ModifyVariable(method = "rotateWithPlayerRotations", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private float framejob$smoothHandSway(float partialTicks) {
        return MotionBlurSettings.smoothHandSway ? Smoothing.exponentialTickDelta(partialTicks, 0.5F) : partialTicks;
    }
}
