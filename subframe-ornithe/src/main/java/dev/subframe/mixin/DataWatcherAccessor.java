package dev.subframe.mixin;

import net.minecraft.entity.DataWatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(DataWatcher.class)
public interface DataWatcherAccessor {
    @Accessor("watchedObjects")
    Map<Integer, DataWatcher.WatchableObject> subframe$getWatchedObjects();
}
