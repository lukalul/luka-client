package dev.customcolors.mixin;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.color.ParticleTints;
import net.minecraft.client.particle.EntityAuraFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mycelium particles take a random entry of the pack's myceliumparticle list. */
@Mixin(EntityAuraFX.class)
public abstract class CcAuraParticleMixin {
    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void customcolors$tint(World world, double x, double y, double z, double dx, double dy, double dz,
                                   CallbackInfo ci) {
        try {
            int c = ParticleTints.mycelium();
            if (c >= 0) {
                ParticleTints.paint((EntityFX) (Object) this, c);
            }
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("mycelium particle colour", t);
        }
    }
}
