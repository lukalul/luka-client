package dev.subframe.mixin;

import net.minecraft.client.particle.EntityCrit2FX;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What a crit emitter (1.7.10's particle emitter) is attached to and which sprite it spawns. */
@Mixin(EntityCrit2FX.class)
public interface EntityCrit2FXAccessor {
    @Accessor("theEntity") Entity subframe$getTheEntity();
    @Accessor("particleName") String subframe$getParticleName();
}
