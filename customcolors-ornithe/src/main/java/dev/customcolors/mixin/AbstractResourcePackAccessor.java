package dev.customcolors.mixin;

import java.io.File;
import net.minecraft.client.resources.AbstractResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractResourcePack.class)
public interface AbstractResourcePackAccessor {
    @Accessor("resourcePackFile")
    File customcolors$file();
}
