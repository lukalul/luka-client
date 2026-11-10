package dev.hcfopt.mixin;

import dev.hcfopt.HcfOptHooks;
import dev.hcfopt.RenderCounters;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityRendererDispatcher.class)
abstract class TileEntityRendererDispatcherMixin {
    @Inject(method = "renderTileEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void hcfopt$distanceGate(TileEntity tile, float partialTicks, CallbackInfo ci) {
        if (HcfOptHooks.skipTileRender(tile)) {
            ci.cancel();
        } else {
            RenderCounters.tileRendered();
        }
    }
}
