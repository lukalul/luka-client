package dev.customcolors.mixin;

import dev.customcolors.RandomMobs;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Render.class)
public abstract class RenderMixin {
    @ModifyVariable(method = "bindTexture", at = @At("HEAD"), argsOnly = true)
    private ResourceLocation customcolors$randomMob(ResourceLocation texture) {
        return RandomMobs.choose(texture);
    }
}
