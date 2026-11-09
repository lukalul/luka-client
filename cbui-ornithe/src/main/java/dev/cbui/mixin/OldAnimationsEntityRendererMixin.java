package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.cbui.feature.oldanimations.OldAnimationsHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Old Animations: smooth sneaking (the camera height eases between standing and sneaking). */
@Mixin(EntityRenderer.class)
public abstract class OldAnimationsEntityRendererMixin {
  @Inject(method = "updateRenderer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;updateEquippedItem()V"), require = 0)
  private void cbui$oldEyeTick(CallbackInfo ci) {
    try { OldAnimationsHooks.tickEyeHeight(Minecraft.getMinecraft()); } catch (Throwable ignored) { }
  }

  @ModifyExpressionValue(method = {"orientCamera", "renderWorldDirections"},
      at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getEyeHeight()F"), require = 0)
  private float cbui$oldEyeHeight(float original, @Local(argsOnly = true) float partialTicks) {
    try { return OldAnimationsHooks.smoothEyeHeight(original, partialTicks); } catch (Throwable ignored) { return original; }
  }
}
