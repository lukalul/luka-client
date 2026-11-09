package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.feature.crashpatch.CrashPatchHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.crash.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class CrashPatchMinecraftMixin {
  @Shadow private boolean hasCrashed;
  @Shadow private CrashReport crashReporter;

  @WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runGameLoop()V"), require = 0)
  private void cbui$guardFrame(Minecraft game, Operation<Void> invocation) {
    Throwable failure = null;
    try { invocation.call(game); } catch (Throwable caught) { failure = caught; }
    if (failure == null) return;
    boolean resumed = false;
    try { resumed = CrashPatchHooks.handleClientFailure(game, failure); } catch (Throwable ignored) { }
    if (!resumed) CrashPatchHooks.propagate(failure);
  }

  @WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;displayCrashReport(Lnet/minecraft/crash/CrashReport;)V"), require = 0)
  private void cbui$interceptServerReport(Minecraft game, CrashReport report, Operation<Void> invocation) {
    boolean recovered = false;
    if (hasCrashed && crashReporter == report)
      try { recovered = CrashPatchHooks.handleIntegratedServerFailure(game, report); } catch (Throwable ignored) { }
    if (recovered) { hasCrashed = false; crashReporter = null; }
    else invocation.call(game, report);
  }
}
