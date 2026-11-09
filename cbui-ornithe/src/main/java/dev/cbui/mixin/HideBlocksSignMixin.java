package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.cbui.feature.hideblocks.HideBlocksModule;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Signs and banners already answer -1 because a block entity draws them, so a hidden one answers -2 to be told apart. */
@Mixin(BlockContainer.class)
public abstract class HideBlocksSignMixin {
  @ModifyReturnValue(method = "getRenderType", at = @At("RETURN"), require = 0)
  private int cbui$hide(int type) {
    return type == -1 && HideBlocksModule.hides((Block) (Object) this) ? -2 : type;
  }
}
