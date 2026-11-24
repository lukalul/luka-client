package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.Cbui;
import dev.cbui.feature.leveldigits.LevelDigitsModule;
import net.minecraft.item.ItemPotion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * the level digits card: the potion strength lookup ("potion.potency.N") answers with digits. Every other lookup in
 * the tooltip (effect names, attribute lines) is matched by its key and passed through unchanged.
 */
@Mixin(ItemPotion.class)
public abstract class LevelDigitsItemPotionMixin {
  @WrapOperation(method = "addInformation",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StatCollector;translateToLocal(Ljava/lang/String;)Ljava/lang/String;"),
      require = 0)
  private String cbui$potionDigits(String key, Operation<String> original) {
    String digits = null;
    try {
      LevelDigitsModule module = LevelDigitsModule.active();
      if (module != null) digits = module.potionStrength(key);
    } catch (Throwable error) {
      Cbui.reportOnce("numerical enchantments potion", error);
    }
    return digits != null ? digits : original.call(key);
  }
}
