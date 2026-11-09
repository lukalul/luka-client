package dev.cbui.mixin;

import dev.cbui.Cbui;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngame.class)
public abstract class GuiIngameMixin {
    @Inject(method="renderGameOverlay", at=@At("HEAD"), require=0)
    private void cbui$overlayBegin(float partialTicks, CallbackInfo ci) { dev.cbui.Profiler.gpuBegin(dev.cbui.Profiler.GpuPhase.HUD); Cbui.guiBegin(); }

    @Inject(method="renderGameOverlay", at=@At("RETURN"), require=0)
    private void cbui$overlay(float partialTicks, CallbackInfo ci) { Cbui.guiEnd(); dev.cbui.Profiler.gpuEnd(dev.cbui.Profiler.GpuPhase.HUD); Cbui.overlay(partialTicks); }

    @Inject(method="renderScoreboard", at=@At("HEAD"), cancellable=true, require=0)
    private void cbui$scoreboard(net.minecraft.scoreboard.ScoreObjective objective, net.minecraft.client.gui.ScaledResolution resolution, CallbackInfo ci) {
        try { if (dev.cbui.hud.module.ScoreboardModule.replacesVanilla()) ci.cancel(); }
        catch (Throwable error) { Cbui.reportOnce("scoreboard hook", error); }
    }
}
