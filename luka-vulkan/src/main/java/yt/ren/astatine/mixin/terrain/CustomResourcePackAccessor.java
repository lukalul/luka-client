package yt.ren.astatine.mixin.terrain;

import java.io.File;
import net.minecraft.client.resource.pack.CustomResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CustomResourcePack.class)
public interface CustomResourcePackAccessor {
	@Accessor("file")
	File getAstatineFile();
}
