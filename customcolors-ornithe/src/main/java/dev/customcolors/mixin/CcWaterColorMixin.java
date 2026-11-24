package dev.customcolors.mixin;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.color.BlockTints;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Water block colour from the pack's water colormap. */
@Mixin(BiomeColorHelper.class)
public abstract class CcWaterColorMixin {
    @Inject(method = "getWaterColorAtPos", at = @At("HEAD"), cancellable = true, require = 0)
    private static void customcolors$water(IBlockAccess world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        try {
            int c = BlockTints.water(world, pos);
            if (c >= 0) {
                cir.setReturnValue(c);
            }
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("water colour", t);
        }
    }
}
