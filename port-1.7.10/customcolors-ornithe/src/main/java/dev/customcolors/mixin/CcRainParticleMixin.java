package dev.customcolors.mixin;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.color.ParticleTints;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.EntityRainFX;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rain splashes take the water colormap colour where they appear. */
@Mixin(EntityRainFX.class)
public abstract class CcRainParticleMixin {
    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void customcolors$tint(World world, double x, double y, double z, CallbackInfo ci) {
        try {
            int c = ParticleTints.water(world, x, y, z);
            if (c >= 0) {
                ParticleTints.paint((EntityFX) (Object) this, c);
            }
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("rain splash colour", t);
        }
    }
}
