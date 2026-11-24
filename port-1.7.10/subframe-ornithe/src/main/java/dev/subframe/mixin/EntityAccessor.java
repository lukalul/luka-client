package dev.subframe.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.entity.DataWatcher;

import java.util.Random;

@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("rand") Random subframe$getRandom();

    /** For particles rebuilt from a recording (ParticleState.rebuild), which never ran the Entity constructor. */
    @Mutable @Accessor("rand") void subframe$setRandom(Random random);

    @Mutable @Accessor("dataWatcher") void subframe$setDataWatcher(DataWatcher watcher);
}
