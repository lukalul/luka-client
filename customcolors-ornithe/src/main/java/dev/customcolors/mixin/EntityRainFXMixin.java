package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.EntityRainFX;
import net.minecraft.util.BlockPos;
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

    @Inject(method = "<init>", at = @At("RETURN"))
    private void customcolors$color(CallbackInfo ci) {
        if (!CustomColors.hasWater()) return;
        BlockPos pos = new BlockPos(posX, posY, posZ);
        int color = CustomColors.water(worldObj.getBiomeGenForCoords(pos), pos);
        particleRed = (color >> 16 & 255) / 255.0F;
        particleGreen = (color >> 8 & 255) / 255.0F;
        particleBlue = (color & 255) / 255.0F;
    }
}
