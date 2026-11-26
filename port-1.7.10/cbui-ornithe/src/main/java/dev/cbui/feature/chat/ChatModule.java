package dev.cbui.feature.chat;

import dev.cbui.chat.Mentions;
import dev.cbui.feature.FeatureModule;
import dev.cbui.feature.Features;
import dev.cbui.hud.Setting;
import java.util.regex.Pattern;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

/**
 * How chat looks and behaves. The card's on/off switch shows or hides chat: off, no chat lines are drawn at all
 * (messages still arrive and are kept). Each look/behaviour option below can be turned off for vanilla behaviour. A
 * server that blocks the mod gives plain vanilla chat (still shown).
 */
public final class ChatModule extends FeatureModule implements Features.Synced {
  public final Setting.Toggle noBackground = grouped("Look", add(new Setting.Toggle("No chat background", false, "Remove the dark box behind chat lines")));
  public final Setting.Toggle shadow = grouped("Look", add(new Setting.Toggle("Chat text shadow", true, "Drop shadow on chat text")));
  public final Setting.Toggle smooth = grouped("Look", add(new Setting.Toggle("Smooth chat", true, "New messages slide in instead of jumping into place")));
  public final Setting.Toggle highlight = grouped("Mentions", add(new Setting.Toggle("Highlight your name", true, "Your name turns yellow when someone writes it in chat")));
  public final Setting.Toggle mentionSound = grouped("Mentions", add(new Setting.Toggle("Mention sound", true, "Play a sound when someone writes your name in chat")));
  public final Setting.Toggle stack = grouped("Messages", add(new Setting.Toggle("Stack repeated messages", true, "Identical messages in a row become one line with a count")));
  public final Setting.Toggle timestamps = grouped("Messages", add(new Setting.Toggle("Timestamps", false, "A grey [HH:mm] in front of every new message")));
  public final Setting.Toggle longHistory = grouped("Messages", add(new Setting.Toggle("Long chat history", true, "Remember 1000 lines instead of 100")));
  public final Setting.Toggle copy = grouped("Messages", add(new Setting.Toggle("Ctrl+click copies", true, "Hold Ctrl and click a message to copy it")));

  private String patternName;
  private Pattern pattern;

  public ChatModule() {
    super("Chat", true);
  }

  @Override public String category() { return "UTILITY"; }

  /** The module while it is switched on, or null when chat should stay vanilla. */
  public static ChatModule active() {
    ChatModule module = Features.get(ChatModule.class);
    return module != null && module.enabled() ? module : null;
  }

  /** True when the player switched Chat off: chat is not drawn at all. (A server block is not the player's switch.) */
  public static boolean hidden() {
    ChatModule module = Features.get(ChatModule.class);
    return module != null && !module.userEnabled();
  }

  private boolean switchChecked;

  /**
   * Once per install: before 1.6.8 the switch meant "luka's chat options off, plain vanilla chat"; now off hides chat.
   * A player who had it off keeps seeing chat: the switch goes on with every option set to vanilla's behaviour.
   */
  @Override
  public void sync() {
    if (switchChecked) return;
    switchChecked = true;
    try {
      java.io.File marker = new java.io.File(mc.mcDataDir, "config/cbui/chat-switch-hides.done");
      if (marker.exists()) return;
      if (!userEnabled()) {
        noBackground.set(false);
        shadow.set(true);
        smooth.set(false);
        highlight.set(false);
        mentionSound.set(false);
        stack.set(false);
        timestamps.set(false);
        longHistory.set(false);
        copy.set(false);
        enabled(true);
        if (dev.cbui.Cbui.config() != null && dev.cbui.Cbui.hud() != null) dev.cbui.Cbui.config().save(dev.cbui.Cbui.hud());
      }
      if (!marker.getParentFile().isDirectory()) marker.getParentFile().mkdirs();
      marker.createNewFile();
    } catch (Throwable error) {
      dev.cbui.Cbui.reportOnce("chat switch update", error);
    }
  }

  /** Applies the mention options to a new message and returns the message to show. */
  public IChatComponent onMessage(IChatComponent message) {
    if (!highlight.get() && !mentionSound.get()) return message;
    Pattern name = namePattern();
    if (name == null || !Mentions.mentioned(message.getUnformattedText(), name)) return message;
    if (mentionSound.get()) mc.getSoundHandler().playSound(PositionedSoundRecord.createPositionedSoundRecord(new ResourceLocation("note.pling"), 1.5f));
    return highlight.get() ? mark(message, name) : message;
  }

  private Pattern namePattern() {
    String me = mc.getSession().getUsername();
    if (me == null || me.length() < 3) return null;
    if (!me.equals(patternName)) {
      patternName = me;
      pattern = Mentions.pattern(me);
    }
    return pattern;
  }

  private static IChatComponent mark(IChatComponent component, Pattern name) {
    IChatComponent out;
    String base = component.getChatStyle().getFormattingCode();
    if (component instanceof ChatComponentText) {
      out = new ChatComponentText(Mentions.highlight(component.getUnformattedTextForChat(), name, base));
    } else if (component instanceof ChatComponentTranslation) {
      ChatComponentTranslation translation = (ChatComponentTranslation) component;
      Object[] args = translation.getFormatArgs().clone();
      for (int i = 0; i < args.length; i++) {
        if (args[i] instanceof IChatComponent) args[i] = mark((IChatComponent) args[i], name);
        else if (args[i] instanceof String) args[i] = Mentions.highlight((String) args[i], name, base);
      }
      out = new ChatComponentTranslation(translation.getKey(), args);
    } else {
      return component;
    }
    out.setChatStyle(component.getChatStyle().createShallowCopy());
    for (Object sibling : component.getSiblings()) out.appendSibling(mark((IChatComponent) sibling, name));
    return out;
  }
}
