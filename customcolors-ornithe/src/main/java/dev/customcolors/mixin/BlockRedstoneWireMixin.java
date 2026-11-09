package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockRedstoneWire.class)
public abstract class BlockRedstoneWireMixin {
    @Inject(method = "colorMultiplier", at = @At("HEAD"), cancellable = true)
    private void customcolors$color(IBlockAccess world, BlockPos pos, int pass, CallbackInfoReturnable<Integer> cir) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != (Object) this) return;
        int color = CustomColors.redstone(state.getValue(BlockRedstoneWire.POWER));
        if (color != -1) cir.setReturnValue(color);
    }
}
