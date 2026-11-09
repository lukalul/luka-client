package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStem;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockStem.class)
public abstract class BlockStemMixin {
    @Shadow @Final private Block crop;

    @Inject(method = "getRenderColor", at = @At("HEAD"), cancellable = true)
    private void customcolors$color(IBlockState state, CallbackInfoReturnable<Integer> cir) {
        if (state.getBlock() != (Object) this) return;
        int color = CustomColors.stem(crop == Blocks.pumpkin, state.getValue(BlockStem.AGE));
        if (color != -1) cir.setReturnValue(color);
    }
}
