package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.EntityRainFX;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRainFX.class)
public abstract class EntityRainFXMixin extends EntityFX {
    protected EntityRainFXMixin(World world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/World;DDD)V", at = @At("RETURN"))
    private void customcolors$color(CallbackInfo ci) {
        if (!CustomColors.hasWater()) return;
        int x = MathHelper.floor_double(posX), y = MathHelper.floor_double(posY), z = MathHelper.floor_double(posZ);
        int color = CustomColors.water(worldObj.getBiomeGenForCoords(x, z), x, y, z);
        particleRed = (color >> 16 & 255) / 255.0F;
        particleGreen = (color >> 8 & 255) / 255.0F;
        particleBlue = (color & 255) / 255.0F;
    }
}
