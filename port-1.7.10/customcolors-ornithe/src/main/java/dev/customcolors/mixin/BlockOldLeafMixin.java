package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import net.minecraft.block.BlockOldLeaf;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockOldLeaf.class)
public abstract class BlockOldLeafMixin {
    /** The low two metadata bits are the wood type: 1 spruce, 2 birch. */
    @Inject(method = "colorMultiplier(Lnet/minecraft/world/IBlockAccess;III)I", at = @At("HEAD"), cancellable = true)
    private void customcolors$leaves(IBlockAccess world, int x, int y, int z, CallbackInfoReturnable<Integer> cir) {
        int type = world.getBlockMetadata(x, y, z) & 3;
        if (type == 1 && CustomColors.hasPine()) {
            cir.setReturnValue(CustomColors.pine(world.getBiomeGenForCoords(x, z), x, y, z));
        } else if (type == 2 && CustomColors.hasBirch()) {
            cir.setReturnValue(CustomColors.birch(world.getBiomeGenForCoords(x, z), x, y, z));
        }
    }
}
