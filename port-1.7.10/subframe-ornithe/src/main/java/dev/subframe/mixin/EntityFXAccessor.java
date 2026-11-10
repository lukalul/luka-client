package dev.subframe.mixin;

import net.minecraft.client.particle.EntityFX;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityFX.class)
public interface EntityFXAccessor {
    @Accessor("particleScale") float subframe$getScale();
    @Accessor("particleAge") int subframe$getAge();
    @Accessor("particleMaxAge") int subframe$getMaxAge();
}
