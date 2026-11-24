package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.feature.crashguard.CrashRecovery;
import net.minecraft.client.Minecraft;
import net.minecraft.crash.CrashReport;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Crash Patch: each frame of the game loop runs inside a guard that hands an escaping error to the recovery code
 * (which throws it on unchanged when it does not catch it), and a crashed singleplayer server can be recovered
 * before vanilla shows its crash report and exits.
 */
@Mixin(Minecraft.class)
public abstract class CrashGuardMinecraftMixin {
  @Shadow private boolean hasCrashed;
  @Shadow private CrashReport crashReporter;

  @WrapOperation(method = "run", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/Minecraft;runGameLoop()V"), require = 0)
  private void cbui$guardFrame(Minecraft self, Operation<Void> original) {
    try {
      original.call(self);
    } catch (Throwable error) {
      CrashRecovery.frameFailed(self, error);
    }
  }

  @ModifyExpressionValue(method = "run", at = @At(value = "FIELD",
      target = "Lnet/minecraft/client/Minecraft;hasCrashed:Z", opcode = Opcodes.GETFIELD), require = 0)
  private boolean cbui$serverCrash(boolean crashed) {
    if (!crashed || crashReporter == null) return crashed;
    if (CrashRecovery.serverCrashed((Minecraft) (Object) this, crashReporter)) {
      hasCrashed = false;
      crashReporter = null;
      return false;
    }
    return true;
  }
}
