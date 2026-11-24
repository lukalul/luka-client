package dev.customcolors.mixin;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.color.SkyTints;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Overworld's distance-fog colour from the pack's fog0 colormap, shaped by vanilla's day curve. */
@Mixin(WorldProvider.class)
public abstract class CcWorldProviderFogMixin {
    @Shadow protected World worldObj;

    @Inject(method = "getFogColor", at = @At("HEAD"), cancellable = true, require = 0)
    private void customcolors$fog(float celestialAngle, float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        try {
            Vec3 c = SkyTints.fog(worldObj, celestialAngle);
            if (c != null) {
                cir.setReturnValue(c);
            }
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("fog colour", t);
        }
    }
}
