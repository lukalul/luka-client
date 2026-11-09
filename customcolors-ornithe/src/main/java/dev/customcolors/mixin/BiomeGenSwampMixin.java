package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import net.minecraft.util.BlockPos;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.BiomeGenSwamp;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeGenSwamp.class)
public abstract class BiomeGenSwampMixin {
    @Inject(method = "getGrassColorAtPos", at = @At("HEAD"), cancellable = true)
    private void customcolors$grass(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (CustomColors.hasSwampGrass()) cir.setReturnValue(CustomColors.swampGrass((BiomeGenBase) (Object) this, pos));
    }

    @Inject(method = "getFoliageColorAtPos", at = @At("HEAD"), cancellable = true)
    private void customcolors$foliage(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (CustomColors.hasSwampFoliage()) cir.setReturnValue(CustomColors.swampFoliage((BiomeGenBase) (Object) this, pos));
    }
}
