package dev.customcolors.mixin;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.color.ItemTints;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Spawn-egg shell and spot colours from color.properties ({@code egg.shell.*}, {@code egg.spots.*}). */
@Mixin(ItemMonsterPlacer.class)
public abstract class CcSpawnEggMixin {
    @Inject(method = "getColorFromItemStack", at = @At("HEAD"), cancellable = true, require = 0)
    private void customcolors$egg(ItemStack stack, int pass, CallbackInfoReturnable<Integer> cir) {
        try {
            if (stack != null) {
                int c = ItemTints.egg(stack.getMetadata(), pass);
                if (c >= 0) {
                    cir.setReturnValue(c);
                }
            }
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("spawn egg colour", t);
        }
    }
}
