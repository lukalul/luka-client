package dev.hcfopt.mixin;

import dev.hcfopt.HcfOptHooks;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Thins remote movement while consuming interpolation steps exactly once per client tick. */
@Mixin(EntityLivingBase.class)
abstract class EntityLivingBaseMixin {
    @Shadow protected int newPosRotationIncrements;
    @Shadow protected double newPosX;
    @Shadow protected double newPosY;
    @Shadow protected double newPosZ;
    @Shadow protected double newRotationYaw;
    @Shadow protected double newRotationPitch;

    @Inject(method = "onLivingUpdate", at = @At("HEAD"), cancellable = true, require = 0)
    private void hcfopt$thinFarMovement(CallbackInfo ci) {
        EntityLivingBase self = (EntityLivingBase)(Object)this;
        if (!HcfOptHooks.skipMobMovement(self)) return;
        try {
            if (newPosRotationIncrements > 0) {
                double x = self.posX + (newPosX - self.posX) / newPosRotationIncrements;
                double y = self.posY + (newPosY - self.posY) / newPosRotationIncrements;
                double z = self.posZ + (newPosZ - self.posZ) / newPosRotationIncrements;
                double yaw = MathHelper.wrapAngleTo180_double(newRotationYaw - self.rotationYaw);
                self.rotationYaw = (float)(self.rotationYaw + yaw / newPosRotationIncrements);
                self.rotationPitch = (float)(self.rotationPitch + (newRotationPitch - self.rotationPitch) / newPosRotationIncrements);
                newPosRotationIncrements--;
                self.setPosition(x, y, z);
                ((EntityAccessor)(Object)self).hcfopt$setRotation(self.rotationYaw, self.rotationPitch);
            } else {
                self.motionX *= 0.98;
                self.motionY *= 0.98;
                self.motionZ *= 0.98;
            }
            ci.cancel();
        } catch (Throwable ignored) {
            // Fail open: do not cancel if a transformed entity class made the interpolation fields unsafe.
        }
    }
}
