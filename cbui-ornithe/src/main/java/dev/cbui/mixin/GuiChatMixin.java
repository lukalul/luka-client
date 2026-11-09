package dev.cbui.mixin;

import dev.cbui.chat.ChatAccess;
import dev.cbui.feature.chat.ChatModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatComponentText;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ctrl + left click on a chat message copies its text. */
@Mixin(GuiChat.class)
public abstract class GuiChatMixin {
  @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$copy(int mouseX, int mouseY, int button, CallbackInfo ci) {
    ChatModule module = ChatModule.active();
    if (module == null || !module.copy.get() || button != 0 || !GuiScreen.isCtrlKeyDown()) return;
    try {
      Minecraft mc = Minecraft.getMinecraft();
      GuiNewChat chat = mc.ingameGUI.getChatGUI();
      String text = ((ChatAccess) chat).cbui$messageAt(Mouse.getX(), Mouse.getY());
      if (text == null || text.isEmpty()) return;
      GuiScreen.setClipboardString(text);
      mc.ingameGUI.setRecordPlaying(new ChatComponentText("Copied chat message"), false);
      ci.cancel();
    } catch (Throwable ignored) {
      // fail open: the click behaves like vanilla
    }
  }
}
