package dev.cbui.mixin;

import dev.cbui.Cbui;
import dev.cbui.feature.lookaround.LookAroundModule;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freelook: while looking around, mouse movement turns the camera and the player keeps its facing. */
@Mixin(Entity.class)
public abstract class LookAroundEntityMixin {
  @Inject(method = "setAngles", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$lookAroundTurn(float yawDelta, float pitchDelta, CallbackInfo ci) {
    LookAroundModule look = LookAroundModule.instance();
    if (look == null) return;
    try {
      if (look.turn((Entity) (Object) this, yawDelta, pitchDelta)) ci.cancel();
    } catch (Throwable error) {
      Cbui.reportOnce("freelook turn", error);
    }
  }
}
