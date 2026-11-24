package dev.customcolors.mixin;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.mobs.MobSkins;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Swaps an entity texture for the alternate chosen for the entity being drawn. */
@Mixin(Render.class)
public abstract class CcRenderMixin {
    @ModifyVariable(method = "bindTexture", at = @At("HEAD"), argsOnly = true, require = 0)
    private ResourceLocation customcolors$alternate(ResourceLocation texture) {
        try {
            return MobSkins.swap(texture);
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("random mob skin", t);
            return texture;
        }
    }
}
