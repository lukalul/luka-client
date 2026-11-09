package dev.cbui.mixin;

import dev.cbui.render.GlintDistance;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs before the other glint hooks (priority 1), so they never start work that a skip would leave unfinished. */
@Mixin(value = LayerArmorBase.class, priority = 1)
public abstract class GlintDistanceArmorMixin {
  @Inject(method = "renderGlint", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$skip(CallbackInfo ci) {
    if (GlintDistance.skip()) ci.cancel();
  }
}
