package dev.subframe.mixin;

import dev.subframe.SubFrameMod;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiIngame.class)
abstract class GuiIngameMixin {
    @Inject(method = "renderGameOverlay", at = @At("RETURN"))
    private void subframe$indicator(float partialTick, CallbackInfo ci) {
        SubFrameMod.renderIndicator();
    }

    @Inject(method = "showCrosshair", at = @At("RETURN"))
    private void subframe$crosshairProbe(CallbackInfoReturnable<Boolean> cir) {
        SubFrameMod.probeCrosshair(cir.getReturnValue());
    }

    @Inject(method = "showCrosshair", at = @At("HEAD"), cancellable = true)
    private void subframe$crosshair(CallbackInfoReturnable<Boolean> cir) {
        if (SubFrameMod.shouldCancelCrosshair()) {
            cir.setReturnValue(false);
        }
    }
}
