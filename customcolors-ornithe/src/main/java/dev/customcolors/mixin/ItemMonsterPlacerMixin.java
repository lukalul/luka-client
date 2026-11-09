package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import net.minecraft.entity.EntityList;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemMonsterPlacer.class)
public abstract class ItemMonsterPlacerMixin {
    @Inject(method = "getColorFromItemStack", at = @At("RETURN"), cancellable = true)
    private void customcolors$color(ItemStack stack, int pass, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(CustomColors.egg(EntityList.getStringFromID(stack.getMetadata()), pass != 0, cir.getReturnValue()));
    }
}
