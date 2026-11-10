package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cbui.feature.freelook.FreelookModule;
import net.minecraft.client.renderer.ActiveRenderInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Particles and billboards read the player's rotation here; give them the detached camera's instead. */
@Mixin(ActiveRenderInfo.class)
public abstract class FreelookActiveRenderInfoMixin {
  @ModifyExpressionValue(method = "updateRenderInfo", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/EntityPlayer;rotationYaw:F", opcode = 180), require = 0)
  private static float cbui$billboardYaw(float playerYaw) {
    return FreelookModule.viewYawOr(playerYaw);
  }

  @ModifyExpressionValue(method = "updateRenderInfo", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/EntityPlayer;rotationPitch:F", opcode = 180), require = 0)
  private static float cbui$billboardPitch(float playerPitch) {
    return FreelookModule.viewPitchOr(playerPitch);
  }
}
