package yt.ren.astatine.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.chat.ChatGui;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.compat.DeferredScreenshot;

@Mixin(Minecraft.class)
public abstract class MinecraftScreenshotMixin {
	// luka: F2. A screenshot that waits for the frame to be drawn has no message yet; it is added when the file is written.
	@Redirect(method = "handleGuiKeyBindings", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/chat/ChatGui;addMessage(Lnet/minecraft/text/Text;)V"))
	private void astatine$screenshotMessage(ChatGui chat, Text message) {
		if (message == DeferredScreenshot.PENDING) {
			DeferredScreenshot.announce(chat);
		} else {
			chat.addMessage(message);
		}
	}
}
