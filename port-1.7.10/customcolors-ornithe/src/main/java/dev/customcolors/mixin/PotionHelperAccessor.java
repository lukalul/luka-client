package dev.customcolors.mixin;

import java.util.HashMap;
import net.minecraft.potion.PotionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PotionHelper.class)
public interface PotionHelperAccessor {
    @Accessor("f_68427683")
    static HashMap<Integer, Integer> customcolors$cache() {
        throw new AssertionError();
    }
}
