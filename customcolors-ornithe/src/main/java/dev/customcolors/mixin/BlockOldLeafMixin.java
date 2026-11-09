package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import net.minecraft.block.BlockOldLeaf;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockOldLeaf.class)
public abstract class BlockOldLeafMixin {
    @Inject(method = "colorMultiplier", at = @At("HEAD"), cancellable = true)
    private void customcolors$leaves(IBlockAccess world, BlockPos pos, int pass, CallbackInfoReturnable<Integer> cir) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != (Object) this) return;
        BlockPlanks.EnumType type = state.getValue(BlockOldLeaf.VARIANT);
        if (type == BlockPlanks.EnumType.SPRUCE && CustomColors.hasPine()) {
            cir.setReturnValue(CustomColors.pine(world.getBiomeGenForCoords(pos), pos));
        } else if (type == BlockPlanks.EnumType.BIRCH && CustomColors.hasBirch()) {
            cir.setReturnValue(CustomColors.birch(world.getBiomeGenForCoords(pos), pos));
        }
    }
}
