package dev.hcfopt.mixin;

import dev.hcfopt.RenderCounters;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderManager.class)
abstract class RenderManagerMixin {
    @Inject(method = "doRenderEntity", at = @At("RETURN"), require = 0)
    private void hcfopt$countRendered(Entity entity, double x, double y, double z, float yaw, float partialTicks,
                                     boolean hideDebugBox, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) RenderCounters.entityRendered();
    }
}
