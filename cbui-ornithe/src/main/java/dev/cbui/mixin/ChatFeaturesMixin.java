package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.chat.ChatAccess;
import dev.cbui.feature.chat.ChatModule;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * What the Chat mod does to vanilla chat (see {@link ChatModule}), and finding the message under the mouse for Ctrl+click copy.
 */
@Mixin(GuiNewChat.class)
public abstract class ChatFeaturesMixin implements ChatAccess {
  @Shadow @Final private Minecraft mc;
  @Shadow @Final private List<ChatLine> chatLines;
  @Shadow @Final private List<ChatLine> drawnChatLines;
  @Shadow private int scrollPos;

  @Unique private String cbui$lastText;
  @Unique private int cbui$count;
  /** How many wrapped lines the last message took in drawnChatLines (they sit at the front, newest first). */
  @Unique private int cbui$lastLines;
  @Unique private ChatLine cbui$firstBefore;
  @Unique private boolean cbui$reprinting;

  // ---- background and text shadow ----

  /** The first drawRect in drawChat is the dark box behind each line (the later ones are the scroll bar). */
  @WrapOperation(
      method = "drawChat",
      at = {
        @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiNewChat;drawRect(IIIII)V", ordinal = 0),
        @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;drawRect(IIIII)V", ordinal = 0)
      },
      require = 0)
  private void cbui$background(int left, int top, int right, int bottom, int color, Operation<Void> original) {
    ChatModule module = ChatModule.active();
    if (module == null || !module.noBackground.get()) original.call(left, top, right, bottom, color);
  }

