package dev.customcolors.mixin;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.color.BlockTints;
import net.minecraft.block.BlockOldLeaf;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Spruce and birch leaf colours from the pack's pine/birch colormaps. */
@Mixin(BlockOldLeaf.class)
public abstract class CcOldLeafMixin {
    @Inject(method = "colorMultiplier", at = @At("HEAD"), cancellable = true, require = 0)
    private void customcolors$leaves(IBlockAccess world, int x, int y, int z, CallbackInfoReturnable<Integer> cir) {
        try {
            int c = BlockTints.leaves(world, x, y, z);
            if (c >= 0) {
                cir.setReturnValue(c);
            }
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("leaf colour", t);
        }
    }
}
