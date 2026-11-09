package dev.hcfopt.mixin;

import dev.hcfopt.HcfOptHooks;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(EffectRenderer.class)
abstract class EffectRendererMixin {
    @Shadow private List<EntityFX>[][] fxLayers;

    @Inject(method = "addEffect", at = @At("HEAD"), cancellable = true, require = 0)
    private void hcfopt$particleAdmission(EntityFX particle, CallbackInfo ci) {
        if (!HcfOptHooks.allowParticle(particle, fxLayers)) ci.cancel();
    }
}
