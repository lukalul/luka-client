package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cbui.feature.oldanimations.OldAnimationsHooks;
import net.minecraft.client.renderer.entity.layers.LayerCustomHead;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Old Animations: no 1.8 sneak shift of worn heads (smooth sneaking). */
@Mixin(LayerCustomHead.class)
public abstract class OldAnimationsLayerCustomHeadMixin {
  @ModifyExpressionValue(method = "doRenderLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;isSneaking()Z"), require = 0)
  private boolean cbui$oldSneakPose(boolean original) {
    try { return OldAnimationsHooks.sneakPose(original); } catch (Throwable ignored) { return original; }
  }
}
