package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.cbui.render.GlintDistance;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 1.7.10 asks the stack whether it has a glint in every place a glint is drawn: dropped items and held items use hasEffect,
 * and armour layers use isItemEnchanted. Answering "no" beyond the configured distance skips all of them.
 */
@Mixin(ItemStack.class)
public abstract class GlintDistanceItemMixin {
  @ModifyReturnValue(method = "hasEffect", at = @At("RETURN"), require = 0)
  private boolean cbui$keepGlint(boolean glint) {
    return glint && !GlintDistance.skip();
  }

  @ModifyReturnValue(method = "isItemEnchanted", at = @At("RETURN"), require = 0)
  private boolean cbui$keepArmorGlint(boolean enchanted) {
    return enchanted && !GlintDistance.skipInWorld();
  }
}
