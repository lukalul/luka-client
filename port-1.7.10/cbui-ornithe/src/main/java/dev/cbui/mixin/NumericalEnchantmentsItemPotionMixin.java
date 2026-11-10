package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.cbui.feature.numericalenchantments.NumericalEnchantmentsModule;
import net.minecraft.item.ItemPotion;
import net.minecraft.potion.PotionEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A potion tooltip's strength suffix is the second localisation lookup; swap it for amplifier + 1 when enabled. */
@Mixin(ItemPotion.class)
public abstract class NumericalEnchantmentsItemPotionMixin {
  @WrapOperation(method = "addInformation",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StatCollector;translateToLocal(Ljava/lang/String;)Ljava/lang/String;", ordinal = 1),
      require = 0)
  private static String cbui$plainStrength(String key, Operation<String> vanillaLookup, @Local PotionEffect effect) {
    if (effect != null && NumericalEnchantmentsModule.substituting()) return Integer.toString(effect.getAmplifier() + 1);
    return vanillaLookup.call(key);
  }
}
