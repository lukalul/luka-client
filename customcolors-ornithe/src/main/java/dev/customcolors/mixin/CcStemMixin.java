package dev.customcolors.mixin;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.color.BlockTints;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStem;
import net.minecraft.block.state.IBlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pumpkin and melon stem colour per growth stage from the pack's stem colormaps. */
@Mixin(BlockStem.class)
public abstract class CcStemMixin {
    @Shadow @Final private Block crop;

    @Inject(method = "getRenderColor", at = @At("HEAD"), cancellable = true, require = 0)
    private void customcolors$stem(IBlockState state, CallbackInfoReturnable<Integer> cir) {
        try {
            int c = BlockTints.stem(state, crop);
            if (c >= 0) {
                cir.setReturnValue(c);
            }
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("stem colour", t);
        }
    }
}
