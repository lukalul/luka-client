package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cbui.feature.freelook.FreelookModule;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Chunk culling and the global view vector follow the detached camera. */
@Mixin(RenderGlobal.class)
public abstract class FreelookRenderGlobalMixin {
  @ModifyExpressionValue(method = {"setupTerrain", "getViewVector"}, at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationYaw:F", opcode = 180), require = 0)
  private float cbui$yawNow(float entityYaw) { return FreelookModule.viewYawOr(entityYaw); }

  @ModifyExpressionValue(method = "getViewVector", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationYaw:F", opcode = 180), require = 0)
  private float cbui$yawBefore(float entityYaw) { return FreelookModule.viewYawOr(entityYaw); }

  @ModifyExpressionValue(method = {"setupTerrain", "getViewVector"}, at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationPitch:F", opcode = 180), require = 0)
  private float cbui$pitchNow(float entityPitch) { return FreelookModule.viewPitchOr(entityPitch); }

  @ModifyExpressionValue(method = "getViewVector", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationPitch:F", opcode = 180), require = 0)
  private float cbui$pitchBefore(float entityPitch) { return FreelookModule.viewPitchOr(entityPitch); }
}
