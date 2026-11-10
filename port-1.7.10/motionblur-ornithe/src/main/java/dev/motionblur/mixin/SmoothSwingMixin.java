package dev.motionblur.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.motionblur.MotionBlurSettings;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smooths arm swings between ticks: the start of every swing (and every restart) is eased in and the part of a swing
 * vanilla crams into one tick when you click again is spread over the next few ticks. Adapted from framejob by Z1xus
 * (MIT licence, see META-INF/LICENSE.framejob), ported to the MCP names used here.
 */
@Mixin(EntityLivingBase.class)
public abstract class SmoothSwingMixin {
    @Shadow
    public float prevSwingProgress;
    @Shadow
    public float swingProgress;

    @Unique
    private static final float SMOOTHING_TICKS = 0.5F;
    @Unique
    private static final float EASED_START = 0.25F;

    @Unique
    private float framejob$lag;
    @Unique
    private float framejob$speed;
    @Unique
    private float framejob$normalStep;

    @Inject(method = "onUpdate", at = @At("RETURN"), require = 0)
    private void framejob$advance(CallbackInfo ci) {
        this.framejob$lag = this.framejob$lagAt(1);
        float step = this.swingProgress - this.prevSwingProgress;
        if (Float.isNaN(step)) step = 0;
        if (step < 0) step++;
        if (this.swingProgress > 0 && step > 0) this.framejob$normalStep = step;
        this.framejob$speed = Math.max(0, step - this.framejob$normalStep);
    }

    @ModifyReturnValue(method = "getSwingProgress", at = @At("RETURN"), require = 0)
    private float framejob$smoothSwing(float progress, float tickDelta) {
        if (!MotionBlurSettings.smoothSwings) return progress;
        float shown = progress + this.framejob$lagAt(tickDelta);
        return framejob$easeStart(shown - (float) Math.floor(shown));
    }

    @Unique
    private static float framejob$easeStart(float progress) {
        if (progress >= EASED_START) return progress;
        float t = progress / EASED_START;
        float root = (float) Math.sqrt(EASED_START) * t * t * (2.5F - 1.5F * t);
        return root * root;
    }

    @Unique
    private float framejob$lagAt(float tickDelta) {
        float trail = this.framejob$speed * SMOOTHING_TICKS;
        return (this.framejob$lag + trail) * (float) Math.exp(-tickDelta / SMOOTHING_TICKS) - trail;
    }
}
