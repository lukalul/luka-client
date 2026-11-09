package dev.cbui.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.cbui.feature.hideblocks.HideBlocksModule;
import net.minecraft.block.state.IBlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hide Blocks on Argentum: chests and signs are baked into the terrain there, so the usual render type check never sees them. */
@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.blockentity.BlockEntityBaker", remap = false)
public abstract class HideBlocksBakerMixin {
  @Inject(method = "bake", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$hide(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) IBlockState state) {
    if (state != null && HideBlocksModule.hides(state.getBlock())) cir.setReturnValue(Boolean.TRUE); // nothing to draw, and no dynamic fallback either
  }
}
