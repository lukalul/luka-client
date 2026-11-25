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
        SubFrameMod.drawRecordedContainer(partialTick);
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
