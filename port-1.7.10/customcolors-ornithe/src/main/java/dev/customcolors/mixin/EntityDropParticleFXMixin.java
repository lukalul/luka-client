package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import net.minecraft.block.material.Material;
import net.minecraft.client.particle.EntityDropParticleFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityDropParticleFX.class)
public abstract class EntityDropParticleFXMixin extends EntityFX {
    @Shadow private Material materialType;
    @Shadow private int bobTimer;

    protected EntityDropParticleFXMixin(World world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/World;DDDLnet/minecraft/block/material/Material;)V", at = @At("RETURN"))
    private void customcolors$water(CallbackInfo ci) {
        if (materialType != Material.water || !CustomColors.hasWater()) return;
        int x = MathHelper.floor_double(posX), y = MathHelper.floor_double(posY), z = MathHelper.floor_double(posZ);
        customcolors$apply(CustomColors.water(worldObj.getBiomeGenForCoords(x, z), x, y, z));
    }

    @Inject(method = "onUpdate", at = @At("TAIL"))
    private void customcolors$lava(CallbackInfo ci) {
        if (materialType == Material.water) return;
        int color = CustomColors.lavaDrop(40 - bobTimer);
        if (color != -1) customcolors$apply(color);
    }

    @Unique
    private void customcolors$apply(int color) {
        particleRed = (color >> 16 & 255) / 255.0F;
        particleGreen = (color >> 8 & 255) / 255.0F;
        particleBlue = (color & 255) / 255.0F;
    }
}
