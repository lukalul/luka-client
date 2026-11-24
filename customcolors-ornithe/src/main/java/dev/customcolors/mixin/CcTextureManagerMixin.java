package dev.customcolors.mixin;

import dev.customcolors.CustomColorsMod;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the mod drop the pack textures it introduced before the texture manager reloads everything. */
@Mixin(TextureManager.class)
public abstract class CcTextureManagerMixin {
    @Inject(method = "onResourceManagerReload", at = @At("HEAD"), require = 0)
    private void customcolors$beforeReload(IResourceManager manager, CallbackInfo ci) {
        try {
            CustomColorsMod.beforeTextureReload((TextureManager) (Object) this);
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("texture reload", t);
        }
    }
}
