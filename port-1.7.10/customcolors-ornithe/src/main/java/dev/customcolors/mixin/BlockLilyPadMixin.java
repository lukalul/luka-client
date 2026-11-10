package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import net.minecraft.block.BlockLilyPad;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockLilyPad.class)
public abstract class BlockLilyPadMixin {
    @Inject(method = "colorMultiplier(Lnet/minecraft/world/IBlockAccess;III)I", at = @At("HEAD"), cancellable = true)
    private void customcolors$color(IBlockAccess world, int x, int y, int z, CallbackInfoReturnable<Integer> cir) {
        int color = CustomColors.lilypad();
        if (color != -1) cir.setReturnValue(color);
    }
}
