package dev.cbui.mixin.skins;

import java.awt.image.BufferedImage;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the renderer see the size of a downloaded skin, so a 64x32 texture is never drawn with the 64x64 model. */
@Mixin(ThreadDownloadImageData.class)
public interface SkinsDownloadedImageAccessor {
  @Accessor("bufferedImage")
  BufferedImage cbui$image();
}
