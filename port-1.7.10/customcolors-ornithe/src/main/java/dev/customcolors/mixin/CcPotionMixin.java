package dev.customcolors.mixin;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.color.ItemTints;
import net.minecraft.potion.Potion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Effect liquid colours from color.properties ({@code potion.<name>}). */
@Mixin(Potion.class)
public abstract class CcPotionMixin {
    @Shadow @Final public int id;

    @Inject(method = "getLiquidColor", at = @At("HEAD"), cancellable = true, require = 0)
    private void customcolors$liquid(CallbackInfoReturnable<Integer> cir) {
        try {
            int c = ItemTints.potion(id);
            if (c >= 0) {
                cir.setReturnValue(c);
            }
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("potion colour", t);
        }
    }
}
