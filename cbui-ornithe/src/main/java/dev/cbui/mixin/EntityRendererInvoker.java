package dev.cbui.mixin;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** {@code EntityRenderer.loadShader} is private in 1.8.9; the menu blur needs to start a post effect by name. */
@Mixin(EntityRenderer.class)
public interface EntityRendererInvoker {
    @Invoker("loadShader")
    void cbui$loadShader(ResourceLocation effect);
}
