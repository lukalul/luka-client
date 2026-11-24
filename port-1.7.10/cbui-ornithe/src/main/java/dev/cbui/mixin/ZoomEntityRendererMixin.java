package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.Cbui;
import dev.cbui.feature.zoom.ZoomModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom: divides the finished FOV, slows the mouse, forces the cinematic camera, hides the HUD and calms the bob. */
@Mixin(EntityRenderer.class)
public abstract class ZoomEntityRendererMixin {
  @Inject(method = "getFOVModifier", at = @At("RETURN"), cancellable = true, require = 0)
  private void cbui$zoomFov(float partialTicks, boolean worldPass, CallbackInfoReturnable<Float> cir) {
    ZoomModule zoom = ZoomModule.instance();
    if (zoom == null) return;
    try {
      float fov = cir.getReturnValueF();
      float zoomed = zoom.zoomedFov(fov, partialTicks, worldPass);
      if (zoomed != fov) cir.setReturnValue(zoomed);
    } catch (Throwable error) {
      Cbui.reportOnce("zoom fov", error);
    }
  }

  @ModifyExpressionValue(method = {"updateCameraAndRender", "updateRenderer"},
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;mouseSensitivity:F"), require = 0)
  private float cbui$zoomSensitivity(float vanilla) {
    ZoomModule zoom = ZoomModule.instance();
    if (zoom == null) return vanilla;
    try {
      return zoom.sensitivity(vanilla);
    } catch (Throwable error) {
      Cbui.reportOnce("zoom sensitivity", error);
      return vanilla;
    }
  }

  @ModifyExpressionValue(method = {"updateCameraAndRender", "updateRenderer"},
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;smoothCamera:Z"), require = 0)
  private boolean cbui$zoomSmoothCamera(boolean vanilla) {
    if (vanilla) return true;
    ZoomModule zoom = ZoomModule.instance();
    return zoom != null && zoom.forcesSmoothCamera();
  }

  @ModifyArg(method = "updateRenderer",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MouseFilter;smooth(FF)F"), index = 1, require = 0)
  private float cbui$zoomSmoothing(float vanilla) {
    ZoomModule zoom = ZoomModule.instance();
    return zoom == null ? vanilla : zoom.smoothingStep(vanilla);
  }

  @ModifyExpressionValue(method = "updateCameraAndRender",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;hideGUI:Z"), require = 0)
  private boolean cbui$zoomHideHud(boolean vanilla) {
    if (vanilla) return true;
    ZoomModule zoom = ZoomModule.instance();
    return zoom != null && zoom.hidesHud();
  }

  @WrapOperation(method = "setupCameraTransform",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;setupViewBobbing(F)V"), require = 0)
  private void cbui$zoomWorldBob(EntityRenderer self, float partialTicks, Operation<Void> original) {
    cbui$calmBob(false, self, partialTicks, original);
  }

  @WrapOperation(method = "renderHand",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;setupViewBobbing(F)V"), require = 0)
  private void cbui$zoomHandBob(EntityRenderer self, float partialTicks, Operation<Void> original) {
    cbui$calmBob(true, self, partialTicks, original);
  }

  /**
   * Runs the bob with the player's camera-yaw pair scaled by the zoom's bob factor. The pair is read more than once in
   * setupViewBobbing and field targets only reach the first read, so the fields themselves are scaled for the call.
   */
  @Unique
  private static void cbui$calmBob(boolean hand, EntityRenderer self, float partialTicks, Operation<Void> original) {
    ZoomModule zoom = ZoomModule.instance();
    if (zoom != null) zoom.bobStart(hand);
    EntityPlayer player = null;
    float ownYaw = 0f, ownPrevYaw = 0f;
    try {
      float scale = zoom == null ? 1f : zoom.bobScale();
      Object view = Minecraft.getMinecraft().renderViewEntity;
      if (scale != 1f && view instanceof EntityPlayer) {
        player = (EntityPlayer) view;
        ownYaw = player.cameraYaw;
        ownPrevYaw = player.prevCameraYaw;
        player.cameraYaw = ownYaw * scale;
        player.prevCameraYaw = ownPrevYaw * scale;
      }
      original.call(self, partialTicks);
    } finally {
      if (player != null) {
        player.cameraYaw = ownYaw;
        player.prevCameraYaw = ownPrevYaw;
      }
      if (zoom != null) zoom.bobEnd();
    }
  }
}