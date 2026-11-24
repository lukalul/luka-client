package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.Cbui;
import dev.cbui.feature.shotchat.ShotLinks;
import dev.cbui.feature.shotchat.ShotQueue;
import java.awt.image.RenderedImage;
import java.io.File;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ScreenShotHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Screenshot message: the PNG write goes to luka's background queue (name reserved first), and the success message
 * is swapped for luka's line with buttons. Other messages, such as a renderer's empty placeholder, pass unchanged.
 */
@Mixin(ScreenShotHelper.class)
public abstract class ShotChatScreenShotHelperMixin {
  @WrapOperation(
      method = "saveScreenshot(Ljava/io/File;Ljava/lang/String;IILnet/minecraft/client/shader/Framebuffer;)Lnet/minecraft/util/IChatComponent;",
      at = @At(value = "INVOKE", target = "Ljavax/imageio/ImageIO;write(Ljava/awt/image/RenderedImage;Ljava/lang/String;Ljava/io/File;)Z"),
      require = 0)
  private static boolean cbui$writeLater(RenderedImage image, String format, File file, Operation<Boolean> original) {
    try {
      if (ShotQueue.saveInBackground(image, format, file)) return true;
    } catch (Throwable error) {
      Cbui.reportOnce("Screenshot Message Enhancer background save", error);
    }
    return original.call(image, format, file);
  }

  @ModifyReturnValue(
      method = "saveScreenshot(Ljava/io/File;Ljava/lang/String;IILnet/minecraft/client/shader/Framebuffer;)Lnet/minecraft/util/IChatComponent;",
      at = @At("RETURN"), require = 0)
  private static IChatComponent cbui$ownMessage(IChatComponent message) {
    try {
      return ShotLinks.replaceMessage(message);
    } catch (Throwable error) {
      Cbui.reportOnce("Screenshot Message Enhancer message", error);
      return message;
    }
  }
}
