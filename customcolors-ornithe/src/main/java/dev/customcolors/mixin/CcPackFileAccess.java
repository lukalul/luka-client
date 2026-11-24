package dev.customcolors.mixin;

import java.io.File;
import net.minecraft.client.resources.AbstractResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The folder or zip a resource pack was read from (to list its anim/ and mob/ files). */
@Mixin(AbstractResourcePack.class)
public interface CcPackFileAccess {
    @Accessor("resourcePackFile")
    File customcolors$file();
}
