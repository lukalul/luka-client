package dev.cbui.mixin;

import dev.cbui.feature.screenshotmessageenhancer.ScreenshotActions;
import dev.cbui.feature.screenshotmessageenhancer.ScreenshotMessageEnhancerModule;
import java.io.File;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ScreenShotHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScreenShotHelper.class)
public abstract class ScreenshotMessageEnhancerScreenShotHelperMixin {
  @Redirect(method = "saveScreenshot(Ljava/io/File;Ljava/lang/String;IILnet/minecraft/client/shader/Framebuffer;)Lnet/minecraft/util/IChatComponent;",
      at = @At(value = "INVOKE", target = "Ljavax/imageio/ImageIO;write(Ljava/awt/image/RenderedImage;Ljava/lang/String;Ljava/io/File;)Z", remap = false), require = 0)
  private static boolean cbui$writeInBackground(java.awt.image.RenderedImage image, String format, File file) throws java.io.IOException {
    return ScreenshotActions.writeInBackground(image, format, file);
  }

  @Inject(method = "saveScreenshot(Ljava/io/File;Ljava/lang/String;IILnet/minecraft/client/shader/Framebuffer;)Lnet/minecraft/util/IChatComponent;",
      at = @At("RETURN"), cancellable = true, require = 0)
  private static void cbui$replaceSavedMessage(File gameDirectory, String requestedName, int width, int height,
      Framebuffer framebuffer, CallbackInfoReturnable<IChatComponent> result) {
    ScreenshotMessageEnhancerModule module = ScreenshotMessageEnhancerModule.lookup(true);
    if (module == null) return;
    try {
      File screenshot = ScreenshotActions.findSavedFile(result.getReturnValue());
      if (screenshot != null) result.setReturnValue(ScreenshotActions.createMessage(module, screenshot));
    } catch (Throwable ignored) { }
  }
}
