package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.Cbui;
import dev.cbui.feature.leveldigits.LevelDigitsModule;
import net.minecraft.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** the level digits card: the level lookup of an enchantment name answers with digits; the name lookup is untouched. */
@Mixin(Enchantment.class)
public abstract class LevelDigitsEnchantmentMixin {
  @WrapOperation(method = "getTranslatedName",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StatCollector;translateToLocal(Ljava/lang/String;)Ljava/lang/String;"),
      require = 0)
  private String cbui$levelDigits(String key, Operation<String> original) {
    String digits = null;
    try {
      LevelDigitsModule module = LevelDigitsModule.active();
      if (module != null) digits = module.enchantmentLevel(key);
    } catch (Throwable error) {
      Cbui.reportOnce("numerical enchantments level", error);
    }
    return digits != null ? digits : original.call(key);
  }
}
