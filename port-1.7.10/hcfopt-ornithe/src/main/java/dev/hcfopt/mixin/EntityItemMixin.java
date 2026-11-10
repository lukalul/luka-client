package dev.hcfopt.mixin;

import dev.hcfopt.HcfOptHooks;
import net.minecraft.entity.item.EntityItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityItem.class)
abstract class EntityItemMixin {
    @Inject(method = "onUpdate", at = @At("HEAD"), cancellable = true, require = 0)
    private void hcfopt$thinFarTick(CallbackInfo ci) {
        if (HcfOptHooks.skipItemTick((EntityItem)(Object)this)) ci.cancel();
    }
}
