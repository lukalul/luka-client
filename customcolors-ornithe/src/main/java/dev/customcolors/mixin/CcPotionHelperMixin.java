package dev.customcolors.mixin;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.color.ItemTints;
import java.util.Collection;
import net.minecraft.potion.PotionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bottles without effects (water, awkward, mundane, thick) in the pack's {@code potion.water} colour. */
@Mixin(PotionHelper.class)
public abstract class CcPotionHelperMixin {
    @Inject(method = "calcPotionLiquidColor", at = @At("HEAD"), cancellable = true, require = 0)
    private static void customcolors$noEffect(Collection<?> effects, CallbackInfoReturnable<Integer> cir) {
        try {
            int c = ItemTints.noEffect(effects);
            if (c >= 0) {
                cir.setReturnValue(c);
            }
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("water bottle colour", t);
        }
    }
}
