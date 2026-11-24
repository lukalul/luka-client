package dev.customcolors.mixin;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.color.BlockTints;
import net.minecraft.block.BlockLilyPad;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Placed lily pads in the pack's lilypad colour (the item stays vanilla). */
@Mixin(BlockLilyPad.class)
public abstract class CcLilyPadMixin {
    @Inject(method = "colorMultiplier", at = @At("HEAD"), cancellable = true, require = 0)
    private void customcolors$lilyPad(IBlockAccess world, BlockPos pos, int pass, CallbackInfoReturnable<Integer> cir) {
        try {
            int c = BlockTints.lilyPad();
            if (c >= 0) {
                cir.setReturnValue(c);
            }
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("lily pad colour", t);
        }
    }
}
