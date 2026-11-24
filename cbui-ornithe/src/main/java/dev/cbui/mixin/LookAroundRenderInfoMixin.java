package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cbui.feature.lookaround.LookAroundModule;
import net.minecraft.client.renderer.ActiveRenderInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Freelook: particle billboards are turned towards the freelook camera. */
@Mixin(ActiveRenderInfo.class)
public abstract class LookAroundRenderInfoMixin {
  @ModifyExpressionValue(method = "updateRenderInfo",
      at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/EntityPlayer;rotationYaw:F"), require = 0)
  private static float cbui$lookAroundBillboardYaw(float vanilla) {
    LookAroundModule look = LookAroundModule.instance();
    return look == null ? vanilla : look.cameraYaw(vanilla);
  }

  @ModifyExpressionValue(method = "updateRenderInfo",
      at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/EntityPlayer;rotationPitch:F"), require = 0)
  private static float cbui$lookAroundBillboardPitch(float vanilla) {
    LookAroundModule look = LookAroundModule.instance();
    return look == null ? vanilla : look.cameraPitch(vanilla);
  }
}
