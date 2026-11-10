package dev.subframe.mixin;

import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(RenderGlobal.class)
public interface RenderGlobalAccessor {
    @Accessor("cloudTickCounter") int subframe$getCloudTickCounter();
    @Accessor("cloudTickCounter") void subframe$setCloudTickCounter(int value);
    @Accessor("worldRenderersToUpdate") List<?> subframe$getWorldRenderersToUpdate();
}