  @WrapOperation(
      method = "drawChat",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;FFI)I"),
      require = 0)
  private int cbui$shadow(FontRenderer font, String text, float x, float y, int color, Operation<Integer> original) {
    ChatModule module = ChatModule.active();
    if (module == null || module.shadow.get()) return original.call(font, text, x, y, color);
    return font.drawString(text, x, y, color, false);
  }

  // ---- smooth chat ----

  @Unique private static final float cbui$SLIDE_MS = 160f;
  @Unique private float cbui$slideFrom;
  @Unique private long cbui$slideAt;
  @Unique private boolean cbui$clipping;

  /** How far the lines still have to travel, in chat pixels. */
  @Unique
  private float cbui$slideLeft() {
    float t = (System.currentTimeMillis() - this.cbui$slideAt) / cbui$SLIDE_MS;
    if (t >= 1f || this.cbui$slideFrom <= 0f) return 0f;
    float rest = 1f - t;
    return this.cbui$slideFrom * rest * rest * rest;
  }

  /** New lines start below their place and rise into it; the area under the chat is clipped so they do not cross the hotbar. */
  @Inject(
      method = "drawChat",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;scale(FFF)V", ordinal = 0, shift = At.Shift.AFTER),
      require = 0)
  private void cbui$slideStart(int updateCounter, CallbackInfo ci) {
    ChatModule module = ChatModule.active();
    if (module == null || !module.smooth.get() || this.scrollPos != 0) return;
    float left = cbui$slideLeft();
    if (left <= 0f) return;
    GlStateManager.translate(0f, left, 0f);
    int bottom = 28 * new ScaledResolution(this.mc).getScaleFactor();
    GL11.glEnable(GL11.GL_SCISSOR_TEST);
    GL11.glScissor(0, bottom, this.mc.displayWidth, Math.max(0, this.mc.displayHeight - bottom));
    this.cbui$clipping = true;
  }

  @Inject(method = "drawChat", at = @At("RETURN"), require = 0)
  private void cbui$slideEnd(int updateCounter, CallbackInfo ci) {
    if (!this.cbui$clipping) return;
    this.cbui$clipping = false;
    GL11.glDisable(GL11.GL_SCISSOR_TEST);
  }

  // ---- history length ----

  @ModifyConstant(method = "setChatLine", constant = @Constant(intValue = 100), require = 0)
  private int cbui$historyLimit(int vanilla) {
    ChatModule module = ChatModule.active();
    return module != null && module.longHistory.get() ? 1000 : vanilla;
  }

  // ---- mentions, timestamps and the slide ----

  @Unique private static final java.text.SimpleDateFormat cbui$CLOCK = new java.text.SimpleDateFormat("HH:mm");

  /** Only new lines are changed: the refresh after a resize passes displayOnly, and those lines already have all this. */
  @WrapMethod(method = "setChatLine", require = 0)
  private void cbui$newLine(IChatComponent message, int id, int tick, boolean displayOnly, Operation<Void> original) {
    ChatModule module = displayOnly ? null : ChatModule.active();
    if (module == null) {
      original.call(message, id, tick, displayOnly);
      return;
    }
    try {
      message = module.onMessage(message);
    } catch (Throwable error) {
      dev.cbui.Cbui.reportOnce("chat mentions", error);
    }
    if (module.timestamps.get()) {
      IChatComponent stamped = new ChatComponentText("\u00a77[" + cbui$CLOCK.format(new java.util.Date()) + "] \u00a7r");
      stamped.appendSibling(message);
      message = stamped;
    }
    ChatLine newest = this.drawnChatLines.isEmpty() ? null : this.drawnChatLines.get(0);
    original.call(message, id, tick, displayOnly);
    if (!module.smooth.get() || this.cbui$reprinting || this.scrollPos != 0) return;
    int added = 0;
    while (added < this.drawnChatLines.size() && this.drawnChatLines.get(added) != newest) added++;
    this.cbui$slideFrom = cbui$slideLeft() + added * this.mc.fontRendererObj.FONT_HEIGHT;
    this.cbui$slideAt = System.currentTimeMillis();
  }

  // ---- stacking ----

  @Inject(method = "printChatMessageWithOptionalDeletion", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$stack(IChatComponent message, int id, CallbackInfo ci) {
    this.cbui$firstBefore = this.drawnChatLines.isEmpty() ? null : this.drawnChatLines.get(0);
    ChatModule module = ChatModule.active();
    if (module == null || !module.stack.get() || id != 0 || this.cbui$reprinting) return;
    String text = message.getFormattedText();
    if (text.equals(this.cbui$lastText)
        && this.cbui$lastLines > 0
        && this.drawnChatLines.size() >= this.cbui$lastLines
        && !this.chatLines.isEmpty()) {
      this.cbui$count++;
      for (int i = 0; i < this.cbui$lastLines; i++) this.drawnChatLines.remove(0);
      this.chatLines.remove(0);
      IChatComponent stacked = message.createCopy();
      stacked.appendSibling(new ChatComponentText(" §7[x" + this.cbui$count + "]"));
      this.cbui$reprinting = true;
      try {
        ((GuiNewChat) (Object) this).printChatMessageWithOptionalDeletion(stacked, 0);
      } finally {
        this.cbui$reprinting = false;
      }
      ci.cancel();
      return;
    }
    this.cbui$lastText = text;
    this.cbui$count = 1;
  }

  @Inject(method = "printChatMessageWithOptionalDeletion", at = @At("RETURN"), require = 0)
  private void cbui$countLines(IChatComponent message, int id, CallbackInfo ci) {
    if (id != 0) return;
    int n = 0;
    while (n < this.drawnChatLines.size() && this.drawnChatLines.get(n) != this.cbui$firstBefore) n++;
    this.cbui$lastLines = n;
  }

  @Inject(method = "clearChatMessages", at = @At("HEAD"), require = 0)
  private void cbui$forget(CallbackInfo ci) {
    this.cbui$lastText = null;
    this.cbui$count = 0;
    this.cbui$lastLines = 0;
  }

  // ---- message under the mouse ----

  @Override
  public String cbui$messageAt(int rawMouseX, int rawMouseY) {
    ScaledResolution resolution = new ScaledResolution(this.mc);
    GuiNewChat chat = (GuiNewChat) (Object) this;
    int scale = resolution.getScaleFactor();
    float chatScale = chat.getChatScale();
    int x = MathHelper.floor_float((rawMouseX / scale - 3) / chatScale);
    int y = MathHelper.floor_float((rawMouseY / scale - 27) / chatScale);
    if (x < 0 || y < 0) return null;
    int visible = Math.min(chat.getLineCount(), this.drawnChatLines.size());
    int font = this.mc.fontRendererObj.FONT_HEIGHT;
    if (x > MathHelper.floor_float(chat.getChatWidth() / chatScale) || y >= font * visible + visible) return null;
    int index = y / font + this.scrollPos;
    if (index < 0 || index >= this.drawnChatLines.size()) return null;
    ChatLine hit = this.drawnChatLines.get(index);
    // a long message is wrapped into several lines that share a creation tick and id: join them, top line first
    int first = index, last = index;
    while (first + 1 < this.drawnChatLines.size() && cbui$sameMessage(this.drawnChatLines.get(first + 1), hit)) first++;
    while (last - 1 >= 0 && cbui$sameMessage(this.drawnChatLines.get(last - 1), hit)) last--;
    StringBuilder out = new StringBuilder();
    for (int i = first; i >= last; i--) {
      String part = this.drawnChatLines.get(i).getChatComponent().getUnformattedText();
      if (out.length() > 0 && !out.toString().endsWith(" ")) out.append(' ');
      out.append(part);
    }
    return out.toString().replaceAll("\\s{2,}", " ").trim();
  }

  @Unique
  private static boolean cbui$sameMessage(ChatLine a, ChatLine b) {
    return a.getUpdatedCounter() == b.getUpdatedCounter() && a.getChatLineID() == b.getChatLineID();
  }
}
