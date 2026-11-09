package dev.envoy.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.envoy.render.CustomModelRenderer;
import net.minecraft.entity.Entity;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Inject(method = "isInRangeToRender3d", at = @At("HEAD"), cancellable = true, require = 0)
    private void envoy$replaceDistanceCheck(double cameraX, double cameraY, double cameraZ, CallbackInfoReturnable<Boolean> result) {
        Entity self = (Entity) (Object) this;
        float configuredRange = CustomModelRenderer.renderRange(self);
        if (configuredRange <= 0.0F) return;
        result.setReturnValue(withinSphere(self, cameraX, cameraY, cameraZ, configuredRange));
    }

    private static boolean withinSphere(Entity entity, double x, double y, double z, float radius) {
        double xOffset = entity.posX - x;
        double yOffset = entity.posY - y;
        double zOffset = entity.posZ - z;
        return xOffset * xOffset + yOffset * yOffset + zOffset * zOffset < (double) radius * (double) radius;
    }
}
