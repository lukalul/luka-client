package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cbui.feature.freelook.FreelookModule;
import net.minecraft.client.renderer.entity.RenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Name tags and other billboards turn to face the detached camera rather than the player's body direction. */
@Mixin(RenderManager.class)
public abstract class FreelookRenderManagerMixin {
  @ModifyExpressionValue(method = "cacheActiveRenderInfo", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationYaw:F", opcode = 180), require = 0)
  private float cbui$yawNow(float entityYaw) { return FreelookModule.viewYawOr(entityYaw); }

  @ModifyExpressionValue(method = "cacheActiveRenderInfo", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationYaw:F", opcode = 180), require = 0)
  private float cbui$yawBefore(float entityYaw) { return FreelookModule.viewYawOr(entityYaw); }

  @ModifyExpressionValue(method = "cacheActiveRenderInfo", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationPitch:F", opcode = 180), require = 0)
  private float cbui$pitchNow(float entityPitch) { return FreelookModule.viewPitchOr(entityPitch); }

  @ModifyExpressionValue(method = "cacheActiveRenderInfo", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationPitch:F", opcode = 180), require = 0)
  private float cbui$pitchBefore(float entityPitch) { return FreelookModule.viewPitchOr(entityPitch); }
}
