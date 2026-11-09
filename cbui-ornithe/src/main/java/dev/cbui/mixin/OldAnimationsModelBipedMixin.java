package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cbui.feature.oldanimations.OldAnimationsHooks;
import net.minecraft.client.model.ModelBiped;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Old Animations: no 1.8 sneak shift of the body (smooth sneaking). */
@Mixin(ModelBiped.class)
public abstract class OldAnimationsModelBipedMixin {
  @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isSneaking()Z"), require = 0)
  private boolean cbui$oldSneakPose(boolean original) {
    try { return OldAnimationsHooks.sneakPose(original); } catch (Throwable ignored) { return original; }
  }
}
