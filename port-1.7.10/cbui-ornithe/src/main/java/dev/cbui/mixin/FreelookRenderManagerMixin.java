package dev.cbui.mixin;

import dev.cbui.feature.freelook.FreelookModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nametags and other billboards face these two angles, so while freelooking they have to be the camera's and not the player's. */
@Mixin(RenderManager.class)
public abstract class FreelookRenderManagerMixin {
  @Shadow public float playerViewY;
  @Shadow public float playerViewX;

  @Inject(method = "cacheActiveRenderInfo", at = @At("RETURN"), require = 0)
  private void cbui$faceTheCamera(CallbackInfo ci) {
    float yaw = FreelookModule.viewYawOr(Float.NaN);
    if (Float.isNaN(yaw)) return;
    this.playerViewY = yaw + (Minecraft.getMinecraft().gameSettings.thirdPersonView == 2 ? 180F : 0F);
    this.playerViewX = FreelookModule.viewPitchOr(this.playerViewX);
  }
}
