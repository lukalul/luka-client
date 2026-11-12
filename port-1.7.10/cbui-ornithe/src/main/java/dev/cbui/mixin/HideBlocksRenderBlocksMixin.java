package dev.cbui.mixin;

import dev.cbui.feature.hideblocks.HideBlocksModule;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hide foliage and Hide Blocks in the world. Most of the blocks they hide (grass, flowers, mushrooms, vines, crops, torches,
 * redstone, rails, lily pads) answer getRenderType themselves in 1.7.10, so the render type of -1 set on Block never reached
 * them. Every block in the world is meshed through this method (both renderers, the Vulkan one also on its worker threads),
 * while items in the hand and the inventory are not, so they still look as before.
 */
@Mixin(RenderBlocks.class)
public abstract class HideBlocksRenderBlocksMixin {
  @Inject(method = "renderBlockByRenderType", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$hide(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
    if (HideBlocksModule.hides(block)) cir.setReturnValue(false);
  }
}
