package dev.customcolors.mixin;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.color.ParticleTints;
import net.minecraft.block.material.Material;
import net.minecraft.client.particle.EntityDropParticleFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Water drips keep the water colormap colour of the spot where they appeared; lava drips walk through
 * the lavadrop list by age in ticks. Vanilla resets the drip colour every tick, so it is set again after.
 */
@Mixin(EntityDropParticleFX.class)
public abstract class CcDripParticleMixin {
    @Shadow private Material materialType;
    @Unique private boolean customcolors$waterTinted;
    @Unique private int customcolors$waterColour;
    @Unique private int customcolors$age;

    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void customcolors$start(World world, double x, double y, double z, Material material, CallbackInfo ci) {
        try {
            if (material == Material.water) {
                int c = ParticleTints.water(world, x, y, z);
                if (c >= 0) {
                    customcolors$waterTinted = true;
                    customcolors$waterColour = c;
                }
            }
            customcolors$apply();
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("drip colour", t);
        }
    }

    @Inject(method = "onUpdate", at = @At("RETURN"), require = 0)
    private void customcolors$update(CallbackInfo ci) {
        try {
            customcolors$age++;
            customcolors$apply();
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("drip colour", t);
        }
    }

    @Unique
    private void customcolors$apply() {
        EntityFX self = (EntityFX) (Object) this;
        if (materialType == Material.water) {
            if (customcolors$waterTinted) {
                ParticleTints.paint(self, customcolors$waterColour);
            }
        } else if (materialType == Material.lava) {
            int c = ParticleTints.lavaDrop(customcolors$age);
            if (c >= 0) {
                ParticleTints.paint(self, c);
            }
        }
    }
}
