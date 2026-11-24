package dev.customcolors.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.customcolors.CustomColorsMod;
import dev.customcolors.color.SkyTints;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The Overworld's base sky colour from the pack's sky0 colormap; vanilla's shading still applies. */
@Mixin(World.class)
public abstract class CcWorldSkyMixin {
    @ModifyExpressionValue(method = "getSkyColor",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/biome/BiomeGenBase;getSkyColorByTemp(F)I"),
        require = 0)
    private int customcolors$sky(int vanilla, @Local(argsOnly = true) Entity viewer) {
        try {
            return SkyTints.sky((World) (Object) this, viewer, vanilla);
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("sky colour", t);
            return vanilla;
        }
    }
}
