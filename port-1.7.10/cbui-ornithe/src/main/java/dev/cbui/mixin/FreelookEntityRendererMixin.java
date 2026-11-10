package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cbui.feature.freelook.FreelookModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Camera setup: the detached rotation is used for the view only, never written to the player or the world. */
@Mixin(EntityRenderer.class)
public abstract class FreelookEntityRendererMixin {
  private float[] cbui$anglesToRestore;
  private Entity cbui$swappedEntity;

  @ModifyExpressionValue(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityLivingBase;rotationYaw:F", opcode = 180), require = 0)
  private float cbui$yawNow(float entityYaw) { return FreelookModule.viewYawOr(entityYaw); }

  @ModifyExpressionValue(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityLivingBase;prevRotationYaw:F", opcode = 180), require = 0)
  private float cbui$yawBefore(float entityYaw) { return FreelookModule.viewYawOr(entityYaw); }

  @ModifyExpressionValue(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityLivingBase;rotationPitch:F", opcode = 180), require = 0)
  private float cbui$pitchNow(float entityPitch) { return FreelookModule.viewPitchOr(entityPitch); }

  @ModifyExpressionValue(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityLivingBase;prevRotationPitch:F", opcode = 180), require = 0)
  private float cbui$pitchBefore(float entityPitch) { return FreelookModule.viewPitchOr(entityPitch); }

  @ModifyArg(
      method = "orientCamera",
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", ordinal = 2, remap = false),
      index = 2,
      require = 0)
  private float cbui$orbitDistance(float distance) {
    try {
      FreelookModule module = FreelookModule.instance();
      return module == null ? distance : module.smoothCameraDistance(distance);
    } catch (Throwable failure) {
      return distance;
    }
  }

  @ModifyExpressionValue(
      method = {"updateCameraAndRender", "updateRenderer"},
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;smoothCamera:Z", opcode = 180),
      require = 0)
  private boolean cbui$forceSmoothing(boolean vanillaSmoothing) {
    try {
      FreelookModule module = FreelookModule.instance();
      return vanillaSmoothing || (module != null && module.cinematic());
    } catch (Throwable failure) {
      return vanillaSmoothing;
    }
  }

  @ModifyArg(
      method = "updateRenderer",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MouseFilter;smooth(FF)F"),
      index = 1,
      require = 0)
  private float cbui$smoothingStrength(float vanillaStrength) {
    try {
      FreelookModule module = FreelookModule.instance();
      return module == null ? vanillaStrength : module.cinematicFactor(vanillaStrength);
    } catch (Throwable failure) {
      return vanillaStrength;
    }
  }

  @Inject(method = "orientCamera", at = @At("HEAD"), require = 0)
  private void cbui$beginCameraSwap(float partialTicks, CallbackInfo ci) {
    try {
      FreelookModule module = FreelookModule.instance();
      if (module == null) return;
      Entity viewEntity = Minecraft.getMinecraft().renderViewEntity;
      cbui$anglesToRestore = module.swapInCameraAngles(viewEntity);
      cbui$swappedEntity = cbui$anglesToRestore == null ? null : viewEntity;
    } catch (Throwable failure) {
      cbui$anglesToRestore = null;
    }
  }

  @Inject(method = "orientCamera", at = @At("RETURN"), require = 0)
  private void cbui$endCameraSwap(float partialTicks, CallbackInfo ci) {
    try {
      if (cbui$anglesToRestore == null) return;
      FreelookModule module = FreelookModule.instance();
      if (module != null) module.swapOutCameraAngles(cbui$swappedEntity, cbui$anglesToRestore);
    } catch (Throwable failure) {
      // nothing left to restore
    } finally {
      cbui$anglesToRestore = null;
      cbui$swappedEntity = null;
    }
  }
}
