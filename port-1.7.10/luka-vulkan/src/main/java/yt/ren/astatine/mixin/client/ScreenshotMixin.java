package yt.ren.astatine.mixin.client;

import java.io.File;
import net.minecraft.client.Screenshot;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yt.ren.astatine.compat.DeferredScreenshot;

@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {
	// luka: screenshots are asked for between frames, when the last frame may only exist in the window. See DeferredScreenshot.
	@Inject(
		method = "take(Ljava/io/File;Ljava/lang/String;IILnet/minecraft/client/render/pipeline/RenderTarget;)Lnet/minecraft/text/Text;",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void astatine$defer(File gameDir, String name, int width, int height, RenderTarget target, CallbackInfoReturnable<Text> callback) {
		if (DeferredScreenshot.defer(gameDir, name, width, height, target)) callback.setReturnValue(DeferredScreenshot.PENDING);
	}
}
