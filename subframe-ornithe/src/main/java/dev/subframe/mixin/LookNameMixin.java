package dev.subframe.mixin;

import dev.subframe.SubFrameMod;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replay editor "hide nametags": players and named mobs show no name. Priority 500: before luka's own (1100). */
@Mixin(value = RendererLivingEntity.class, priority = 500)
abstract class LookNameMixin {
    @Inject(method = "canRenderName(Lnet/minecraft/entity/EntityLivingBase;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void subframe$hideName(CallbackInfoReturnable<Boolean> cir) {
        if (SubFrameMod.lookHides("nametags")) {
            cir.setReturnValue(false);
        }
    }
}
