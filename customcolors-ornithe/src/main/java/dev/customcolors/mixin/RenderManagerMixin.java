package dev.customcolors.mixin;

import dev.customcolors.RandomMobs;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderManager.class)
public abstract class RenderManagerMixin {
    @Inject(method = "doRenderEntity", at = @At("HEAD"))
    private void customcolors$begin(Entity entity, double x, double y, double z, float yaw, float partialTicks, boolean hideBox, CallbackInfoReturnable<Boolean> info) {
        RandomMobs.begin(entity);
    }

    @Inject(method = "doRenderEntity", at = @At("RETURN"))
    private void customcolors$end(Entity entity, double x, double y, double z, float yaw, float partialTicks, boolean hideBox, CallbackInfoReturnable<Boolean> info) {
        RandomMobs.end();
    }
}
