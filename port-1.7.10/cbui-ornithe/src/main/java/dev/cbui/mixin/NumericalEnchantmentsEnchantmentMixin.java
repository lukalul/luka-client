package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.feature.numericalenchantments.NumericalEnchantmentsModule;
import net.minecraft.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The level text of an enchantment name is the second localisation lookup; swap it for the plain level when enabled. */
@Mixin(Enchantment.class)
public abstract class NumericalEnchantmentsEnchantmentMixin {
  @WrapOperation(method = "getTranslatedName",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StatCollector;translateToLocal(Ljava/lang/String;)Ljava/lang/String;", ordinal = 1),
      require = 0)
  private static String cbui$plainLevel(String key, Operation<String> vanillaLookup, int level) {
    return NumericalEnchantmentsModule.substituting() ? Integer.toString(level) : vanillaLookup.call(key);
  }
}
