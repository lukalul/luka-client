package dev.hcfopt.mixin;

import dev.hcfopt.HcfOptHooks;
import net.minecraft.client.renderer.entity.RenderEntityItem;
import net.minecraft.entity.item.EntityItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderEntityItem.class)
abstract class RenderEntityItemMixin {
    @Inject(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void hcfopt$itemAdmission(EntityItem item, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
        if (!HcfOptHooks.allowItemRender(item)) ci.cancel();
    }
}
