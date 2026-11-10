package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldProvider.class)
public abstract class WorldProviderMixin {
    @Shadow protected World worldObj;

    /** Vanilla's fog curve with the pack's colour in place of its fixed light blue. */
    @Inject(method = "getFogColor", at = @At("HEAD"), cancellable = true)
    private void customcolors$fog(float celestialAngle, float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        if (worldObj == null || !CustomColors.hasFog(worldObj)) return;
        int color = CustomColors.fog(worldObj);
        float light = MathHelper.cos(celestialAngle * (float) Math.PI * 2.0F) * 2.0F + 0.5F;
        light = Math.max(0.0F, Math.min(1.0F, light));
        double red = (color >> 16 & 255) / 255.0F * (light * 0.94F + 0.06F);
        double green = (color >> 8 & 255) / 255.0F * (light * 0.94F + 0.06F);
        double blue = (color & 255) / 255.0F * (light * 0.91F + 0.09F);
        cir.setReturnValue(Vec3.createVectorHelper(red, green, blue));
    }
}
