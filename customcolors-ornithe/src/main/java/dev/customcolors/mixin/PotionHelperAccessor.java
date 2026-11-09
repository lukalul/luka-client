package dev.customcolors.mixin;

import java.util.Map;
import net.minecraft.potion.PotionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PotionHelper.class)
public interface PotionHelperAccessor {
    @Accessor("DATAVALUE_COLORS")
    static Map<Integer, Integer> customcolors$cache() {
        throw new AssertionError();
    }
}
