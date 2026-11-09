package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import net.minecraft.potion.PotionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(PotionHelper.class)
public abstract class PotionHelperMixin {
    /** The water bottle colour. */
    @ModifyConstant(method = "calcPotionLiquidColor", constant = @Constant(intValue = 3694022))
    private static int customcolors$water(int original) {
        return CustomColors.waterBottle(original);
    }
}
