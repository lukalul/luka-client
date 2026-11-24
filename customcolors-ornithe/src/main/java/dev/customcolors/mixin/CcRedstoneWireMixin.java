package dev.customcolors.mixin;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.color.BlockTints;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Redstone wire tint per power level from the pack's redstone colormap. */
@Mixin(BlockRedstoneWire.class)
public abstract class CcRedstoneWireMixin {
    @Inject(method = "colorMultiplier(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;I)I",
        at = @At("HEAD"), cancellable = true, require = 0)
    private void customcolors$wire(IBlockAccess world, BlockPos pos, int pass, CallbackInfoReturnable<Integer> cir) {
        try {
            int c = BlockTints.redstone(world, pos, (Block) (Object) this);
            if (c >= 0) {
                cir.setReturnValue(c);
            }
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("redstone colour", t);
        }
    }
}
