package dev.hcfopt.mixin;

import dev.hcfopt.HcfOptHooks;
import net.minecraft.entity.item.EntityXPOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityXPOrb.class)
abstract class EntityXPOrbMixin {
    @Inject(method = "onUpdate", at = @At("HEAD"), cancellable = true, require = 0)
    private void hcfopt$thinFarTick(CallbackInfo ci) {
        if (HcfOptHooks.skipOrbTick((EntityXPOrb)(Object)this)) ci.cancel();
    }
}
