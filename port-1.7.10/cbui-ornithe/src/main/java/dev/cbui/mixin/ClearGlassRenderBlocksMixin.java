package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.init.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Clear glass (Video Settings, Quality): glass is not drawn in the world, frame included. This is the world path only (both
 * renderers mesh blocks through it, the Vulkan one also on its worker threads), so glass items in the hand and the inventory
 * still look like glass. Glass never hides the faces next to it, so nothing else has to change.
 */
@Mixin(RenderBlocks.class)
public abstract class ClearGlassRenderBlocksMixin {
  @Inject(method = "renderBlockByRenderType", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$clearGlass(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
    int mode = GlobalOptions.clearGlass;
    if (mode == 0) return;
    if (block == Blocks.glass || block == Blocks.glass_pane
        || (mode > 1 && (block == Blocks.stained_glass || block == Blocks.stained_glass_pane))) cir.setReturnValue(false);
  }
}
