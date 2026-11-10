package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** On 1.7.10 the 3x3 biome blend for water lives in BlockLiquid.colorMultiplier (there is no BiomeColorHelper yet). */
@Mixin(BlockLiquid.class)
public abstract class BlockLiquidMixin {
    @Inject(method = "colorMultiplier(Lnet/minecraft/world/IBlockAccess;III)I", at = @At("HEAD"), cancellable = true)
    private void customcolors$water(IBlockAccess world, int x, int y, int z, CallbackInfoReturnable<Integer> cir) {
        if (!CustomColors.hasWater() || ((Block) (Object) this).getMaterial() != Material.water) return;
        int red = 0, green = 0, blue = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int color = CustomColors.water(world.getBiomeGenForCoords(x + dx, z + dz), x + dx, y, z + dz);
                red += color >> 16 & 255;
                green += color >> 8 & 255;
                blue += color & 255;
            }
        }
        cir.setReturnValue((red / 9) << 16 | (green / 9) << 8 | blue / 9);
    }
}
