package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStem;
import net.minecraft.init.Blocks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockStem.class)
public abstract class BlockStemMixin {
    /** The crop this stem grows; the name is unmapped in MCP stable_12. */
    @Shadow @Final private Block f_36833465;

    /** The metadata is the stem's age, and colorMultiplier goes through this method too. */
    @Inject(method = "getRenderColor(I)I", at = @At("HEAD"), cancellable = true)
    private void customcolors$color(int age, CallbackInfoReturnable<Integer> cir) {
        int color = CustomColors.stem(f_36833465 == Blocks.pumpkin, age);
        if (color != -1) cir.setReturnValue(color);
    }
}
