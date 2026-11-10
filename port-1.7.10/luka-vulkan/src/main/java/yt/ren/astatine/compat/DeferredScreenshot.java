package yt.ren.astatine.compat;

import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.chat.ChatGui;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import yt.ren.astatine.Native;

/**
 * luka: a screenshot of the game's picture, taken when the frame is complete instead of when it was asked for.
 *
 * The game asks for a screenshot between frames and expects the last frame to still sit in its picture. The renderer
 * draws the game straight into the window when it can, so between frames the last frame is already on its way to
 * the screen and the picture is stale. The screenshot is therefore taken at the end of the frame that follows, right
 * before that frame is handed to the window: the renderer then copies the finished frame back for the read.
 */
public final class DeferredScreenshot {
	/** What the caller gets while the screenshot waits: an empty message, never null, so adding it to the chat is harmless. */
	public static final Text PENDING = new LiteralText("");

	private static RenderTarget shown;
	private static File gameDir;
	private static String name;
	private static int width;
	private static int height;
	private static RenderTarget target;
	private static ChatGui chat;
	private static boolean pending;
	private static boolean running;

	private DeferredScreenshot() {
	}

	/** True when the screenshot now waits for the frame; the caller gets PENDING then, and the real message goes to the chat later. */
	public static boolean defer(File gameDir, String name, int width, int height, RenderTarget target) {
		if (running || target == null || target != shown) return false;
		if (GLCompat.framebufferHandle(target.frameBufferId) == 0L || !Native.directPossible()) return false;
		DeferredScreenshot.gameDir = gameDir;
		DeferredScreenshot.name = name;
		DeferredScreenshot.width = width;
		DeferredScreenshot.height = height;
		DeferredScreenshot.target = target;
		chat = null;
		pending = true;
		return true;
	}

	/** The caller would have put the screenshot's message in this chat. */
	public static void announce(ChatGui chat) {
		if (pending) DeferredScreenshot.chat = chat;
	}

	/** The picture is complete and about to be copied over the whole window. */
	public static void frameDrawn(RenderTarget drawn) {
		shown = drawn;
		if (!pending || drawn != target) return;
		pending = false;
		ChatGui chat = DeferredScreenshot.chat;
		DeferredScreenshot.chat = null;
		target = null;
		running = true;
		try {
			Text message = Screenshot.take(gameDir, name, width, height, drawn);
			if (chat == null) {
				Minecraft minecraft = Minecraft.getInstance();
				if (minecraft != null && minecraft.gui != null) chat = minecraft.gui.getChat();
			}
			if (chat != null && message != null) chat.addMessage(message);
		} finally {
			running = false;
		}
	}
}
