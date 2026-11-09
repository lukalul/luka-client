package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cbui.feature.freelook.FreelookModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Argentum's terrain renderer builds its own camera for chunk culling and drawing from the player's yaw and pitch, which the vanilla
 * camera hooks never touch. Without this, the world would stay put while entities and name tags followed the detached camera.
 */
@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer", remap = false)
public abstract class FreelookArgentumCameraMixin {
  @ModifyExpressionValue(method = "captureCameraState",
      at = @At(value = "FIELD", target = "Lnet/minecraft/unmapped/C_42232651;f_80130373:F", opcode = 180), require = 0)
  private static float cbui$terrainYaw(float playerYaw) {
    return FreelookModule.viewYawOr(playerYaw);
  }

  @ModifyExpressionValue(method = "captureCameraState",
      at = @At(value = "FIELD", target = "Lnet/minecraft/unmapped/C_42232651;f_03549461:F", opcode = 180), require = 0)
  private static float cbui$terrainPitch(float playerPitch) {
    return FreelookModule.viewPitchOr(playerPitch);
  }
}
