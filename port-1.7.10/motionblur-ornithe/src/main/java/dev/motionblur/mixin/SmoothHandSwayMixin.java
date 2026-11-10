package dev.motionblur.mixin;

import dev.motionblur.MotionBlurSettings;
import dev.motionblur.Smoothing;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Held-item sway when turning: the same between-tick curve as the FOV, applied to the arm pitch/yaw interpolation. On 1.7.10 that
 * interpolation sits inside renderItemInFirstPerson, whose partial-tick argument is used for much more than the sway, so the two
 * "current arm angle" reads are adjusted instead: {@code prev + (read - prev) * t} then lands on the curve. Adapted from framejob
 * by Z1xus (MIT licence).
 */
@Mixin(ItemRenderer.class)
public abstract class SmoothHandSwayMixin {
    @Redirect(method = "renderItemInFirstPerson", at = @At(value = "FIELD", target = "Lnet/minecraft/client/entity/EntityPlayerSP;renderArmPitch:F", opcode = 180), require = 0)
    private float framejob$smoothArmPitch(EntityPlayerSP player, float tickDelta) {
        return framejob$curved(player.prevRenderArmPitch, player.renderArmPitch, tickDelta);
    }

    @Redirect(method = "renderItemInFirstPerson", at = @At(value = "FIELD", target = "Lnet/minecraft/client/entity/EntityPlayerSP;renderArmYaw:F", opcode = 180), require = 0)
    private float framejob$smoothArmYaw(EntityPlayerSP player, float tickDelta) {
        return framejob$curved(player.prevRenderArmYaw, player.renderArmYaw, tickDelta);
    }

    private static float framejob$curved(float previous, float current, float tickDelta) {
        if (!MotionBlurSettings.smoothHandSway || tickDelta <= 0.0F) return current;
        return previous + (current - previous) * (Smoothing.exponentialTickDelta(tickDelta, 0.5F) / tickDelta);
    }
}
