package dev.cbui.mixin;

import dev.cbui.feature.freelook.FreelookModule;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes the mouse movement to the detached camera instead of turning the local player. */
@Mixin(Entity.class)
public abstract class FreelookEntityMixin {
  @Inject(method = "setAngles", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$steerCameraInstead(float yawDelta, float pitchDelta, CallbackInfo ci) {
    try {
      if ((Object) this != Minecraft.getMinecraft().thePlayer) return;
      FreelookModule module = FreelookModule.instance();
      if (module != null && module.rotateCamera(yawDelta, pitchDelta)) ci.cancel();
    } catch (Throwable failure) {
      // let vanilla turn the player
    }
  }
}
