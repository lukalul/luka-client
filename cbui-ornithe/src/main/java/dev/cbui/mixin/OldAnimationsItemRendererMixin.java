package dev.cbui.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.cbui.feature.oldanimations.OldAnimationsHooks;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Old Animations: block hitting (the first person hand keeps swinging while an item is in use). */
@Mixin(ItemRenderer.class)
public abstract class OldAnimationsItemRendererMixin {
  @ModifyArg(method = "renderItemInFirstPerson",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;transformFirstPersonItem(FF)V"), index = 1, require = 0)
  private float cbui$oldBlockHitSwing(float swing, @Local(argsOnly = true) float partialTicks) {
    try { return OldAnimationsHooks.firstPersonSwing(swing, partialTicks); } catch (Throwable ignored) { return swing; }
  }
}
