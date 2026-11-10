package dev.subframe.mixin;

import net.minecraft.client.particle.EffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(EffectRenderer.class)
public interface EffectRendererAccessor {
    @Accessor("fxLayers") List<?>[] subframe$getFxLayers();
}
