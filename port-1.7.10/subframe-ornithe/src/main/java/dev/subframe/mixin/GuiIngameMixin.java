package dev.subframe.mixin;

import dev.subframe.SubFrameMod;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngame.class)
abstract class GuiIngameMixin {
    @Inject(method = "renderGameOverlay", at = @At("RETURN"))
    private void subframe$indicator(float partialTick, boolean hasScreen, int mouseX, int mouseY, CallbackInfo ci) {
        SubFrameMod.renderIndicator();
    }

    /** 1.7.10 has no showCrosshair(): the crosshair is the third drawTexturedModalRect of renderGameOverlay, drawn unconditionally. */
    @Redirect(method = "renderGameOverlay",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiIngame;drawTexturedModalRect(IIIIII)V", ordinal = 2))
    private void subframe$crosshair(GuiIngame gui, int x, int y, int u, int v, int width, int height) {
        boolean cancel = SubFrameMod.shouldCancelCrosshair();
        SubFrameMod.probeCrosshair(!cancel);
        if (!cancel) {
            gui.drawTexturedModalRect(x, y, u, v, width, height);
        }
    }
}
