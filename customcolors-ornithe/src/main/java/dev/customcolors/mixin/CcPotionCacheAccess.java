package dev.customcolors.mixin;

import java.util.Map;
import net.minecraft.potion.PotionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla's potion colour cache (item damage value -> colour), emptied on every resource reload. */
@Mixin(PotionHelper.class)
public interface CcPotionCacheAccess {
    @Accessor("DATAVALUE_COLORS")
    static Map<Integer, Integer> customcolors$colours() {
        throw new AssertionError();
    }
}
