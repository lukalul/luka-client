package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import net.minecraft.client.particle.EntityAuraFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In 1.7.10 only mycelium spawns this particle. */
@Mixin(EntityAuraFX.class)
public abstract class EntityAuraFXMixin extends EntityFX {
    protected EntityAuraFXMixin(World world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/World;DDDDDD)V", at = @At("RETURN"))
    private void customcolors$color(CallbackInfo ci) {
        int color = CustomColors.mycelium();
        if (color == -1) return;
        particleRed = (color >> 16 & 255) / 255.0F;
        particleGreen = (color >> 8 & 255) / 255.0F;
        particleBlue = (color & 255) / 255.0F;
    }
}
