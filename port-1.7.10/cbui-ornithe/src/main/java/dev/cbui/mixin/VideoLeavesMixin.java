package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.block.BlockLeaves;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The Leaves setting: 0 follows Graphics, 1 is always fast (solid), 2 is always fancy (see-through). */
@Mixin(BlockLeaves.class)
public abstract class VideoLeavesMixin {
  @ModifyVariable(method = "setGraphicsLevel", at = @At("HEAD"), argsOnly = true, require = 0)
  private boolean cbui$leaves(boolean fancy) {
    int mode = GlobalOptions.leavesMode;
    return mode == 0 ? fancy : mode == 2;
  }
}
