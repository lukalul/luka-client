package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeColorHelper;
import net.minecraft.world.biome.BiomeGenBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeColorHelper.class)
public abstract class BiomeColorHelperMixin {
    /** The same 3x3 biome blend vanilla uses for water. */
    @Inject(method = "getWaterColorAtPos", at = @At("HEAD"), cancellable = true)
    private static void customcolors$water(IBlockAccess world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!CustomColors.hasWater()) return;
        int red = 0, green = 0, blue = 0;
        for (BlockPos.MutableBlockPos at : BlockPos.getAllInBoxMutable(pos.add(-1, 0, -1), pos.add(1, 0, 1))) {
            BiomeGenBase biome = world.getBiomeGenForCoords(at);
            int color = CustomColors.water(biome, at);
            red += color >> 16 & 255;
            green += color >> 8 & 255;
            blue += color & 255;
        }
        cir.setReturnValue((red / 9) << 16 | (green / 9) << 8 | blue / 9);
    }
}
