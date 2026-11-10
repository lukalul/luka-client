package yt.ren.astatine.mixin.texture;

import java.io.File;
import net.minecraft.client.resource.pack.ResourcePacks__UnopenedPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a pack lives on disk, for packs another mod opens through its own pack class. */
@Mixin(ResourcePacks__UnopenedPack.class)
public interface UnopenedPackAccessor {
	@Accessor("file")
	File getAstatineFile();
}
