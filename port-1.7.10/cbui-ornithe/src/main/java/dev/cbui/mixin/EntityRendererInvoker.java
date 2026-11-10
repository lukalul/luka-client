package dev.cbui.mixin;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.shader.ShaderGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.7.10 only cycles its built-in post effects; the menu blur needs to start one by name. */
@Mixin(EntityRenderer.class)
public interface EntityRendererInvoker {
    @Accessor("theShaderGroup")
    void cbui$setShaderGroup(ShaderGroup group);
}
