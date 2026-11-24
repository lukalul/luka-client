package dev.customcolors.mixin;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.color.BlockTints;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.BiomeGenSwamp;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Swamp grass and foliage colours from the pack's swampgrass/swampfoliage colormaps. */
@Mixin(BiomeGenSwamp.class)
public abstract class CcSwampBiomeMixin {
    @Inject(method = "getBiomeGrassColor", at = @At("HEAD"), cancellable = true, require = 0)
    private void customcolors$grass(int x, int y, int z, CallbackInfoReturnable<Integer> cir) {
        try {
            int c = BlockTints.swampGrass((BiomeGenBase) (Object) this, x, y, z);
            if (c >= 0) {
                cir.setReturnValue(c);
            }
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("swamp grass colour", t);
        }
    }

    @Inject(method = "getBiomeFoliageColor", at = @At("HEAD"), cancellable = true, require = 0)
    private void customcolors$foliage(int x, int y, int z, CallbackInfoReturnable<Integer> cir) {
        try {
            int c = BlockTints.swampFoliage((BiomeGenBase) (Object) this, x, y, z);
            if (c >= 0) {
                cir.setReturnValue(c);
            }
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("swamp foliage colour", t);
        }
    }
}
