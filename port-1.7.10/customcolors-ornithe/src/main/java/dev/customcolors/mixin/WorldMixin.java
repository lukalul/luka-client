package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(World.class)
public abstract class WorldMixin {
    @Redirect(method = "getSkyColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/biome/BiomeGenBase;getSkyColorByTemp(F)I"))
    private int customcolors$sky(BiomeGenBase biome, float temperature) {
        World world = (World) (Object) this;
        return CustomColors.hasSky(world) ? CustomColors.sky(world) : biome.getSkyColorByTemp(temperature);
    }
}
