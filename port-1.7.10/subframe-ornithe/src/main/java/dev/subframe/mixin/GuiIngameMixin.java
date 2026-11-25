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
        SubFrameMod.drawRecordedContainer(partialTick);
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

    /** Replay editor "hide the HUD": nothing of the in-game overlay in that render (the hand stays). */
    @Inject(method = "renderGameOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void subframe$hideHud(CallbackInfo ci) {
        if (SubFrameMod.lookHides("hud")) {
            ci.cancel();
        }
    }

    /** Replay editor "hide the scoreboard" (luka's Scoreboard module is switched off for that render too). */
    @Inject(method = "renderScoreboard", at = @At("HEAD"), cancellable = true, require = 0)
    private void subframe$hideScoreboard(CallbackInfo ci) {
        if (SubFrameMod.lookHides("scoreboard")) {
            ci.cancel();
        }
    }
}
