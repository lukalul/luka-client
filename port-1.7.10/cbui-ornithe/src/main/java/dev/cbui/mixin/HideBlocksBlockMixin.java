package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.cbui.feature.hideblocks.HideBlocksModule;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A render type of -1 is how the game itself marks a block that draws nothing; collision and everything else stay as they are. */
@Mixin(Block.class)
public abstract class HideBlocksBlockMixin {
  @ModifyReturnValue(method = "getRenderType", at = @At("RETURN"), require = 0)
  private int cbui$hide(int type) {
    return HideBlocksModule.hides((Block) (Object) this) ? -1 : type;
  }

  /** With a hidden full block next to it, this block has to draw the face that would normally be covered. */
  @ModifyReturnValue(method = "shouldSideBeRendered", at = @At("RETURN"), require = 0)
  private boolean cbui$revealSide(boolean render, IBlockAccess world, int x, int y, int z, int side) {
    return render || (HideBlocksModule.anySolid() && HideBlocksModule.hides(world.getBlock(x, y, z)));
  }
}
