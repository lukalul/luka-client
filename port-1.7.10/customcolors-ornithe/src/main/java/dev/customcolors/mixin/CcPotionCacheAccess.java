package dev.customcolors.mixin;

import java.util.HashMap;
import net.minecraft.potion.PotionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Vanilla's potion colour cache (item damage value -> colour), emptied on every resource reload.
 * The field has no MCP name on 1.7.10.
 */
@Mixin(PotionHelper.class)
public interface CcPotionCacheAccess {
    @Accessor("f_68427683")
    static HashMap<?, ?> customcolors$colours() {
        throw new AssertionError();
    }
}
