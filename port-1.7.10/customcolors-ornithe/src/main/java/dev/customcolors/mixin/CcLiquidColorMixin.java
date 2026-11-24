package dev.customcolors.mixin;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.color.BlockTints;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Water block colour from the pack's water colormap (lava is never touched). */
@Mixin(BlockLiquid.class)
public abstract class CcLiquidColorMixin {
    @Inject(method = "colorMultiplier", at = @At("HEAD"), cancellable = true, require = 0)
    private void customcolors$water(IBlockAccess world, int x, int y, int z, CallbackInfoReturnable<Integer> cir) {
        try {
            if (((Block) (Object) this).getMaterial() != Material.water) {
                return;
            }
            int c = BlockTints.water(world, x, y, z);
            if (c >= 0) {
                cir.setReturnValue(c);
            }
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("water colour", t);
        }
    }
}
