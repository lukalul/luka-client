package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import net.minecraft.potion.Potion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Potion.class)
public abstract class PotionMixin {
    @Shadow public abstract String getName();

    @Inject(method = "getLiquidColor", at = @At("RETURN"), cancellable = true)
    private void customcolors$color(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(CustomColors.potion(getName(), cir.getReturnValue()));
    }
}
