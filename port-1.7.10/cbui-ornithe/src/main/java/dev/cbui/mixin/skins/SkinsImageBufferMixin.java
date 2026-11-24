package dev.cbui.mixin.skins;

import dev.cbui.Cbui;
import dev.cbui.skins.SkinImage;
import java.awt.image.BufferedImage;
import net.minecraft.client.renderer.ImageBufferDownload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The game crops every downloaded skin to 64x32, which throws away the second layer and the separate left limbs. Skins
 * are kept at 64x64 instead, and old 64x32 skins are widened to that layout. If that fails the vanilla crop still runs.
 */
@Mixin(ImageBufferDownload.class)
public abstract class SkinsImageBufferMixin {
  @Inject(method = "parseUserSkin(Ljava/awt/image/BufferedImage;)Ljava/awt/image/BufferedImage;", at = @At("HEAD"),
      cancellable = true, require = 0)
  private void cbui$keepFullSkin(BufferedImage image, CallbackInfoReturnable<BufferedImage> cir) {
    if (image == null) return;
    try {
      cir.setReturnValue(SkinImage.toModern(image));
    } catch (Throwable error) {
      Cbui.reportOnce("64x64 skins", error);
    }
  }
}
